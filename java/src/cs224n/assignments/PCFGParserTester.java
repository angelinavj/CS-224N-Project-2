package cs224n.assignments;

import cs224n.io.PennTreebankReader;
import cs224n.ling.Tree;
import cs224n.ling.Trees;
import cs224n.parser.EnglishPennTreebankParseEvaluator;
import cs224n.util.*;


import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Harness for PCFG Parser project.
 *
 * @author Dan Klein
 */
public class PCFGParserTester {

	// Parser interface ===========================================================

	/**
	 * Parsers are required to map sentences to trees.  How a parser is
	 * constructed and trained is not specified.
	 */
	public static interface Parser {
		public void train(List<Tree<String>> trainTrees);
		public Tree<String> getBestParse(List<String> sentence);
	}


	// PCFGParser =================================================================

	/**
	 * The PCFG Parser you will implement.
	 */
	public static class PCFGParser implements Parser {

		private Grammar grammar;
		private Lexicon lexicon;

		public void train(List<Tree<String>> trainTrees) {

			List<Tree<String> > annotatedTrees = new ArrayList<Tree<String>> ();
			for (Tree<String> tree: trainTrees) {
				Tree<String> newTree = TreeAnnotations.annotateTree(tree);
				annotatedTrees.add(newTree);
			}
			lexicon = new Lexicon(annotatedTrees);
			grammar = new Grammar(annotatedTrees);
		}

		private void printDebug(List<String> sentence, ArrayList<ArrayList<Counter<String> > >score, Set<String> nonTerminals) {
			for (int i = 0; i < sentence.size(); i++) {
				for (int j = 0; j <= sentence.size(); j++) {
					for (String nonTerminal: nonTerminals) {
						if (score.get(i).get(j).getCount(nonTerminal) != 0) {
							System.out.println(i + " (" + sentence.get(i) + ") " + j + " " + nonTerminal + " " + score.get(i).get(j).getCount(nonTerminal));

						}
					}
				}
			}
		}

		public Tree<String> getBestParse(List<String> sentence) {
			ArrayList<ArrayList<Counter<String> > > score = new ArrayList<ArrayList<Counter<String> > >();

			long origStart = System.currentTimeMillis();
			long start = origStart;
			for (int i = 0; i < sentence.size(); i++) {
				score.add(i, new ArrayList<Counter<String> >());
				for (int j = 0; j <= sentence.size(); j++) {
					score.get(i).add(j, new Counter<String>());
				}
			}

			System.out.println("Done initalizing arrays: " + ((System.currentTimeMillis()-start)/1000F));
			start = System.currentTimeMillis();
			// Initialize score.
			for (int i = 0; i < sentence.size(); i++) {
				Counter<String> squareScore = score.get(i).get(i+1);
				String curWord = sentence.get(i);
				for (String tag : lexicon.getAllTags()) {
					if(lexicon.scoreTagging(curWord, tag) != 0.0)
						squareScore.setCount(tag, -Math.log(lexicon.scoreTagging(curWord, tag)));
				}

				// Handle unaries
				boolean added = true;
				while (added) {
					added = false;
					Set<String> temp = new HashSet<String>(squareScore.keySet());
					for (String B: temp) {
						for (UnaryRule rule: grammar.getUnaryRulesByChild(B)) {

							String A = rule.getParent();

							double probability = squareScore.getCount(B) + -Math.log(rule.getScore());
							if (probability < squareScore.getCount(A) || squareScore.getCount(A) == 0.0) {
								squareScore.setCount(A, probability);
								added = true;
							}
						}
					}
				}
			}

			System.out.println("Done inputting lex: " + ((System.currentTimeMillis()-start)/1000F));
			start = System.currentTimeMillis();

			for (int span = 2; span <= sentence.size(); span++) {
				for (int begin = 0; begin <= sentence.size() - span; begin++) {
					int end = begin + span;
					Counter<String> squareScore = score.get(begin).get(end);
					for (int split = begin + 1; split <= end - 1; split++) {
						Counter<String> leftScore = score.get(begin).get(split);
						Counter<String> rightScore = score.get(split).get(end);

						for (String B: leftScore.keySet()) {
							double scoreB = leftScore.getCount(B);

							for (BinaryRule rule: grammar.getBinaryRulesByLeftChild(B)) {
								String C = rule.getRightChild();
								String A = rule.getParent();

								if (rightScore.keySet().contains(C)) {
									double scoreC = rightScore.getCount(C);

									double probability = scoreB + scoreC + 
											(-Math.log(rule.getScore()));
									double oldScore = squareScore.getCount(A);
									if (probability < oldScore  || oldScore == 0.0) {
										squareScore.setCount(A, probability);
									}
								}
							}
						} 
					}  
					// Handle unaries

					boolean added = true;
					while (added) {
						added = false;

						Set<String> temp = new HashSet<String>(squareScore.keySet());
						for (String B: temp) {
							for (UnaryRule rule: grammar.getUnaryRulesByChild(B)) {
								String A = rule.getParent();

								double probability = squareScore.getCount(B) + -Math.log(rule.getScore());
								double oldScore = squareScore.getCount(A);
								if (probability < oldScore  || oldScore == 0.0) {
									squareScore.setCount(A, probability);
									added = true;
								}
							}  
						}
					}

				}

			}
			System.out.println("Done building box: " + ((System.currentTimeMillis()-start)/1000F));
			start = System.currentTimeMillis();
			Tree<String> toReturn = buildTree(sentence, score);
			System.out.println("Done building tree: " + ((System.currentTimeMillis()-start)/1000F));
			System.out.println("Total time: " + ((System.currentTimeMillis()-origStart)/1000F));
			return toReturn;
		}


