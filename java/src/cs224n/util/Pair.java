package cs224n.util;

/**
 * A generic-typed pair of objects.
 * @author Dan Klein
 */
public class Pair<F,S> {
  F first;
  S second;

  public F getFirst() {
    return first;
  }

  public S getSecond() {
    return second;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Pair)) return false;
    
    @SuppressWarnings("unchecked")
    final Pair pair = (Pair) o;

    if (first != null ? !first.equals(pair.first) : pair.first != null) return false;
    if (second != null ? !second.equals(pair.second) : pair.second != null) return false;

    return true;
  }
  
  private int hashCode;

  private void setHashcode() {
	  hashCode = (first != null ? first.hashCode() : 0);
	  hashCode = 29 * hashCode + (second != null ? second.hashCode() : 0);
  }
  
  public int hashCode() {
    return hashCode;
  }

  public String toString() {
    return "(" + getFirst() + ", " + getSecond() + ")";
  }

  public Pair(F first, S second) {
    this.first = first;
    this.second = second;
    setHashcode();
  }
}
