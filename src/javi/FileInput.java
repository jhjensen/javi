package javi;

import java.io.IOException;
import org.mozilla.universalchardet.UniversalDetector;

final class FileInput extends BufInIoc<String> {
   /* Copyright 1996 James Jensen all rights reserved */
   private static final long serialVersionUID = 1;

//private FileDescriptor file;

   public String fromString(String s) {
      return s;
   }

   String newExternal(history.ByteInput dis) {
      return dis.readUTF();
   }

   protected void truncIo() {
      npos = Integer.MAX_VALUE;
      rpos = Integer.MAX_VALUE;
      fileend = filestart;
      filestring = null;
      super.truncIo();
   }

   private void readObject(java.io.ObjectInputStream is) throws
         ClassNotFoundException, java.io.IOException {
      is.defaultReadObject();
      truncIo();
   }

   private enum FileMode { UNIX, MS , MIXED };

   public String getnext() {
      //trace("fileread getnext");
      //trace("fileread getnext returning " + retval);
      String retval = getLine();
      if (null != retval)
         return retval;

      String nextstring;
      if (rpos == Integer.MAX_VALUE && npos == Integer.MAX_VALUE) {
         if (fileend <= filestart) {
            filestring = null;
            //trace("fileread getnext returning " + null);
            return null;
         } else {
            //trace("len = " + filestring.length() +  " filestart = " + filestart + " fileend = " + fileend);
            nextstring =  filestring.substring(filestart, fileend);
            filestart = fileend;
            //trace("fileread getnext returning " + nextstring);
            return nextstring;
         }
      } else {
         switch (inputmode) {
            case UNIX: // we know there is no \r in the input
               nextstring = filestring.substring(filestart, npos);
               filestart = npos + 1;
               npos = filestring.indexOf('\n', filestart);
               if (npos == -1) npos = Integer.MAX_VALUE;
               //trace("fileread getnext returning " + nextstring);
               return nextstring;

            case MIXED:  // treat these the same for now.
            case MS:
            default:
               if (rpos > npos) {
                  nextstring = filestring.substring(filestart, npos);
                  filestart = npos + 1;
                  if (filestart == rpos) {
                     filestart++;
                     rpos = filestring.indexOf('\r', filestart);
                     if (rpos == -1) rpos = Integer.MAX_VALUE;
                  }
                  npos = filestring.indexOf('\n', filestart);
                  if (npos == -1) npos = Integer.MAX_VALUE;
               } else {
                  nextstring = filestring.substring(filestart, rpos);
                  filestart = rpos + 1;
                  if (filestart == npos) {
                     filestart++;
                     npos = filestring.indexOf('\n', filestart);
                     if (npos == -1) npos = Integer.MAX_VALUE;
                  }
                  rpos = filestring.indexOf('\r', filestart);
                  if (rpos == -1) rpos = Integer.MAX_VALUE;
               }
               //trace("fileread getnext returning " + nextstring);
               return nextstring;
         }
      }
   }

   FileInput(FileProperties fp) {
      super(fp, false, null);
      //trace("new FileInput prop = " + fp);
      if (null == fp)
         throw new RuntimeException("file input with no File");

      //???FileDescriptor.LocalFile.make(fdes.shortName+".dmp2");
   }


   private transient String filestring;
   private transient int fileend;
   private transient int filestart;
   private transient int npos;
   private transient int rpos;
   private transient FileMode inputmode;

   protected void preRun() throws IOException {
      if (prop.fdes instanceof FileDescriptor.LocalFile) {
         byte [] filebyte = ((FileDescriptor.LocalFile) prop.fdes).readFile();
         try {
            UniversalDetector detector = new UniversalDetector(null);
            detector.handleData(filebyte, 0, filebyte.length);
            detector.dataEnd();
            String encoding = detector.getDetectedCharset();
            if (null == encoding) {
               filestring  = new String(filebyte);
            } else {
               //trace("Detected encoding = " + encoding);
               filestring  = new String(filebyte, encoding);
            }

            // (5)
            detector.reset();
         } catch (java.io.UnsupportedEncodingException e) {
            filestring = new String(filebyte);
         }
      }
      fileend = filestring.length();
      filestart = 0;

      //trace("filestring:" + filestring);
      npos = filestring.indexOf('\n');
      rpos = filestring.indexOf('\r');

      inputmode = rpos == -1
                  ? FileMode.UNIX
                  : rpos + 1 == npos
                  ? FileMode.MS
                  : FileMode.MIXED;

      prop.setSeperator(inputmode == FileMode.UNIX
         ? "\n"
         : inputmode == FileMode.MS
            ? "\r\n"
            : System.getProperty("line.separator"));

      if (npos == -1) npos = Integer.MAX_VALUE;
      if (rpos == -1) rpos = Integer.MAX_VALUE;
   }
}
