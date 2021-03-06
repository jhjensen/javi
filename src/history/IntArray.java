package history;
public final class IntArray {

   private int[] store;
   private int len;

   public IntArray(int initialSize) {
      store = new int[initialSize];
   }

   public void add(int element) {
      //trace("element = " + element +  " offset = " + len);
      if (len == store.length)  {
         int[] temp = new int[len << 1];
         System.arraycopy(store, 0, temp, 0, len);
         store = temp;
      }
      store[len++] = element;
   }

   public int get(int index) {
      if (index >= len)
         throw new IndexOutOfBoundsException(String.valueOf(index));
      return store[index];
   }

   public void set(int index, int val) {
      if (index >= len) {
         int[] temp =  new int[index > len << 1
                                                 ? index << 1
                                                 : len
                                            ];
         System.arraycopy(store, 0, temp, 0, len);
         store = temp;
      }
      store[index] = val;
   }

   public void removeRange(int from, int to) {
      //trace("int array clearing from = " + from + " to = " + to);
      if ((from > to) || (to > len))
         throw new IndexOutOfBoundsException("from > to");
      System.arraycopy(store, from, store, to, store.length - to);
      len -=  to - from;
   }

   void clear() {
      //trace("clearing int array");
      store = new int[1];
      len = 0;
   }

   public int size() {
      return len;
   }

}
