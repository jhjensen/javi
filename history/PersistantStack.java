package history;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.NoSuchElementException;

// PersistantStack is a funny sort of Stack.  If you add an element
// in the middle the rest of the array is deleted.  The array is backed by a 
// disk file, and in fact may not keep any of the array in memory.
// 

public abstract class PersistantStack {
   protected abstract void usercb(ByteInput dis) throws EOFException;
   protected abstract void deletecb(int index);

   abstract public class PSIterator implements ListIterator{
      protected abstract void writeExternal(DataOutputStream dosi,Object obj)
         throws IOException;
      protected abstract Object readExternal(ByteInput dis)throws IOException ;
      protected abstract Object newExternal(ByteInput dis)throws IOException ;
      protected abstract boolean isOutLine(Object ob)throws IOException ;
      protected abstract boolean matches(Object ob,Object ob2);
      private int recordIndex;

      public int getIndex() {
         return recordIndex;
      }

      public void setEqual(PSIterator it) {
         recordIndex = it.recordIndex;
      }

      public void setInvalid() {
         recordIndex = -Integer.MAX_VALUE;
      }
      public boolean isValid() {
         return recordIndex>=-1;
      }
      protected PSIterator() {
          recordIndex=-1;
      }

      public String toString() {
         return "PSIterator index = " + recordIndex + " cache.size = " +
            cache.size() + " size " + size;
      }
      
         
      public boolean hasPrevious() {
         return recordIndex>0;
      } 

      public void set(Object obj) {
         throw new UnsupportedOperationException();
      }
      public int nextIndex() {return recordIndex+1;}
      public int previousIndex() {return recordIndex-1;}

      public void add(Object obj) {
         throw new UnsupportedOperationException();
      }

      public void decrement() {
         --recordIndex;
      }

      public void increment() {
         ++recordIndex;
      }

      public void remove() {
         throw new UnsupportedOperationException();
      }

      public boolean hasNext(){
         //trace("recordIndex = " + recordIndex  + " size = " + size);
         //trace("returning = " +(recordIndex+1 < size));
         return recordIndex+1 < size;
      }

      public Object next() {
          //trace("next pieceIndex = " + recordIndex + " size " + size );
          if (++recordIndex >= size) {
             recordIndex --;
             throw new NoSuchElementException();
          }
          return curr();
      }

      public Object previous() {
          //trace("recordIndex = " + recordIndex + " cache size = " + cache.size());
          --recordIndex;
          Object retval;
          if (recordIndex>=size - cache.size()) 
             retval = cache.get(recordIndex-(size - cache.size()));
          else  {
             try {
                binp.seek(offsets.get(recordIndex));
                retval = newExternal(binp);
                cache.add(0,retval);
             } catch (IOException e) {
                throw new RuntimeException(
                    "PersistantStack.curr unexpected exception " + e,e);
             } catch (NullPointerException e) {
                trace("PersistantStack.curr caught " + e);
                e.printStackTrace();
                throw new IndexOutOfBoundsException ();
             }
          }
             
          return retval;
      }

      public Object curr() {
          //trace(
          //     " recordIndex = "  + recordIndex 
          //    + " binp  = " + binp
          //    + " offsets = "  + offsets
          //    + " size = "  + size + " cache.size = " + cache.size());

          Object retval;
          if (recordIndex<size - cache.size()) 
             try {
                binp.seek(offsets.get(recordIndex));
                retval = readExternal(binp);
             } catch (IOException e) {
                throw new RuntimeException(
                    "PersistantStack.curr unexpected exception " ,e);
             } catch (NullPointerException e) {
                trace("PersistantStack.curr caught " + e);
                e.printStackTrace();
                throw new IndexOutOfBoundsException ();
             }
          else
             retval = cache.get(recordIndex-(size - cache.size()));
          //trace("returning " + retval + dump());
          return retval;
             
      }

      public boolean remove(Object obi) { //??? needs test
         //trace("size = " + size + " writtenCount = " + writtenCount);
         for (int i=size-1;i>=writtenCount;i--) {
             Object ob = cache.get(i-(size -cache.size()));
             if (matches(ob,obi)) {
                cache.remove(i);
                size--;
                recordIndex--;
                return true;
              }
         }
         return false;
      }
     
