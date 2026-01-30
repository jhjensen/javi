package javi;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.Iterator;
import java.nio.charset.Charset;
import java.nio.file.Files;

//import static history.Tools.trace;

/**
 * Properties and metadata for an edited file.
 * 
 * <p>FileProperties tracks file format (line endings, charset), read-only status,
 * and external modification detection. It handles file I/O operations like
 * safe write (write to temp, then rename).</p>
 * 
 * @param <OType> Element type stored in the EditContainer (typically String)
 */
public final class FileProperties<OType> implements Serializable {

   private enum FileMode { UNIX, MS, MIXED };
   static final String staticLine = System.getProperty("line.separator");
   private static final long serialVersionUID = 1;
   public final FileDescriptor fdes;
   public final ClassConverter<OType> conv;
   private transient Charset charSet;
   private transient String fileString;
   private transient boolean readonly = false;

   /** Last known modification time of the file on disk. */
   private transient long lastModifiedTime = 0;

   /** If true, ignore external modifications for this file. */
   private transient boolean ignoreExternalChanges = false;

   private String lsep = staticLine; //??? final

   public String toString() {
      return fdes.toString();
   }

   /**
    * Set whether to ignore external changes to this file.
    * 
    * @param ignore true to ignore external changes
    */
   public synchronized void setIgnoreExternalChanges(boolean ignore) {
      ignoreExternalChanges = ignore;
   }

   /**
    * Check if external changes should be ignored for this file.
    * 
    * @return true if external changes are being ignored
    */
   public synchronized boolean isIgnoringExternalChanges() {
      return ignoreExternalChanges;
   }

   /**
    * Check if the file has been modified externally since last check.
    * 
    * <p>Compares current file modification time against stored time.
    * Returns false if the file is not a local file or if external
    * changes are being ignored.</p>
    * 
    * @return true if file was modified externally
    */
   public synchronized boolean checkModified() {
      if (ignoreExternalChanges) {
         return false;
      }
      if (!(fdes instanceof FileDescriptor.LocalFile)) {
         return false;
      }
      FileDescriptor.LocalFile lf = (FileDescriptor.LocalFile) fdes;
      if (!lf.exists()) {
         return false;
      }
      long currentModTime = lf.lastModified();
      return lastModifiedTime != 0 && currentModTime != lastModifiedTime;
   }

   /**
    * Update stored modification time to current file modification time.
    * 
    * <p>Call this after reading or writing the file, or after the user
    * decides to ignore an external change notification.</p>
    */
   public synchronized void updateModifiedTime() {
      if (fdes instanceof FileDescriptor.LocalFile) {
         FileDescriptor.LocalFile lf = (FileDescriptor.LocalFile) fdes;
         if (lf.exists()) {
            lastModifiedTime = lf.lastModified();
         }
      }
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
            new FileProperties<>(this, tempFile).writeAll(strIter);

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

      try (OutputStreamWriter ow = new OutputStreamWriter(
            new BufferedOutputStream(fdes.getOutputStream()), charSet)) {
         while (sitr.hasNext()) {
            String line = sitr.next();
            //trace("writing:" + line);
            ow.write(line, 0, line.length());
            ow.write(lsep, 0, lsep.length());
         }
         ow.flush();
      }
   }

   public FileProperties(FileDescriptor fd, ClassConverter<OType> convi) {
      fdes = fd;
      conv = convi;
      charSet = Charset.defaultCharset();
      fileString = "";
   }

   // create properties that will have the same format as a prototype
   @SuppressWarnings("unchecked")
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

      // Initialize modification time tracking when file is first read
      updateModifiedTime();

      return fileString;
   }
}