		private Tree<String> buildTree(List<String> sentence, ArrayList<ArrayList<Counter<String> > > score) {
			Tree<String> annotated = recursiveBuildTree(sentence, score, 0, sentence.size(), "ROOT");
			Tree<String> unanno = TreeAnnotations.unAnnotateTree(annotated);
			return unanno;
		}

		private Tree<String> recursiveBuildTree(List<String> sentence, ArrayList<ArrayList<Counter<String> > > score,
				int begin, int end, String tag) {
			Tree<String> curTree = new Tree<String>(tag);

			double tagScore = score.get(begin).get(end).getCount(tag);
			Counter<String> squareScore = score.get(begin).get(end);
			for (int split = begin + 1; split <= end - 1; split++) {
				for (String B : score.get(begin).get(split).keySet()) {
					for (BinaryRule rule: grammar.getBinaryRulesByLeftChild(B)) {
						String C = rule.getRightChild();
						if (!rule.getParent().equals(tag) || !score.get(split).get(end).keySet().contains(C)) continue;
						double ruleScore = rule.getScore();
						if (ruleScore == 0.0) continue;

						double probability = score.get(begin).get(split).getCount(B) +
								score.get(split).get(end).getCount(C) +
								-Math.log(ruleScore);
						if (probability == tagScore) {

							List<Tree<String> > children = new ArrayList<Tree<String> >();
							children.add(recursiveBuildTree(sentence, score, begin, split, B));
							children.add(recursiveBuildTree(sentence, score, split, end, C));
							curTree.setChildren(children);

							return curTree;
						}

					}
				}
			}

			for (String child: squareScore.keySet()) {
				for (UnaryRule rule: grammar.getUnaryRulesByChild(child)) {
					String parent = rule.getParent();					
					if (parent.equals(tag)) {
						double probability = squareScore.getCount(child) + -Math.log(rule.getScore());
						if (probability == squareScore.getCount(parent)) {
							List<Tree<String> > children = new ArrayList<Tree<String> >();
							children.add(recursiveBuildTree(sentence, score, begin, end, child));
							curTree.setChildren(children);

							return curTree;
						}
					}
				}
			}
			List<Tree<String> > children = new ArrayList<Tree<String> > ();

			children.add(new Tree<String>(sentence.get(begin)));
			curTree.setChildren(children);
			return curTree;
		}
	}

	// BaselineParser =============================================================

	/**
	 * Baseline parser (though not a baseline I've ever seen before).  Tags the
	 * sentence using the baseline tagging method, then either retrieves a known
	 * parse of that tag sequence, or builds a right-branching parse for unknown
	 * tag sequences.
	 */
	public static class BaselineParser implements Parser {

		CounterMap<List<String>,Tree<String>> knownParses;
		CounterMap<Integer,String> spanToCategories;
		Lexicon lexicon;

		public void train(List<Tree<String>> trainTrees) {
			lexicon = new Lexicon(trainTrees);
			knownParses = new CounterMap<List<String>, Tree<String>>();
			spanToCategories = new CounterMap<Integer, String>();
			for (Tree<String> trainTree : trainTrees) {
				List<String> tags = trainTree.getPreTerminalYield();
				knownParses.incrementCount(tags, trainTree, 1.0);
				tallySpans(trainTree, 0);
			}
		}

