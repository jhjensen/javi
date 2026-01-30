package history;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import static history.Tools.trace;

import java.nio.file.StandardOpenOption;

/**
 * Disk-backed stack for persistent undo history.
 *
 * <p>PersistantStack provides a modified stack data structure where inserting
 * in the middle truncates the rest. It backs the array to a .dmp2 disk file,
 * allowing undo history to persist across editor sessions.</p>
 *
 * <h2>Key Characteristics</h2>
 * <ul>
 *   <li><b>Partial memory</b>: May not keep entire array in memory</li>
 *   <li><b>Truncating insert</b>: Adding in middle deletes all following elements</li>
 *   <li><b>Persistent</b>: Backed by .dmp2 file for persistant undo and crash recovery</li>
 *   <li><b>Lazy loading</b>: Elements read from disk on demand</li>
 * </ul>
 *
 * <h2>File Format</h2>
 * <p>The .dmp2 file format stores:</p>
 * <ul>
 *   <li>File identifier/magic number</li>
 *   <li>Offset table for random access to records</li>
 *   <li>Serialized records (undo operations)</li>
 *   <li>User callback data</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Not thread-safe. Caller must ensure synchronization.
 * File locking via {@link FileLock} prevents concurrent access to .dmp2 files.</p>
 *
 * <h2>Known Issues</h2>
 * <ul>
 *   <li>Uses deprecated {@code finalize()} - should migrate to Cleaner (see BUGS.md)</li>
 *   <li>Flush on every push is expensive - should move to close (see todo.md M17)</li>
 * </ul>
 *
 * <h2>Abstract Methods</h2>
 * <p>Subclasses must implement:</p>
 * <ul>
 *   <li>{@link #usercb} - Read user callback data from backup</li>
 *   <li>{@link #deletecb} - Called when elements are removed</li>
 * </ul>
 *
 * @param <E> Element type stored in the stack
 * @see javi.UndoHistory
 * @see ByteInput
 */
public abstract class PersistantStack<E> {

   protected abstract void usercb(ByteInput dis) throws EOFException;
   protected abstract void deletecb(int index);

   public abstract class PSIterator implements ListIterator<E> {

      protected abstract void writeExternal(DataOutputStream dosi, E obj)
         throws IOException;
      protected abstract E readExternal(ByteInput dis) throws IOException;
      protected abstract E newExternal(ByteInput dis) throws IOException;
      protected abstract boolean isOutLine(Object ob) throws IOException;
      protected abstract boolean matches(Object ob, Object ob2);
      private int recordIndex;

      public final int getIndex() {
         return recordIndex;
      }

      public final void setEqual(PSIterator it) {
         recordIndex = it.recordIndex;
      }

      public final void invalidate() {
         recordIndex = -Integer.MAX_VALUE;
      }

      public final boolean isValid() {
         return recordIndex >= -1;
      }

      protected PSIterator() {
         recordIndex = -1;
      }

      public String toString() {
         return "PSIterator index = " + recordIndex + " cache.size = "
            + cache.size() + " size " + size;
      }


      public final boolean hasPrevious() {
         return recordIndex > 0;
      }

      public final void set(Object obj) {
         throw new UnsupportedOperationException("setting unsupported");
      }

      public final int nextIndex() {
         return recordIndex + 1;
      }

      public final int previousIndex() {
         return recordIndex - 1;
      }

      public final void add(Object obj) {
         throw new UnsupportedOperationException("add unsupported");
      }

      public final void decrement() {
         --recordIndex;
      }

      public final void increment() {
         ++recordIndex;
      }

      public final void remove() {
         throw new UnsupportedOperationException("remove unsupported");
      }

      public final boolean hasNext() {
         //trace("recordIndex = " + recordIndex  + " size = " + size);
         //trace("returning = " +(recordIndex+1 < size));
         return recordIndex + 1 < size;
      }

      public final E next() {
         //trace("next pieceIndex = " + recordIndex + " size " + size );
         if (++recordIndex >= size) {
            recordIndex--;
            throw new NoSuchElementException("next past end of list");
         }
         return curr();
      }

