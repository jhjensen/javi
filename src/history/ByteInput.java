package history;
import java.io.DataInput;

public class ByteInput implements DataInput {
  
   private byte[] buf;
   private int offset;
   private int limit;

   int getOffset() {
      return offset;
   }
   ByteInput(byte [] b) {
       buf=b;
       offset=0;
       limit =b.length;
   }

   void seek(int off) {
      if (off<0 || off>limit)
          throw new ArrayIndexOutOfBoundsException(" offset = " + offset+ " limit = "  + limit);
      offset =off;
   }

   ByteInput(ByteInput bip,int length) {
       buf=bip.buf;
       offset=bip.offset;
       limit =bip.offset + length;
       bip.offset += length;
       if (bip.available()<0)
          throw new ArrayIndexOutOfBoundsException(" req len = " + length + " avail = " + (bip.limit - bip.offset));
   }

   int available() {
      return limit-offset;
   }

   public String toString() {
      return  "buf (" + (limit -offset ) + ") = " + Testutil.dumphex(buf,offset,limit - offset);
   }

/*
   void setBuf(byte [] bufi,int offseti,int leni) {
        offset=offseti;
        buf = bufi;
        limit = offset + leni;
   }

   void setBuf(Chunk ck) {
        offset=ck.offset;
        buf = ck.data;
        limit = ck.offset + ck.length;
        
   }
   Chunk readChunk(int len) {
//trace("readChunk len = " + len + this);
     if (len>limit - offset)
        throw new ArrayIndexOutOfBoundsException(" req len = " + len + " avail = " + (limit - offset));
     if (len<0)
        throw new ArrayIndexOutOfBoundsException(" req len = " + len + " avail = " + (limit - offset));
     Chunk ck = new Chunk(buf,offset,len);
     offset +=len;
//trace("readChunk len = " + len + this);
     return ck;
   }
   void readChunk(Chunk ck) {
     ck.data=buf;
     ck.offset=offset;
     ck.length=limit-offset;
     offset +=ck.length;
   }
*/
   public void readFully(byte[] b) throws ArrayIndexOutOfBoundsException{
       if (limit - offset  < b.length)
          throw new ArrayIndexOutOfBoundsException(offset+b.length);
       System.arraycopy(buf,offset,b,0,b.length);  //??? should throw IOException 
       offset+=b.length;
   }

   public void readFully(byte[] b, int off, int len)  throws ArrayIndexOutOfBoundsException{
       System.arraycopy(buf,offset,b,offset,len);  //??? should throw IOException 
       offset+=len;
   }

   public int skipBytes(int n) {
       if (n+offset >buf.length)
          n = buf.length-offset;
       offset+=n;
       return n;
   }
   public boolean readBoolean()  throws ArrayIndexOutOfBoundsException{
       if (offset>=limit)
          throw new ArrayIndexOutOfBoundsException(offset);
         
       return (buf[offset++]==0);
   }

   public byte readByte()  throws ArrayIndexOutOfBoundsException{

       if (offset>=limit)
          throw new ArrayIndexOutOfBoundsException(offset);
//trace("readByte " + buf[offset]);
//      if (buf[offset]==2)
//         Thread.dumpStack();
      return buf[offset++];
   }

   public int readUnsignedByte()  throws ArrayIndexOutOfBoundsException{
       if (offset>=limit)
          throw new ArrayIndexOutOfBoundsException(offset);
      return 0xff & buf[offset++];
   }

   public short readShort()  throws ArrayIndexOutOfBoundsException{
       if (offset+1>=limit)
          throw new ArrayIndexOutOfBoundsException(offset+1);
      return (short)((buf[offset++] << 8) | (buf[offset++] & 0xff));
   }

   public int readUnsignedShort() throws ArrayIndexOutOfBoundsException{

       if (offset+1>=limit)
          throw new ArrayIndexOutOfBoundsException(offset+1);
      return (((buf[offset++] & 0xff) << 8) | (buf[offset++] & 0xff));
   }
     