		public Tree<String> getBestParse(List<String> sentence) {
			List<String> tags = getBaselineTagging(sentence);
			if (knownParses.keySet().contains(tags)) {
				return getBestKnownParse(tags, sentence);
			}
			return buildRightBranchParse(sentence, tags);
		}

		/* Builds a tree that branches to the right.  For pre-terminals it
		 * uses the most common tag for the word in the training corpus.
		 * For all other non-terminals it uses the tag that is most common
		 * in training corpus of tree of the same size span as the tree
		 * that is being labeled. */
		private Tree<String> buildRightBranchParse(List<String> words, List<String> tags) {
			int currentPosition = words.size() - 1;
			Tree<String> rightBranchTree = buildTagTree(words, tags, currentPosition);
			while (currentPosition > 0) {
				currentPosition--;
				rightBranchTree = merge(buildTagTree(words, tags, currentPosition),
						rightBranchTree);
			}
			rightBranchTree = addRoot(rightBranchTree);
			return rightBranchTree;
		}

		private Tree<String> merge(Tree<String> leftTree, Tree<String> rightTree) {
			int span = leftTree.getYield().size() + rightTree.getYield().size();
			String mostFrequentLabel = spanToCategories.getCounter(span).argMax();
			List<Tree<String>> children = new ArrayList<Tree<String>>();
			children.add(leftTree);
			children.add(rightTree);
			return new Tree<String>(mostFrequentLabel, children);
		}

		private Tree<String> addRoot(Tree<String> tree) {
			return new Tree<String>("ROOT", Collections.singletonList(tree));
		}

		private Tree<String> buildTagTree(List<String> words,
				List<String> tags,
				int currentPosition) {
			Tree<String> leafTree = new Tree<String>(words.get(currentPosition));
			Tree<String> tagTree = new Tree<String>(tags.get(currentPosition), 
					Collections.singletonList(leafTree));
			return tagTree;
		}

		private Tree<String> getBestKnownParse(List<String> tags, List<String> sentence) {
			Tree<String> parse = knownParses.getCounter(tags).argMax().deepCopy();
			parse.setWords(sentence);
			return parse;
		}

		private List<String> getBaselineTagging(List<String> sentence) {
			List<String> tags = new ArrayList<String>();
			for (String word : sentence) {
				String tag = getBestTag(word);
				tags.add(tag);
			}
			return tags;
		}

		private String getBestTag(String word) {
			double bestScore = Double.NEGATIVE_INFINITY;
			String bestTag = null;
			for (String tag : lexicon.getAllTags()) {
				double score = lexicon.scoreTagging(word, tag);
				if (bestTag == null || score > bestScore) {
					bestScore = score;
					bestTag = tag;
				}
			}
			return bestTag;
		}

		private int tallySpans(Tree<String> tree, int start) {
			if (tree.isLeaf() || tree.isPreTerminal()) 
				return 1;
			int end = start;
			for (Tree<String> child : tree.getChildren()) {
				int childSpan = tallySpans(child, end);
				end += childSpan;
			}
			String category = tree.getLabel();
			if (! category.equals("ROOT"))
				spanToCategories.incrementCount(end - start, category, 1.0);
			return end - start;
		}

	}


	// TreeAnnotations ============================================================

	/**
	 * Class which contains code for annotating and binarizing trees for
	 * the parser's use, and debinarizing and unannotating them for
	 * scoring.
	 */
	public static class TreeAnnotations {

		public static Tree<String> markovUnannotateTree(Tree<String> annotatedTree) {
			recMarkovUnannotateTree(annotatedTree);
			return annotatedTree;
		}

		public static void recMarkovUnannotateTree(Tree<String> tree){
			String label = tree.getLabel();
			int cutIndex = label.indexOf('^');
			if(cutIndex > 0) tree.setLabel(label.substring(0, cutIndex));
			for(Tree<String> child: tree.getChildren()) {
				recMarkovUnannotateTree(child);
			}
		}

		public static Tree<String> markovAnnotateTree(Tree<String> unAnnotatedTree) {
			return markovizeTree(unAnnotatedTree);
		}