      public final E previous() {
         //trace("recordIndex = " + recordIndex + " cache size = " + cache.size());
         --recordIndex;
         if (recordIndex >= size - cache.size())
            return cache.get(recordIndex - (size - cache.size()));
         else  {
            try {
               binp.seek(offsets.get(recordIndex));
               E retval = newExternal(binp);
               cache.add(0, retval);
               return retval;
            } catch (IOException e) {
               throw new RuntimeException(
                  "PersistantStack.curr unexpected exception " + e, e);
            } catch (NullPointerException e) {
               trace("PersistantStack.curr caught " + e);
               e.printStackTrace();
               throw new IndexOutOfBoundsException("no previous object");
            }
         }
      }

      public final E curr() {
         //trace(
         //     " recordIndex = "  + recordIndex
         //    + " binp  = " + binp
         //    + " offsets = "  + offsets
         //    + " size = "  + size + " cache.size = " + cache.size());

         if (recordIndex < size - cache.size())
            try {
               binp.seek(offsets.get(recordIndex));
               return readExternal(binp);
            } catch (IOException e) {
               throw new RuntimeException(
                  "PersistantStack.curr unexpected exception ", e);
            } catch (NullPointerException e) {
               trace("PersistantStack.curr caught " + e);
               e.printStackTrace();
               throw new IndexOutOfBoundsException("no current object");
            }
         else
            return cache.get(recordIndex - (size - cache.size()));
      }

      public final boolean remove(Object obi) { //??? needs test
         //trace("size = " + size + " writtenCount = " + writtenCount);
         for (int i = size - 1; i >= writtenCount; i--) {
            Object ob = cache.get(i - (size - cache.size()));
            if (matches(ob, obi)) {
               cache.remove(i);
               size--;
               recordIndex--;
               return true;
            }
         }
         return false;
      }

