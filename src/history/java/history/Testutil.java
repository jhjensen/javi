package history;

class Testutil {

   static final String dumphex(byte[] b) {
      return dumphex(b, 0, b.length);
   }

   static final String dumphex(byte[] b, int offset, int length) {
      StringBuilder sb = new StringBuilder(3 * length);
      if (offset + length > b.length) {
         sb.append("Error in dumphex length to great:\n");
         sb.append("offset = " + offset + " length = " + length
            + " real length = " + b.length + '\n');
         offset = 0;
         length = b.length;

      }
      for (int ii = offset, count = 0; count < length; count++, ii++)  {
         sb.append(' ');
         sb.append(Byte.toString(b[ii]));
         if (count > 20)
            break;
      }
      return sb.toString();
   }

   static final boolean arraycmp(byte[] b1, int offset1, int length,
         byte[] b2, int offset2) {
      if (b1 == b2)
         return true;
      if (null == b1)
         return false;
      if (null == b2)
         return false;
      if (b1.length < offset1 + length)
         return false;
      if (b2.length < offset2 + length)
         return false;
      for (int ii = 0; ii < length; ii++)
         if (b1[ii + offset1] != b2[offset2 + ii])
            return false;
      return true;
   }

   static final void myassert(boolean flag, Object dump) {
      if (!flag)
         throw new RuntimeException(" ASSERTION FAILURE " + dump.toString());
   }

}
