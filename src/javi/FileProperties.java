package javi;

import java.io.Serializable;

public final class FileProperties<OType> implements Serializable {
   private static String staticLine = System.getProperty("line.separator");
   final FileDescriptor fdes;
   final ClassConverter<OType> conv;
   private String lsep = staticLine; //??? final

   public FileProperties(FileDescriptor fd, ClassConverter<OType> convi) {
      fdes = fd;
      conv = convi;
   }

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
}