      public final void push(E obj) {
         //trace("index = " +  recordIndex
         //   + " size= " + size + " cache.size = " + cache.size()
         //   + " obj = " + obj);

         recordIndex++;
         if (recordIndex != size) {
            cache.subList(cache.size() - (size - recordIndex),
                          cache.size()).clear();
            size = recordIndex;
            if (writtenCount > recordIndex)
               try { //??? dont' want to do any IO here move flush to close
                  flush();
                  if (offsets != null && offsets.size() > recordIndex)
                     offsets.removeRange(recordIndex, offsets.size());
                  if (rfile != null)
                     writePop(recordIndex - 1);
                  writtenCount = recordIndex;
               } catch (IOException e) {
                  trace("PersistantStack caught " + e);
                  e.printStackTrace();
                  throw new RuntimeException("fix this ???", e);
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

      public final void resetCache() throws IOException {
         //trace(
         //    " recordIndex = " + recordIndex
         //   + " size = " + size + " offsets.size = " + offsets.size());
         if (offsets == null) {
            if (recordIndex != -1)
               cache.subList(0, cache.size() - (size - recordIndex)).clear();
         } else {
            cache.clear();
            for (int i = recordIndex == -1 ? 0 : recordIndex; i < size; i++) {
               binp.seek(offsets.get(i));
               cache.add(newExternal(binp));

            }
         }
         //trace(dump());
      }

      public final void close() throws IOException {
         //trace("closing rfile = " +rfile + " recordIndex = " + recordIndex + " size = " + size + dump());
         if (recordIndex >= size)
            throw new IOException("Illegal quitmark recordIndex = "
               + recordIndex + " size = " + size);
         flush();

         if (rfile != null) {

            //trace("writing file");
            if (rfile.length() != filesize) {
               invalidate();
               invalidateFile();
               throw new IOException("inconsistant filesize");
            }
            FileOutputStream fs = new FileOutputStream(rfile, true);
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
         invalidateFile();
         invalidate();
      }

      private void flush() throws IOException {

         int unwritten = size - writtenCount;
         if (unwritten == 0)
            return;
         //trace("unwritten = " + unwritten);
         //trace("size = " + size + " writ" + writtenCount);
         //trace("delayFile = " + delayFile );
         if (delayFile != null && delayFile.exists() && !delayFile.delete())
            throw new RuntimeException("unable to delete file " + delayFile);

         makeReal();

         if (null == rfile)
            return;

         //trace("unwritten = " + unwritten);
         //trace(
         //     " rfile.length = " + rfile.length()
         //    + " filesize " + filesize );
         if (rfile.length() != filesize)
            throw new BadBackupFile("inconsistant filesize");

         DataOutputStream ds =
            new DataOutputStream(
               new BufferedOutputStream(
                  new FileOutputStream(rfile.toString(), true)));
         try {
            for (int ii = size - unwritten; ii < size; ii++) {
               E ob = cache.get(ii - (size - cache.size()));
               //trace("" + ob);
               writeExternal(dos, ob);
               if (isOutLine(ob)) {
                  ds.write(0);
                  ds.write(USERCALLBACK);
                  ds.write(bufoff);
                  ds.write(writebuffer, 0, bufoff);
                  ds.write(bufoff);
                  ds.write(USERCALLBACK);
                  ds.write(0);
               } else {
                  if (bufoff > 255) {
                     ds.write(0);
                     ds.write(LONGRECORD);
                     ds.writeInt(bufoff);
                  } else {
                     ds.write(bufoff);
                  }
                  ds.write(writebuffer, 0, bufoff);
                  if (bufoff > 255) {
                     ds.writeInt(bufoff);
                     ds.write(LONGRECORD);
                     ds.write(0);
                  } else {
                     ds.write(bufoff);
                  }
               }
               bufoff = 0;
            }
         } finally {
            ds.close();
         }
         //ds.close();System.exit(0);
         filesize += ds.size();
         writtenCount = size;
         //trace("exit unwritten = " + unwritten);
      }

      public final void idleSave() throws IOException {
         flush();
      }

      public final boolean beforeQuit() {
         return recordIndex < lastquit;
      }
   }

   final void makeReal() throws IOException {

      if (delayFile != null) {
         fc = FileChannel.open(delayFile.toPath(),
             StandardOpenOption.CREATE,
             StandardOpenOption.READ,
             StandardOpenOption.WRITE);
         lock = fc.tryLock();
         if (lock == null)
             throw new FileLockException("unable to obtain lock on file");
         rfile = delayFile;
         delayFile = null;
      }
   }

   protected PersistantStack() {
      //trace("constructed");
      rfile = null;
      //dos = new DataOutputStream(bwr);
      quitAtEnd = true;
   }

   public final boolean isQuitAtEnd() {
      //trace("lastquit = " + lastquit + " size = " + size);
      return lastquit == (size - 1);
   }

   public final boolean cleanClose() {
      return quitAtEnd;
   }

   final void checkQuit(int i) { // for testcase
      Testutil.myassert(lastquit == i, Integer.valueOf(lastquit));
   }

   public String toString() {
      return "PersitantStack " + rfile + "\n" + dump();
   }

   public final boolean hasFile() {
      //trace("rfile = " + rfile + " delayFile = " + delayFile);
      return null != rfile || null != delayFile;
   }

   public final void pushend(E obj) {
      //trace("index = " +
      //    recordIndex + " size= " + size
      //    + " cache.size = " + cache.size()
      //    + " obj = " + obj);
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
      if (rfile.length() != filesize)
         throw new IOException("inconsistant filesize");

      DataOutputStream ds =
         new DataOutputStream(
            new BufferedOutputStream(
               new FileOutputStream(rfile.toString(), true)));
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
      offsets.removeRange(popPoint + 1, size);
      size = popPoint + 1;
      ds.readByte(); // skip opcode
      ds.readByte(); // skip 0
      //trace("size = " + size + " offsets.size = " + offsets.size());
      return popPoint;
   }

   private byte[] readFile() throws IOException {
      if (fc.size() > Integer.MAX_VALUE)
          throw new IOException("file to large");
      int length = (int) fc.size();

      ByteBuffer buf = ByteBuffer.allocate(length);
      int ilen = fc.read(buf);
      if (ilen != length)
         throw new RuntimeException(
            "filereader.fopen: read in length doesnt match");
      return buf.array();
   }

   final void readUserCB() throws IOException {
      int len = 0x000000ff & binp.readByte();
      offsets.add(binp.getOffset());
      //trace("adding len = " + len + " offset = "  + binp.getOffset());
      size++;
      usercb(binp);
      if (len != (0x000000ff & binp.readByte())) {
         throw new IOException("USER callback bad length");
      }
      if (binp.readByte() != USERCALLBACK)
         throw new IOException("USER callback bad opcode");
      if (0 != binp.readByte())
         throw new IOException("USER callback bad opcode");
   }

   final void readQuit() throws IOException {
      lastquit = binp.readInt();
      if (lastquit >= size)
         throw new IOException("inconsistant quit mark size "
                               + size + " lastquit " + lastquit);
      binp.readByte(); // skip quitmark
      binp.readByte(); // skip 0
      //trace("Quitmark avail = " + binp.available());
      if (0 == binp.available())
         quitAtEnd = true;
   }

   final void readObj(int len) {
      //trace("adding len = " + len + " offset = "  + binp.getOffset());
      offsets.add(binp.getOffset());
      size++;
      binp.skipBytes(len);
   }

   public final Exception setFile(File filei) throws IOException {
      //trace("set file file", filei, "len", filei.length() );
      if (filei.equals(rfile))
         return null;
      //FileChannel fc = new FileInputStream("regtest").getChannel();

      // Get the file's size and then map it into memory
      //int sz = (int)fc.size();

      // Decode the file into a char buffer

      //CharBuffer cb = Charset.forName("windows-1252").newDecoder().decode(fc.map(FileChannel.MapMode.READ_ONLY, 0, sz));

      reset();

      delayFile = filei;
      makeReal();
      int lastgood = 0;
      byte[] iarray = null;
      try {
         iarray = readFile();
         offsets = new IntArray(iarray.length / 20 + 1); // guessing at size

         for (binp = new ByteInput(iarray); 0 != binp.available();) {
            int len = 0x000000ff & binp.readByte();
            if (0 == len) {
               int op = binp.readByte();
               switch (op) {
                  case POP:
                     deletecb(readPop(binp));
                     break;
                  case LONGRECORD:
                     len = binp.readInt();
                     readObj(len);
                     int len1 = binp.readInt();
                     if (len1 != len)
                        throw new IOException("long lengths don't match");
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
                     throw new IOException("PersistantArray got bad opcode:"
                        + op + " offset = " + (binp.getOffset() - 1));
               }
            } else {
               readObj(len);
               int len1 = 0x000000ff & binp.readByte();
               if (len1 != len)
                  throw new IOException("lengths don't match");
            }
            lastgood = binp.getOffset();
         }
      } catch (IOException e) {
         writtenCount = size;
         if (lastquit >= size)
            lastquit = size;
         // truncate file
         filesize = lastgood;

         if (null != iarray)
            try {
               File file2 = new File(filei.getCanonicalPath() + ".bad");
               if (filei.renameTo(file2)) {

                  FileOutputStream output = new FileOutputStream(rfile);
                  try {
                     output.write(iarray, 0, filesize);
                  } finally {
                     output.close();
                  }
               }
            } catch (Throwable e1) {
               trace("failed to save file on IOException " + e1);
            }
         return e;
      }
      writtenCount = size;
      filesize = (int) rfile.length();
      if (lastquit >= size) {
         lastquit = size;
         return new IOException("inconsistant quitmark size " + size);
      }
      //trace(
      //     " writtenCount = " + writtenCount  + " offsets = " + offsets
      //    + " size = " + size + " lastquit = " + lastquit);
      return null;
   }

   public final void newFile(File file) {
      //trace(file);
      delayFile = file;
   }

   public final StringBuilder dump() {

      StringBuilder sb = new StringBuilder("   dumping ran cache starts at "
         + (size - cache.size())  + "\n");
      //int unwritten = store.size() - writtenCount;
      for (Object ob : cache) {
         if (ob instanceof byte[])
            sb.append(Testutil.dumphex((byte[]) ob));
         else
            sb.append(ob.toString());
         sb.append("\n    ");
      }
      return sb;
   }

   final void kill() throws IOException {
      // trace("deleteing " + rfile);
      if (!rfile.delete())
         throw new IOException("unable to delete " + rfile);
      invalidateFile();
   }

   public final void  terminateWEP() throws IOException {
      // test entry to simulate sudden death of system.
      invalidateFile();
      rfile = null;
   }

   private final class ByteWriter extends OutputStream {

      void expandBuffer(int minsize) {
         int newsize = 2 * writebuffer.length;
         while (newsize < minsize)
            newsize *= 2;
         byte[] b2 = new byte[newsize];
         System.arraycopy(writebuffer, 0, b2, 0, writebuffer.length);
         writebuffer = b2;
      }

      public void write(int i) {
         //trace("byteWriter.write int = " + i);
         if (bufoff >= writebuffer.length)
            expandBuffer(writebuffer.length + 1);
         writebuffer[bufoff++] = (byte) i;
      }

      public void write(byte[] b) {
         write(b, 0, b.length);
      }

      public void write(byte[] b, int off, int len) {
         if (len + bufoff >= writebuffer.length)
            expandBuffer(len + bufoff);
         System.arraycopy(b, off, writebuffer, bufoff, len);
         bufoff += len;
      }

      public void flush() throws IOException {
         if (bufoff > 0)
             throw new IOException("unsupported flush " + bufoff);
      }
      public void close() {
      }

   }

   final int size() {
      return size;
   }

   @SuppressWarnings("removal")
   @Deprecated(forRemoval = true)
   protected final void finalize() throws Throwable {
      if (writtenCount != -1)
         trace(
            "*********************************************************\n"
            + "PersitantStack.finalize found unclosed file\n"
            +  " writtencount = " + writtenCount
            +  " rfile = " + rfile
            + "\n*********************************************************\n");
      super.finalize();
   }

   public final void reset() throws IOException { //??? needs test

      quitAtEnd = false;
      delayFile = null;
      rfile = null;

      cache.clear();
      offsets = null;

      size = 0;
      writtenCount = 0;
      filesize = 0;
      lock = null;

      FileChannel tfc = fc;
      fc = null;
      FileLock tlock = lock;
      lock = null;
      if (tlock != null)
          tlock.close();
      if (tfc != null)
          tfc.close();
   }

   private void invalidateFile() throws IOException {
      reset();
      bwr = null;
      writebuffer = null;
      size = -1;
      bufoff = -1;
      writtenCount = -1;
//      cache=null;
      try {
         if (dos != null) {
            dos.close();
         }
      } catch (IOException e) {
         trace("caught IOException closing dos " + e);
         e.printStackTrace();
      } finally {
         dos = null;
      }
   }

   private File delayFile;
   private ArrayList<E> cache = new ArrayList<E>();
   private int size = 0;
   private ByteWriter bwr = new ByteWriter();
   private DataOutputStream dos = new DataOutputStream(bwr);
   private byte[] writebuffer = new byte[16];
   private int bufoff;
//   private byteInput brd = new byteInput(new byte[0]);
   private int lastquit = -1;
   private File rfile;
   private int writtenCount;
   private static final byte POP = 1;
   private static final byte LONGRECORD = 2;
   private static final byte QUITMARK = 3;
   private static final byte USERCALLBACK = 4;
   private boolean quitAtEnd;
   private IntArray offsets = null;
   private ByteInput binp = null;
   private int filesize;
   private FileChannel fc;
   private FileLock lock;


}
