package javi;

public final class Position {

   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";
   public final int x;
   public final int y;
   /*private*/
   public final FileDescriptor filename;
   public final String comment;

   public Position(int xi, int yi, String filenamei, String commenti) {
      x = xi;
      y = yi;
      filename = FileDescriptor.make(filenamei);
      comment = commenti;
   }

   public Position(int xi, int yi, FileDescriptor filenamei, String commenti) {
      if (null == filenamei)
         throw new NullPointerException("null filename");
      x = xi;
      y = yi;
      filename = filenamei;
      comment = commenti;
   }

   public String toString() {
      return 0 == x
             ? (filename.shortName + "("  + y + ")-" + comment)
             : (filename.shortName + "(" + x + "," + y + ")" + "-" + comment);
   }

   public static final Position badpos = new Position(0, 0, "", null);

   public boolean equals(Object ob) {
      if (null == ob)
         return  false;

      if (ob == this)
         return true;

      if (ob instanceof Position) {
         Position po = (Position) ob;
         return  filename.equals(po.filename) && po.x == x && po.y == y;
      }

      return false;
   }

   public int hashCode() {
      return filename.hashCode() + x * y;
   }

}