		public static Tree<String> annotateTree(Tree<String> unAnnotatedTree) {

			// Currently, the only annotation done is a lossless binarization

			// TODO: change the annotation from a lossless binarization to a
			// finite-order markov process (try at least 1st and 2nd order)
			Tree<String> annotated = markovAnnotateTree(unAnnotatedTree);
			annotated = binarizeTree(annotated);
			return annotated;

		}

		private static Tree<String> markovizeTreeHelper(Tree<String> parent, Tree<String> tree) {
			List<Tree<String>> children = new ArrayList<Tree<String>>();
			for(Tree<String> child: tree.getChildren()) {
				children.add(markovizeTreeHelper(tree, child));
			}
			if(parent != null && !tree.isLeaf()) {
				String curLabel = tree.getLabel();
				String parentLabel = parent.getLabel();
				tree.setLabel(curLabel + "^" + parentLabel);
			}
			return tree;
		}

		private static Tree<String> markovizeTree(Tree<String> tree) {
			return markovizeTreeHelper(null, tree);
		}

		private static Tree<String> binarizeTree(Tree<String> tree) {
			String label = tree.getLabel();
			if (tree.isLeaf())
				return new Tree<String>(label);
			if (tree.getChildren().size() == 1) {
				return new Tree<String>
				(label, 
						Collections.singletonList(binarizeTree(tree.getChildren().get(0))));
			}
			// otherwise, it's a binary-or-more local tree, 
			// so decompose it into a sequence of binary and unary trees.
			String intermediateLabel = "@"+label+"->";
			Tree<String> intermediateTree =
					binarizeTreeHelper(tree, 0, intermediateLabel);
			return new Tree<String>(label, intermediateTree.getChildren());
		}

		private static Tree<String> binarizeTreeHelper(Tree<String> tree,
				int numChildrenGenerated, 
				String intermediateLabel) {
			Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
			List<Tree<String>> children = new ArrayList<Tree<String>>();
			children.add(binarizeTree(leftTree));
			if (numChildrenGenerated < tree.getChildren().size() - 1) {
				Tree<String> rightTree = 
						binarizeTreeHelper(tree, numChildrenGenerated + 1, 
								intermediateLabel + "_" + leftTree.getLabel());
				children.add(rightTree);
			}
			return new Tree<String>(intermediateLabel, children);
		}

		public static Tree<String> unAnnotateTree(Tree<String> annotatedTree) {

			// Remove intermediate nodes (labels beginning with "@"
			// Remove all material on node labels which follow their base symbol 
			// (cuts at the leftmost -, ^, or : character)
			// Examples: a node with label @NP->DT_JJ will be spliced out, 
			// and a node with label NP^S will be reduced to NP

			Tree<String> debinarizedTree =
					Trees.spliceNodes(annotatedTree, new Filter<String>() {
						public boolean accept(String s) {
							return s.startsWith("@");
						}
					});
			Tree<String> unAnnotatedTree = 
					(new Trees.FunctionNodeStripper()).transformTree(debinarizedTree);
			return markovUnannotateTree(unAnnotatedTree);
		}
	}


	// Lexicon ====================================================================

	/**
	 * Simple default implementation of a lexicon, which scores word,
	 * tag pairs with a smoothed estimate of P(tag|word)/P(tag).
	 */
	public static class Lexicon {

		CounterMap<String,String> wordToTagCounters = new CounterMap<String, String>();
		double totalTokens = 0.0;
		double totalWordTypes = 0.0;
		Counter<String> tagCounter = new Counter<String>();
		Counter<String> wordCounter = new Counter<String>();
		Counter<String> typeTagCounter = new Counter<String>();

		public Set<String> getAllTags() {
			return tagCounter.keySet();
		}

		public boolean isKnown(String word) {
			return wordCounter.keySet().contains(word);
		}

		/* Returns a smoothed estimate of P(word|tag) */
		public double scoreTagging(String word, String tag) {
			double p_tag = tagCounter.getCount(tag) / totalTokens;
			double c_word = wordCounter.getCount(word);
			double c_tag_and_word = wordToTagCounters.getCount(word, tag);
			if (c_word < 10) { // rare or unknown
				c_word += 1.0;
				c_tag_and_word += typeTagCounter.getCount(tag) / totalWordTypes;
			}
			double p_word = (1.0 + c_word) / (totalTokens + totalWordTypes);
			double p_tag_given_word = c_tag_and_word / c_word;
			return p_tag_given_word / p_tag * p_word;
		}