      public void push(Object obj) {
         //trace("index = " +  recordIndex
         //   + " size= " + size + " cache.size = " + cache.size()
         //   + " obj = " + obj);
         if (delayFile!=null) {
            if (delayFile.exists())
               if (!delayFile.delete()) 
                  throw new RuntimeException("unable to delete file " + delayFile);
            rfile=delayFile;
            delayFile=null;
         }
         recordIndex++;
         if (recordIndex!= size) {
            cache.subList(cache.size() - (size-recordIndex), 
                   cache.size()).clear();
            size = recordIndex ;
            if (writtenCount > recordIndex) try { //??? dont' want to do any IO here move flush to close
               flush();
               if (offsets != null && offsets.size() > recordIndex) 
                  offsets.removeRange(recordIndex,offsets.size());
               if (rfile != null) 
                  writePop(recordIndex-1);
               writtenCount=recordIndex;
            } catch (IOException e) {
                  trace("PersistantStack caught " + e);
                  e.printStackTrace();
                  throw new RuntimeException("fix this ???",e);
            }
     
         }
         cache.add(obj);
         size++;
         //trace("exit index = " + recordIndex + " size= " + size);
         //trace("dump= " + dump());
         //trace("index = " + 
         //    recordIndex + " size= " + size 
         //    + " cache.size = " + cache.size()
         //    + " obj = " + obj);
      }

      public void resetCache() throws IOException{
         //trace(
         //    " recordIndex = " + recordIndex
         //   + " size = " + size + " offsets.size = " + offsets.size());
         if (offsets==null) {
            if (recordIndex != -1)
               cache.subList(0,cache.size() - (size-recordIndex)).clear();
         } else {
            cache.clear();
            for (int i=recordIndex == -1 ? 0: recordIndex;i <size ; i++) {
               binp.seek(offsets.get(i));
               cache.add(newExternal(binp));

            }
         }
         //trace(dump());
      }

      public void close() throws IOException{
          //trace("rfile = " +rfile + " recordIndex = " + recordIndex + " size = " + size + dump());
          if (recordIndex >= size) 
             throw new IOException("Illegal quitmark recordIndex = " + recordIndex + " size = " + size );
   
          flush();

          if (rfile != null) {
   
             //trace("writing file");
             if (rfile.length()!= filesize) {
                invalidate();
                throw new IOException("inconsistant filesize");
             }
             FileOutputStream fs = new FileOutputStream(rfile,true);
             DataOutputStream ds = new DataOutputStream(fs);
             try {
                ds.write(0);
                ds.write(QUITMARK);
                ds.writeInt(recordIndex);
                ds.write(QUITMARK);
                ds.write(0);
                filesize += ds.size();
             } finally {
                ds.close();
             }
          }
          invalidate();
      }

