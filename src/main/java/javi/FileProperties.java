package javi;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.Iterator;
import java.nio.charset.Charset;
import java.nio.file.Files;

//import static history.Tools.trace;

public final class FileProperties<OType> implements Serializable {

   private enum FileMode { UNIX, MS, MIXED };
   static final String staticLine = System.getProperty("line.separator");
   private static final long serialVersionUID = 1;
   public final FileDescriptor fdes;
   public final ClassConverter<OType> conv;
   private transient Charset charSet;
   private transient String fileString;
   private transient boolean readonly = false;

   private String lsep = staticLine; //??? final

   public String toString() {
      return fdes.toString();
   }

   public synchronized void setReadOnly(boolean flag) {
      readonly = flag;
   }

   public synchronized boolean isWriteable() {
      return !readonly;
   }

   void safeWrite(Iterator<String> strIter) throws IOException {
      if (fdes.exists() && fdes instanceof  FileDescriptor.LocalFile) {
         FileDescriptor.LocalFile fd2 = (FileDescriptor.LocalFile) fdes;

         FileDescriptor.LocalFile tempFile =
            FileDescriptor.LocalFile.make(fd2.canonName + ".new");

         try {
            new FileProperties(this, tempFile).writeAll(strIter);

         } catch (IOException e) {
            tempFile.delete();
            throw e;
         }

         Files.setPosixFilePermissions(tempFile.toPath(),
             Files.getPosixFilePermissions(fdes.toPath()));
         tempFile.renameTo(fd2, false);
      } else {
         writeAll(strIter);
      }
   }

   void writeAll(Iterator<String> sitr)  throws IOException {

      OutputStreamWriter ow = new OutputStreamWriter(
         new BufferedOutputStream(fdes.getOutputStream()), charSet);

      try {
         while (sitr.hasNext()) {
            String line = sitr.next();
            //trace("writing:" + line);
            ow.write(line, 0, line.length());
            ow.write(lsep, 0, lsep.length());
         }
         ow.flush();
      } finally {
         ow.close();
      }
   }

   public FileProperties(FileDescriptor fd, ClassConverter<OType> convi) {
      fdes = fd;
      conv = convi;
      charSet = Charset.defaultCharset();
      fileString = "";
   }

   // create properties that will have the same format as a prototype
   public FileProperties(FileProperties proto, FileDescriptor fd) {
      fdes = fd;
      conv = proto.conv;
      charSet = proto.charSet;
      fileString = "";
      lsep = proto.lsep;
   }

   public String initFile() throws IOException {
      fileString = fdes.getString();
      //trace("fileString:" + fileString);
      int npos = fileString.indexOf('\n');
      int rpos = fileString.indexOf('\r');

      lsep = rpos == -1
         ? "\n"
         : rpos + 1 == npos
            ? "\r\n"
            : System.getProperty("line.separator");

      return fileString;
   }
}