		/* Builds a lexicon from the observed tags in a list of training trees. */
		public Lexicon(List<Tree<String>> trainTrees) {
			for (Tree<String> trainTree : trainTrees) {
				List<String> words = trainTree.getYield();
				List<String> tags = trainTree.getPreTerminalYield();
				for (int position = 0; position < words.size(); position++) {
					String word = words.get(position);
					String tag = tags.get(position);
					tallyTagging(word, tag);
				}
			}
		}

		private void tallyTagging(String word, String tag) {
			if (! isKnown(word)) {
				totalWordTypes += 1.0;
				typeTagCounter.incrementCount(tag, 1.0);
			}
			totalTokens += 1.0;
			tagCounter.incrementCount(tag, 1.0);
			wordCounter.incrementCount(word, 1.0);
			wordToTagCounters.incrementCount(word, tag, 1.0);
		}
	}


	// Grammar ====================================================================

	/**
	 * Simple implementation of a PCFG grammar, offering the ability to
	 * look up rules by their child symbols.  Rule probability estimates
	 * are just relative frequency estimates off of training trees.
	 */
	public static class Grammar {

		Map<String, List<BinaryRule>> binaryRulesByLeftChild = 
				new HashMap<String, List<BinaryRule>>();
		Map<String, List<BinaryRule>> binaryRulesByRightChild = 
				new HashMap<String, List<BinaryRule>>();
		Map<String, List<UnaryRule>> unaryRulesByChild = 
				new HashMap<String, List<UnaryRule>>();

		Set<String> allNonTerminals = new HashSet<String>();


		/* Rules in grammar are indexed by child for easy access when
		 * doing bottom up parsing. */
		public List<BinaryRule> getBinaryRulesByLeftChild(String leftChild) {
			return CollectionUtils.getValueList(binaryRulesByLeftChild, leftChild);
		}

		public List<BinaryRule> getBinaryRulesByRightChild(String rightChild) {
			return CollectionUtils.getValueList(binaryRulesByRightChild, rightChild);
		}

		public List<UnaryRule> getUnaryRulesByChild(String child) {
			return CollectionUtils.getValueList(unaryRulesByChild, child);
		}

		private void computeAllNonTerminals() {
			for (String leftChild : binaryRulesByLeftChild.keySet()) {

				for (BinaryRule binaryRule : getBinaryRulesByLeftChild(leftChild)) {
					allNonTerminals.add(binaryRule.getParent());
				}

			}

			for (String child : unaryRulesByChild.keySet()) {
				for (UnaryRule unaryRule : getUnaryRulesByChild(child)) {
					allNonTerminals.add(unaryRule.getParent());
				}
			}
		}

		public Set<String> getAllNonTerminals() {
			return allNonTerminals;
		}
		public String toString() {
			StringBuilder sb = new StringBuilder();
			List<String> ruleStrings = new ArrayList<String>();
			for (String leftChild : binaryRulesByLeftChild.keySet()) {
				for (BinaryRule binaryRule : getBinaryRulesByLeftChild(leftChild)) {
					ruleStrings.add(binaryRule.toString());
				}
			}
			for (String child : unaryRulesByChild.keySet()) {
				for (UnaryRule unaryRule : getUnaryRulesByChild(child)) {
					ruleStrings.add(unaryRule.toString());
				}
			}
			for (String ruleString : CollectionUtils.sort(ruleStrings)) {
				sb.append(ruleString);
				sb.append("\n");
			}
			return sb.toString();
		}

		private void addBinary(BinaryRule binaryRule) {
			CollectionUtils.addToValueList(binaryRulesByLeftChild, 
					binaryRule.getLeftChild(), binaryRule);
			CollectionUtils.addToValueList(binaryRulesByRightChild, 
					binaryRule.getRightChild(), binaryRule);
		}

		private void addUnary(UnaryRule unaryRule) {
			CollectionUtils.addToValueList(unaryRulesByChild, 
					unaryRule.getChild(), unaryRule);
		}

