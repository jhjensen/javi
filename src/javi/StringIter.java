package javi;
import java.util.Iterator;

final class StringIter implements Iterator<String> {

   private final Iterator baseIter;
   StringIter(Iterator it) {
      baseIter = it;
   }

   public boolean hasNext() {
      return baseIter.hasNext();
   }

   public String next() {
      return baseIter.next().toString();
   }

   public void remove() {
      throw new UnsupportedOperationException();
   }
}
