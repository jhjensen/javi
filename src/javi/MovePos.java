package javi;

final class MovePos {

   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";
   int x = 0;
   int y = 0;

   MovePos(int xi, int yi) {
      x = xi;
      y = yi;
   }

   MovePos(MovePos xo) {
      x = xo.x;
      y = xo.y;
   }

   public String toString() {
      return x == 0
             ?  "("  + y + ")"
             : ("(" + x + "," + y + ")");
//    ? (filename.getShortName() + "("  + y + ")-" + comment)
//    : (filename.getShortName() + "(" + x + "," + y + ")" + "-" + comment);
   }

   MovePos(Position p) {
      set(p);
   }

   void set(Position p) {
      x = p.x;
      y = p.y;
      //filename = p.filename;
      //comment = p.comment;
   }

   boolean equals(MovePos p) {
      return p == null
             ? false
//      : filename.equals(p.filename) && p.x == x && p.y == y;
             :  p.x == x && p.y == y;
   }

   public boolean equals(Object p) {
      return p instanceof MovePos
             ? equals((MovePos) p)
             : false;
   }

   public int hashCode() {
      //return filename.hashCode()+x*y;
//   return filename.hashCode()+
      return (x & 0xff) + ((y >> 8) & 0xff);
   }
   static void trace(String str) {
      Tools.trace(str, 1);
   }
}