		/* A builds PCFG using the observed counts of binary and unary
		 * productions in the training trees to estimate the probabilities
		 * for those rules.  */ 
		public Grammar(List<Tree<String>> trainTrees) {
			Counter<UnaryRule> unaryRuleCounter = new Counter<UnaryRule>();
			Counter<BinaryRule> binaryRuleCounter = new Counter<BinaryRule>();
			Counter<String> symbolCounter = new Counter<String>();
			for (Tree<String> trainTree : trainTrees) {
				tallyTree(trainTree, symbolCounter, unaryRuleCounter, binaryRuleCounter);
			}
			for (UnaryRule unaryRule : unaryRuleCounter.keySet()) {
				double unaryProbability = 
						unaryRuleCounter.getCount(unaryRule) / 
						symbolCounter.getCount(unaryRule.getParent());
				unaryRule.setScore(unaryProbability);
				addUnary(unaryRule);
			}
			for (BinaryRule binaryRule : binaryRuleCounter.keySet()) {
				double binaryProbability = 
						binaryRuleCounter.getCount(binaryRule) / 
						symbolCounter.getCount(binaryRule.getParent());
				binaryRule.setScore(binaryProbability);
				addBinary(binaryRule);
			}
			computeAllNonTerminals();

		}

		private void tallyTree(Tree<String> tree, Counter<String> symbolCounter,
				Counter<UnaryRule> unaryRuleCounter, 
				Counter<BinaryRule> binaryRuleCounter) {
			if (tree.isLeaf()) return;
			if (tree.isPreTerminal()) return;
			if (tree.getChildren().size() == 1) {
				UnaryRule unaryRule = makeUnaryRule(tree);
				symbolCounter.incrementCount(tree.getLabel(), 1.0);
				unaryRuleCounter.incrementCount(unaryRule, 1.0);
			}
			if (tree.getChildren().size() == 2) {
				BinaryRule binaryRule = makeBinaryRule(tree);
				symbolCounter.incrementCount(tree.getLabel(), 1.0);
				binaryRuleCounter.incrementCount(binaryRule, 1.0);
			}
			if (tree.getChildren().size() < 1 || tree.getChildren().size() > 2) {
				throw new RuntimeException("Attempted to construct a Grammar with an illegal tree: "+tree);
			}
			for (Tree<String> child : tree.getChildren()) {
				tallyTree(child, symbolCounter, unaryRuleCounter,  binaryRuleCounter);
			}
		}

		private UnaryRule makeUnaryRule(Tree<String> tree) {
			return new UnaryRule(tree.getLabel(), tree.getChildren().get(0).getLabel());
		}

		private BinaryRule makeBinaryRule(Tree<String> tree) {
			return new BinaryRule(tree.getLabel(), tree.getChildren().get(0).getLabel(), 
					tree.getChildren().get(1).getLabel());
		}
	}


	// BinaryRule =================================================================

	/* A binary grammar rule with score representing its probability. */
	public static class BinaryRule {

		String parent;
		String leftChild;
		String rightChild;
		double score;

		public String getParent() {
			return parent;
		}

		public String getLeftChild() {
			return leftChild;
		}

		public String getRightChild() {
			return rightChild;
		}

		public double getScore() {
			return score;
		}

