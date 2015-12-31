package javi;

import java.io.IOException;
import static history.Tools.trace;

final class FileInput extends BufInIoc<String> {
   private static final long serialVersionUID = 1;
   private transient FileMode inputmode;

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

   private enum FileMode { UNIX, MS, MIXED };

   public String getnext() {
      //trace("fileread getnext");
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

   protected void preRun() throws IOException {
      trace("about to init file");
      filestring = prop.initFile();
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

      if (npos == -1) npos = Integer.MAX_VALUE;
      if (rpos == -1) rpos = Integer.MAX_VALUE;
   }
}
