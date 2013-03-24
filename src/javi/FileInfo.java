package javi;

import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import org.mozilla.universalchardet.UniversalDetector;
import java.nio.charset.Charset;

import history.Tools;

   
public final class FileInfo<OType> implements Serializable {

   private enum FileMode { UNIX, MS , MIXED };
   static final String staticLine = System.getProperty("line.separator");
   private static final long serialVersionUID = 1;
   public final FileDescriptor fdes;
   public final ClassConverter<OType> conv;
   private transient Charset charSet;
   private transient String fileString;

   private String lsep = staticLine; //??? final

   public String toString() {
      return fdes.toString();
   }

   String getSeperator() {
      return lsep;
   }

   void setSeperator(String sep) {
      //e! resets this if (lsep != staticLine)
      //   throw new RuntimeException("attempt to reset line seperator");
      lsep = sep;
   }
   
   void writeAll(Iterator<String> sitr)  throws IOException {

      OutputStreamWriter ow =
         new OutputStreamWriter(new BufferedOutputStream(fdes.getOutputStream()));

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

   public FileInfo(FileDescriptor fd, ClassConverter<OType> convi) throws IOException {
      fdes = fd;
      conv = convi;
      charSet = Charset.defaultCharset();
      fileString = "";
   }

   public String initFile() throws IOException {
      if (fdes instanceof FileDescriptor.LocalFile) {
         byte [] filebyte = ((FileDescriptor.LocalFile) fdes).readFile();
         UniversalDetector detector = new UniversalDetector(null);
         detector.handleData(filebyte, 0, filebyte.length);
         detector.dataEnd();
         String encoding = detector.getDetectedCharset();
         detector.reset();

         charSet = encoding == null
            ? Charset.defaultCharset()
            : Charset.forName(encoding);
            fileString  = new String(filebyte,charSet);

      }
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
