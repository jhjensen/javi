package javi;

public final class Position {

   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";
   final int x;
   final int y;
   /*private*/
   final FileDescriptor filename;
   final String comment;

   public Position(int xi, int yi, String filenamei, String commenti) {
      x = xi;
      y = yi;
      filename = FileDescriptor.make(filenamei);
      comment = commenti;
   }
   public Position(int xi, int yi, FileDescriptor filenamei, String commenti) {
      if (filenamei == null)
         throw new NullPointerException();
      x = xi;
      y = yi;
      filename = filenamei;
      comment = commenti;
   }
   public String toString() {
      return x == 0
             ? (filename.shortName + "("  + y + ")-" + comment)
             : (filename.shortName + "(" + x + "," + y + ")" + "-" + comment);
   }

   Position(MovePos p, String fname, String commi) {
      x = p.x;
      y = p.y;
      filename = FileDescriptor.make(fname);
      comment = commi;
   }

   public static final Position badpos = new Position(0, 0, "", null);

   boolean equals(Position p) {
      return p == null
             ? false
             : filename.equals(p.filename) && p.x == x && p.y == y;
   }

   public boolean equals(Object p) {
      return p instanceof Position
             ? equals((Position) p)
             : false;
   }

   public int hashCode() {
      return filename.hashCode() + x * y;
   }
   static void trace(String str) {
      Tools.trace(str, 1);
   }
}