      private void flush() throws IOException {
   
          int unwritten = size - writtenCount;
          if (unwritten==0)
             return;
          //trace("unwritten = " + unwritten);
          //trace("size = " + size + " writ" + writtenCount);
          //trace("delayFile = " + delayFile );
          if (delayFile != null && delayFile.exists()){
             if (!delayFile.delete()) 
                throw new IOException("unable to delete file " + delayFile);
              rfile=delayFile; 
          }

          if (rfile==null)
             return;

          //trace("unwritten = " + unwritten);
          //trace(
          //     " rfile.length = " + rfile.length()
          //    + " filesize " + filesize );
          if (rfile.length()!= filesize)
             throw new IOException("inconsistant filesize");
          FileOutputStream fs = new FileOutputStream(rfile.toString(),true);
          DataOutputStream ds = new DataOutputStream(fs);
          try {
          for (int i=size-unwritten;i<size;i++) {
             Object ob = cache.get(i-(size -cache.size()));
             //trace("" + ob);
             writeExternal(dos,ob);
             if (isOutLine(ob)) {
                ds.write(0);
                ds.write(USERCALLBACK);
                ds.write(bufoff);
                ds.write(writebuffer,0,bufoff);
                ds.write(bufoff);
                ds.write(USERCALLBACK);
                ds.write(0);
             } else {
                if (bufoff>255) {
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
             bufoff=0;
          }
          } finally {
             ds.close();
          }
   //ds.close();System.exit(0);
          filesize += ds.size();
          writtenCount=size;
          //trace("exit unwritten = " + unwritten);
      }

      public void idleSave() throws IOException {
         flush(); 
      }

      public boolean beforeQuit() {
           return (recordIndex < lastquit);
      }
   }

   public PersistantStack() {
      //trace("constructed");
      rfile = null;
      //dos = new DataOutputStream(bwr);
      quitAtEnd=true;
   }

   public boolean isQuitAtEnd() {
      //trace("lastquit = " + lastquit + " size = " + size);
      return lastquit == (size-1);
   }
 
   public boolean cleanClose() {
      return quitAtEnd;
   }

   void checkQuit(int i) { // for testcase
    Testutil.myassert(lastquit == i,Integer.valueOf(lastquit));
   }

   public String toString() {
      return "PersitantStack " + rfile + "\n" + dump();
   }

   public boolean hasFile() {
     //trace("rfile = " + rfile + " delayFile = " + delayFile);
     return rfile!=null || delayFile != null;
   }
 
   public void pushend(Object obj) {
         //trace("index = " + 
         //    recordIndex + " size= " + size 
         //    + " cache.size = " + cache.size()
         //    + " obj = " + obj);
         if (delayFile!=null) {
            if (delayFile.exists())
               delayFile.delete();
            rfile=delayFile;
            delayFile=null;
         }
         cache.add(obj);
         size++;
         //trace("exit index = " + 
         //    recordIndex + " size= " + size);
         //trace("dump= " + dump());
         //trace("index = " + 
         //    recordIndex + " size= " + size 
         //    + " cache.size = " + cache.size()
         //    + " obj = " + obj);
   }
   private void writePop(int index) throws IOException {
       if (rfile.length()!= filesize)
          throw new IOException("inconsistant filesize");
       FileOutputStream fs = new FileOutputStream(rfile.toString(),true);
       DataOutputStream ds = new DataOutputStream(fs);
       try {
       ds.write(0);  // mark as special record
       ds.write(POP);  // mark as pop
       ds.writeInt(index);
       ds.write(POP); // backwards pop
       ds.write(0);  // mark as special record
          filesize += ds.size();
        } finally {
       ds.close();
        }
   }
     
   private int readPop(ByteInput ds) { // opcode read
      //trace("size = " + size + " offsets.size = " + offsets.size());
      int popPoint = ds.readInt();
      //trace("pop = " + popPoint );
      offsets.removeRange(popPoint+1, size);
      size = popPoint+1;
      ds.readByte(); // skip opcode
      ds.readByte(); // skip 0
      //trace("size = " + size + " offsets.size = " + offsets.size());
      return popPoint;
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

   void readUserCB() throws IOException {
      int len = 0x000000ff & binp.readByte();
      offsets.add(binp.getOffset());
      //trace("adding len = " + len + " offset = "  + binp.getOffset());
      size++;
      usercb(binp);
      if (len!= (0x000000ff & binp.readByte())) {
         throw new IOException("USER callback bad length");
      }
      if (binp.readByte() != USERCALLBACK)
         throw new IOException("USER callback bad opcode");
      if (0!=binp.readByte())
         throw new IOException("USER callback bad opcode");
   }

   void readQuit() throws IOException {
      lastquit = binp.readInt();
      if (lastquit>=size)
         throw new IOException("inconsistant quit mark size "
             + size + " lastquit " + lastquit);
      binp.readByte(); // skip quitmark
      binp.readByte(); // skip 0
      //trace("Quitmark avail = " + binp.available());
      if (binp.available()==0)
         quitAtEnd=true;
   }

   void readObj(int len) {
         //trace("adding len = " + len + " offset = "  + binp.getOffset());
         offsets.add(binp.getOffset());
         size++;
         binp.skipBytes(len);
   }

   public Exception setFile(File filei) {
      //trace("file =  " + filei);
      if (filei.equals(rfile)) 
          return null;
    //FileChannel fc = new FileInputStream("regtest").getChannel();
    
    // Get the file's size and then map it into memory
    //int sz = (int)fc.size();
    
    // Decode the file into a char buffer
    
    //CharBuffer cb = Charset.forName("windows-1252").newDecoder().decode(fc.map(FileChannel.MapMode.READ_ONLY, 0, sz));

      reset();

      rfile = filei;
      int lastgood=0;
      byte [] iarray=null;
      try {
         iarray = readFile();
         offsets = new IntArray(iarray.length/20+1); // guessing at size
         binp = new ByteInput(iarray);
         while(binp.available()!=0) {
            int len = 0x000000ff & binp.readByte();
            if (len==0) {
               int op;
               switch (op = binp.readByte()) {
                  case POP:
                      deletecb(readPop(binp));
                      break;
                  case LONGRECORD:
                      len = binp.readInt();
                      readObj(len);
                      int len1 = binp.readInt(); 
                      if (len1 != len)
                        throw new IOException ("long lengths don't match");
                      binp.readByte(); // LONGRECORD
                      binp.readByte(); // 0 marker
                      break;
                  case USERCALLBACK:
                     readUserCB();
                     break;
                       
                  case QUITMARK:
                      readQuit();
                      break;
                   default: 
                      throw new IOException("PersistantArray got bad opcode:"+ op + " offset = " + (binp.getOffset() - 1));
                }
            } else {
               readObj(len);
               int len1 = 0x000000ff & binp.readByte();
               if (len1 != len)
                  throw new IOException ("lengths don't match");
            }
            lastgood = binp.getOffset();
         }
      } catch (IOException e) {
         writtenCount = size;
         if (lastquit >=size)
            lastquit = size;
         // truncate file
         filesize=lastgood;

         if (iarray != null )try {
            File file2 = new File(filei.getCanonicalPath() + ".bad");
            if (filei.renameTo(file2)) {
            
               FileOutputStream output = new FileOutputStream(rfile);
               try {
               output.write(iarray,0,filesize);
               } finally {
               output.close();
            }
            }
         } catch (Throwable e1) {/*ignore any exception is catch block */ }
         return e;
      }
      writtenCount = size;
      filesize=(int)rfile.length();
      if (lastquit >=size) {
         lastquit = size;
         return new IOException("inconsistant quitmark size " + size);
      }
      //trace(
      //     " writtenCount = " + writtenCount  + " offsets = " + offsets
      //    + " size = " + size + " lastquit = " + lastquit);
      return null;
   }

   public void newFile(File file) { 
       //trace(file);
       delayFile=file;
   }

   public StringBuffer dump() {
     
       StringBuffer sb = new StringBuffer("   dumping ran cache starts at " + (size-cache.size())  + "\n" );
       //int unwritten = store.size() - writtenCount;
       for (Object ob :cache) {
          if (ob instanceof byte[])
             sb.append(Testutil.dumphex((byte[])ob));
          else
             sb.append(ob.toString());
          sb.append("\n    ");
        }
        return sb;
   }

   void kill() throws IOException {
      // trace("deleteing " + rfile);
      if (!rfile.delete())
          throw new IOException("unable to delete " + rfile);
      invalidate();
   }
   public void  terminateWEP() {  // test entry to simulate sudden death of system.
       invalidate();
       rfile = null;
   }
       
   private class ByteWriter extends OutputStream {

     void expandBuffer(int minsize) {
         int newsize =2*writebuffer.length;
         while (newsize < minsize)
            newsize *= 2;
         byte[] b2 = new byte[newsize];
         System.arraycopy(writebuffer,0,b2,0,writebuffer.length);
         writebuffer=b2;
     }

     public void write(int i) throws IOException {
     //trace("byteWriter.write int = " + i);
       if (bufoff >= writebuffer.length)
          expandBuffer(writebuffer.length+1);
       writebuffer[bufoff++] = (byte)i;
     }

     public void write(byte[] b)  throws IOException{
         write(b,0,b.length);
     }
     public void write(byte[] b,int off, int len)  throws IOException{
         if (len+bufoff >=writebuffer.length)
            expandBuffer(len+bufoff);
         System.arraycopy(b,off,writebuffer,bufoff,len);
         bufoff +=len;
     }
     void seek(long offset) throws IOException{
        throw new IOException();
     }
     long getFilePointer()throws IOException {
        throw new IOException();
     }
     public void flush() throws IOException{
        throw new IOException();
     }

   }
     
   int size() {
      return size;
   }

   protected void finalize() {
      if ( writtenCount !=-1)
         trace(
           "*********************************************************\n" 
           + "PersitantStack.finalize found unclosed file\n"
           +  " writtencount = " + writtenCount 
           +  " rfile = " + rfile 
           + "\n*********************************************************\n" );
                            
   }

   public void reset() { //??? needs test
      quitAtEnd=false;
      delayFile=null;
      rfile=null;
      cache.clear();
      offsets=null;
      size=0;
      writtenCount=0;
      filesize=0;
   }

   private void invalidate() {
       reset();
       bwr = null;
       writebuffer = null;
       dos = null;
       size=-1;
       bufoff = -1;
       writtenCount= -1;
   }

   private File delayFile;
   private ArrayList<Object> cache= new ArrayList<Object>();
   private int size=0;
   private ByteWriter bwr = new ByteWriter();
   private DataOutputStream dos = new DataOutputStream(bwr);
   private byte [] writebuffer = new byte[16];
   private int bufoff;
//   private byteInput brd = new byteInput(new byte[0]);
   private int lastquit=-1;  
   private File rfile;
   private int writtenCount;
   private static final byte POP=1;
   private static final byte LONGRECORD=2;
   private static final byte QUITMARK=3;
   private static final byte USERCALLBACK=4;
   private boolean quitAtEnd;
   private IntArray offsets = null;
   private ByteInput binp = null;
   private int filesize;

static void trace(String str) {
   trace(str,1);
}

static void trace(String str,int offset) {
   try { 

       throw new Exception("");
   } catch (Exception e) {
      
      StackTraceElement[] tr = e.getStackTrace();
      System.err.println(tr[1+offset].getClassName().replace('$','.') + "." + tr[1+offset].getMethodName() + " " + str);
   }
}

}

