
class RanFileArray {
   // this class implements a file that has records added only at the end. the records can be
   // read efficiently from either the beginning or the end.

   class rchunk {
      final int offset;
      final int len;
      rchunk(int offseti,int leni) {
         offset = offseti;
         len = leni;
      }
   }

   void setBackingFile(File bfile) {
      boolean exists = bfile.exists();
 
      if (exists && writtenIndex !=0)
         throw new IOException("RanFileArrray setting file when both file and cache have data");

      if (exists)
          flush();

      else {
         byte [] iarray=null;
         try {
            rfile=bfile;
            iarray = readFile();

            setFileMode=true;
            if (readRec==null) 
               readRec = new ArrayList<rchunk>(iarray.length/20+1); // guessing at size
            else 
               readRec.clear();

            binp.setBuf(iarray,0,iarray.length);

            while(binp.available()!=0) {
               int len = 0x000000ff & binp.readByte();
               int offset;
               if (len==0) {
                  len = binp.readInt();
                  offset = binp.getOffset();
                  binp.skipBytes(len);
                  if (binp.readInt() != len)
                    throw new IOException ("long lengths don't match");
                  if (0!= binp.readByte())
                    throw new IOException ("long op don't match");
                  break;
               } else {
                  offset = binp.getOffset();
                  binp.skipBytes(len);
                  int len1 = 0x000000ff & binp.readByte();
                  if (len1 != len)
                     throw new IOException ("lengths don't match");
               }
               readRec.add(new rchunk(offset,len));
            }
         } finally {
            writtenIndex = readRec.size();
            filesize=iarray.length;
         }
      //trace(
      //     " writtenCount = " + writtenCount  + " offsets = " + offsets
      //    + " size = " + offsets.size + " );
      }
   }

   private File rfile;
   private ArrayList<rchunk> readRec;
   private ArrayList<byte[]> unWritten;
   
   private int writtenIndex;
   private int filesize;
   private final ByteInput binp = new ByteInput(null);
   private boolean setFileMode=false;

   ByteInput getBytes(int record) {
      if (setFileMode)
         binp.setBuf(readRec.get(record));
      else
         binp.setBuf(offsets2.get(record));
      return binp;
   }

   public void resetCache() throws IOException{
      // called when user is done intial reading in of file.
      if ((bfile==null)) {
         throw new RuntimeException("fix this");
      } else {
         binp.setBuf(null,0,0);
         flush();
         readRec=null;
         unWritten=new ArrayList<byte[]>();
      }
   }

   void appendRecord(byte[] rec) {
      if (setFileMode)
         throw new RuntimeException("don't call this until setFile is done processing");
      unWritten.add(rec);
         
   }

   void flush() {
       FileOutputStream fs = new FileOutputStream(rfile.toString(),true);
       DataOutputStream ds = new DataOutputStream(fs);
       for (Chunk ch:  unWritten) {
          if (ch.len>255) {
             ds.write(0);
             ds.write(LONGRECORD);
             ds.writeInt(bufoff);
          } else {
             ds.write(bufoff);
          }
          ds.write(writebuffer,0,bufoff);
          if (bufoff>255) {
             ds.writeInt(bufoff);
             ds.write(LONGRECORD);
             ds.write(0);
          } else {
             ds.write(bufoff);
          }
      }
      unWritten.clear();
      filesize += ds.size();
      writtenCount=size;
   }

   void clear() {
      flush();
      unWritten.trimToSize();
   }
    
   void rebuild() {
   }
  
   private byte [] readFile() throws IOException { 
      FileInputStream input = new FileInputStream(rfile);
      try {
         int length=(int)rfile.length();
         byte[] iarray=new byte[length];
         int ilen = input.read(iarray,0,length);
         if (ilen!=length)
            throw new RuntimeException("filereader.fopen: read in length doesnt match");
            return iarray;
         } finally {
         input.close();
      }
   }
}
