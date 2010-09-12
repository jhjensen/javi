package history;
final class Chunk {

   // invariant : if data!=null then length and offset are valid.
   // or it would be if I could make a variable read only.

   private final byte[] data;
   private final int length;
   private final int offset;

   Chunk(byte [] b, int offseti, int sizei) {
      if ((offseti < 0) || (sizei + offseti > b.length))
         throw new IndexOutOfBoundsException();
      data = b;
      length = sizei;
      offset = offseti;
   }

   Chunk(byte [] b) {
      data = b;
      length = b.length;
      offset = 0;
   }

   // Chunk() {
   // }

   public String toString() {
      return Testutil.dumphex(data, offset, length) + "(" + length + ")";
   }

   boolean compare(byte[] b2, int offset2) {
      return Testutil.arraycmp(data, offset, length, b2, offset2);
   }

}