		public void setScore(double score) {
			this.score = score;
		}

		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof BinaryRule)) return false;

			final BinaryRule binaryRule = (BinaryRule) o;

			if (leftChild != null ? !leftChild.equals(binaryRule.leftChild) : binaryRule.leftChild != null) 
				return false;
			if (parent != null ? !parent.equals(binaryRule.parent) : binaryRule.parent != null) 
				return false;
			if (rightChild != null ? !rightChild.equals(binaryRule.rightChild) : binaryRule.rightChild != null) 
				return false;

			return true;
		}

		public int hashCode() {
			int result;
			result = (parent != null ? parent.hashCode() : 0);
			result = 29 * result + (leftChild != null ? leftChild.hashCode() : 0);
			result = 29 * result + (rightChild != null ? rightChild.hashCode() : 0);
			return result;
		}

		public String toString() {
			return parent + " -> " + leftChild + " " + rightChild + " %% "+score;
		}

		public BinaryRule(String parent, String leftChild, String rightChild) {
			this.parent = parent;
			this.leftChild = leftChild;
			this.rightChild = rightChild;
		}
	}


	// UnaryRule ==================================================================

	/** A unary grammar rule with score representing its probability. */
	public static class UnaryRule {

		String parent;
		String child;
		double score;

		public String getParent() {
			return parent;
		}

		public String getChild() {
			return child;
		}

		public double getScore() {
			return score;
		}

		public void setScore(double score) {
			this.score = score;
		}

		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof UnaryRule)) return false;

			final UnaryRule unaryRule = (UnaryRule) o;

			if (child != null ? !child.equals(unaryRule.child) : unaryRule.child != null) return false;
			if (parent != null ? !parent.equals(unaryRule.parent) : unaryRule.parent != null) return false;

			return true;
		}

		public int hashCode() {
			int result;
			result = (parent != null ? parent.hashCode() : 0);
			result = 29 * result + (child != null ? child.hashCode() : 0);
			return result;
		}

		public String toString() {
			return parent + " -> " + child + " %% "+score;
		}

		public UnaryRule(String parent, String child) {
			this.parent = parent;
			this.child = child;
		}
	}


	// PCFGParserTester ===========================================================

	// Longest sentence length that will be tested on.
	private static int MAX_LENGTH = 20;

	private static void testParser(Parser parser, List<Tree<String>> testTrees) {
		EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> eval = 
				new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>
		(Collections.singleton("ROOT"), 
				new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));
		for (Tree<String> testTree : testTrees) {
			List<String> testSentence = testTree.getYield();
			if (testSentence.size() > MAX_LENGTH)
				continue;
			Tree<String> guessedTree = parser.getBestParse(testSentence);
			System.out.println("Guess:\n"+Trees.PennTreeRenderer.render(guessedTree));
			System.out.println("Gold:\n"+Trees.PennTreeRenderer.render(testTree));
			eval.evaluate(guessedTree, testTree);
		}
		eval.display(true);
	}

	private static List<Tree<String>> readTrees(String basePath, int low,
			int high) {
		Collection<Tree<String>> trees = PennTreebankReader.readTrees(basePath,
				low, high);
		// normalize trees
		Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
		List<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trees) {
			Tree<String> normalizedTree = treeTransformer.transformTree(tree);
			// System.out.println(Trees.PennTreeRenderer.render(normalizedTree));
			normalizedTreeList.add(normalizedTree);
		}
		return normalizedTreeList;
	}

	public static void main(String[] args) {

		// set up default options ..............................................
		Map<String, String> options = new HashMap<String, String>();
		options.put("-path",      "/afs/ir/class/cs224n/pa2/data/");
		options.put("-data",      "miniTest");
		options.put("-parser",    "cs224n.assignments.PCFGParserTester$BaselineParser");
		options.put("-maxLength", "20");

		// let command-line options supersede defaults .........................
		options.putAll(CommandLineUtils.simpleCommandLineParser(args));
		System.out.println("PCFGParserTester options:");
		for (Map.Entry<String, String> entry: options.entrySet()) {
			System.out.printf("  %-12s: %s%n", entry.getKey(), entry.getValue());
		}
		System.out.println();

		MAX_LENGTH = Integer.parseInt(options.get("-maxLength"));

		Parser parser;
		try {
			Class parserClass = Class.forName(options.get("-parser"));
			parser = (Parser) parserClass.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		System.out.println("Using parser: " + parser);

		String basePath = options.get("-path");
		String dataSet = options.get("-data");
		if (!basePath.endsWith("/"))
			basePath += "/";
		//basePath += dataSet;
		System.out.println("Data will be loaded from: " + basePath + "\n");

		List<Tree<String>> trainTrees = new ArrayList<Tree<String>>(),
				validationTrees = new ArrayList<Tree<String>>(),
				testTrees = new ArrayList<Tree<String>>();

		if (!basePath.endsWith("/"))
			basePath += "/";
		basePath += dataSet;
		if (dataSet.equals("miniTest")) {
			System.out.print("Loading training trees...");
			trainTrees = readTrees(basePath, 1, 3);
			System.out.println("done.");
			System.out.print("Loading test trees...");
			testTrees = readTrees(basePath, 4, 4);
			System.out.println("done.");
		}
		else if (dataSet.equals("treebank")) {
			System.out.print("Loading training trees...");
			trainTrees = readTrees(basePath, 200, 2199);
			System.out.println("done.");
			System.out.print("Loading validation trees...");
			validationTrees = readTrees(basePath, 2200, 2299);
			System.out.println("done.");
			System.out.print("Loading test trees...");
			testTrees = readTrees(basePath, 2300, 2319);
			System.out.println("done.");
		}
		else {
			throw new RuntimeException("Bad data set mode: "+ dataSet+", use miniTest, or treebank."); 
		}
		parser.train(trainTrees);
		testParser(parser, testTrees);

		/*List<Tree<String>> test1 = new ArrayList<Tree<String>>();
		test1.add(testTrees.get(0));
		System.out.println("test 1 is " + test1.toString());
		testParser(parser, test1);*/
	}
}