   public char readChar() throws ArrayIndexOutOfBoundsException{
       if (offset+1>=limit)
          throw new ArrayIndexOutOfBoundsException(offset+1);
      return (char)((buf[offset++] << 8) | (buf[offset++] & 0xff));
   }
     
   public int readInt()  throws ArrayIndexOutOfBoundsException{

       if (offset+3>=limit)
          throw new ArrayIndexOutOfBoundsException(offset+3);
//trace("readInt returns "  +      (((buf[offset] & 0xff) << 24) | ((buf[offset+1] & 0xff) << 16) |
//         ((buf[offset+2] & 0xff) << 8) | (buf[offset+3] & 0xff)));
       return  (((buf[offset++] & 0xff) << 24) | ((buf[offset++] & 0xff) << 16) |
         ((buf[offset++] & 0xff) << 8) | (buf[offset++] & 0xff));
    }
     

   public long readLong()  throws ArrayIndexOutOfBoundsException{

       if (offset+7>=limit)
          throw new ArrayIndexOutOfBoundsException(offset+7);
     return (((long)(buf[offset++] & 0xff) << 56) |
      ((long)(buf[offset++] & 0xff) << 48) |
      ((long)(buf[offset++] & 0xff) << 40) |
      ((long)(buf[offset++] & 0xff) << 32) |
      ((long)(buf[offset++] & 0xff) << 24) |
      ((long)(buf[offset++] & 0xff) << 16) |
      ((long)(buf[offset++] & 0xff) <<  8) |
      ((buf[offset++] & 0xff)));
   }

   public float readFloat()  throws ArrayIndexOutOfBoundsException{
      return Float.intBitsToFloat(readInt());
   }

   public double readDouble()  throws ArrayIndexOutOfBoundsException{
        return Double.longBitsToDouble(readLong());
   }

   public String readLine()  throws ArrayIndexOutOfBoundsException{
       if (offset>= buf.length)
         return null;
       StringBuilder sb = new StringBuilder();
      
       while (offset <buf.length) 
           sb.append(readChar());
       
       return sb.toString();
   }

   public String readUTF() {

      int buflen = readUnsignedShort();

      int bufoff = offset;

      offset+=buflen;
      if (offset > limit)
         throw new ArrayIndexOutOfBoundsException(offset);

      char[] charr= new char[buflen];
      int charoffset = 0;

      while (bufoff<offset) {
          int ch = buf[bufoff++] & 0xff;
          if (ch <=127)
             charr[charoffset++]=(char)ch;
          else {
             int ch2 = buf[bufoff++] & 0xff;
             switch ((ch >>4) & 0x7) {
                case 4: case 5: //10x
                   charr[charoffset++] = (char)(((ch& 0x1F) << 6) | (ch2 & 0x3F));
                   break;
                case 6:
                   int ch3 = buf[bufoff++] & 0xff;
                   charr[charoffset++] = (char)(((ch & 0x0F) << 12) 
                      | ((ch2 & 0x3F) << 6) | (ch3 & 0x3F));
                   break;
                default: //0,1,2,3,7
                   throw new ArrayIndexOutOfBoundsException(offset); // really ought to be  dataFormat
            }
         }
      }

      if (bufoff>offset)
         throw new ArrayIndexOutOfBoundsException(bufoff); // really ought to be  dataFormat
         
      return new String(charr,0,charoffset);
      
//trace("readUTF returning " + retval);
   }
/*
   public String readUTF() {

      int len = readUnsignedShort();
      offset+=len;
      if (offset > buf.length)
         throw new ArrayIndexOutOfBoundsException(offset);
      //trace("len = " + len + "offset = " + offset + " limit = " + limit + Testutil.dumphex(buf,offset,limit - offset));
      //trace("readUTF returning " + new String(buf,offset-len,len));
      return new String(buf,offset-len,len);
      
//trace("readUTF returning " + retval);
   }
*/
static void trace(String str) {
   PersistantStack.trace(str,1);
}

}
