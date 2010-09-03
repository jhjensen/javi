package history;
class Testutil {

static String dumphex(byte[] b) {
      return dumphex(b,0,b.length);
}
static String dumphex(byte[] b,int offset,int length) {
     StringBuilder sb = new StringBuilder();
     if (offset+length > b.length) {
        sb.append("Error in dumphex length to great:\n");
        sb.append("offset = " + offset + " length = " + length + " real length = " + 
        b.length + '\n');
        offset = 0; 
        length = b.length;
        
     }
     for (int i=offset,count=0;count<length;count++,i++)  {
          sb.append(' ');
          sb.append(Byte.toString(b[i]));
          if (count>20)
             break;
     }
     return sb.toString();
}

static boolean arraycmp(byte[] b1,int offset1,int length,byte[] b2,int offset2) {
   if (b1==b2)
      return true;
   if (b1==null)
      return false;
   if (b2==null)
      return false;
   if (b1.length<offset1+length)
      return false;
   if (b2.length<offset2+length)
      return false;
   for (int i=0;i<length;i++) 
      if (b1[i+offset1]!=b2[offset2+i])
         return false;
   return true;
}

static boolean myassert(boolean flag,Object dump) {
   if (!flag)
        throw new RuntimeException(" ASSERTION FAILURE " + dump.toString());
   return flag;
}

}
