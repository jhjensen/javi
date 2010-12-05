package javi;

public final class MovePos {

   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";
   public int x = 0;
   public int y = 0;

   public MovePos(int xi, int yi) {
      x = xi;
      y = yi;
   }

   public MovePos(MovePos xo) {
      x = xo.x;
      y = xo.y;
   }

   public MovePos(Position p) {
      set(p);
   }

   public String toString() {
      return 0 == x
             ?  "("  + y + ")"
             : ("(" + x + "," + y + ")");
//    ? (filename.getShortName() + "("  + y + ")-" + comment)
//    : (filename.getShortName() + "(" + x + "," + y + ")" + "-" + comment);
   }

   public void set(Position p) {
      x = p.x;
      y = p.y;
      //filename = p.filename;
      //comment = p.comment;
   }

   public boolean equals(Object ob) {
      if (this == ob)
         return true;
      if (null == ob)
         return false;
      if (ob instanceof MovePos) {
         MovePos po = (MovePos) ob;
                 //      : filename.equals(po.filename) && po.x == x && po.y == y;
         return po.x == x && po.y == y;
      }
      return false;
   }

   public int hashCode() {
      //return filename.hashCode()+x*y;
//   return filename.hashCode()+
      return (x & 0xff) + ((y >> 8) & 0xff);
   }
}
