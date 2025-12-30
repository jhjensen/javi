package javi;

public final class MovePos {

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

   public String toString() {
      return 0 == x
             ?  "("  + y + ")"
             : ("(" + x + "," + y + ")");
//    ? (filename.getShortName() + "("  + y + ")-" + comment)
//    : (filename.getShortName() + "(" + x + "," + y + ")" + "-" + comment);
   }

   public void set(int px, int py) {
      x = px;
      y = py;
      //filename = p.filename;
      //comment = p.comment;
   }

}
