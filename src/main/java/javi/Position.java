package javi;

/**
 * Immutable position in a file: x (column), y (line), and file reference.
 *
 * <p>Position represents a cursor location or marker in the editor:
 * <ul>
 *   <li>{@link #x} - Column position (0-based)</li>
 *   <li>{@link #y} - Line number (1-based)</li>
 *   <li>{@link #filename} - FileDescriptor identifying the file</li>
 *   <li>{@link #comment} - Optional description (e.g., "search result")</li>
 * </ul>
 *
 * <h2>Immutability</h2>
 * <p>Position is immutable. For mutable positions, see {@link MovePos}.
 * Use {@link #getMovable()} to get a mutable copy.</p>
 *
 * <h2>Special Values</h2>
 * <ul>
 *   <li>{@link #badpos} - Sentinel for invalid/unknown position</li>
 * </ul>
 *
 * <h2>Equality</h2>
 * <p>Two positions are equal if they have same file, x, and y.
 * The comment is NOT part of equality.</p>
 *
 * <h2>Modernization Note</h2>
 * <p>This class is a candidate for conversion to a Java record.
 * See IMPROVEMENTS.md M7.</p>
 *
 * @see MovePos
 * @see FvContext
 */
public final class Position {

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

   MovePos getMovable() {
      return new MovePos(x, y);
   }

   void posMove(MovePos mpos)  {
      mpos.set(x, y);
   }

   public boolean equiv(MovePos po) {
      if (null == po)
         return  false;

      return
         x == po.x
         && y == po.y;

   }
}

