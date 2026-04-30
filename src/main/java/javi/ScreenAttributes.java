package javi;

import java.util.Arrays;

/**
 * Per-character attribute grid for the VT100 terminal emulator.
 *
 * <p>Stores a packed {@link CellAttr} value for every cell on the
 * visible terminal screen.  The grid is indexed by <em>absolute line
 * number</em> (matching the 1-based line numbers in the backing
 * {@code TextEdit} buffer) and 0-based column index.</p>
 *
 * <h2>Usage</h2>
 * <ol>
 *   <li>{@link #setAttr(int, int, int)} — called by ECScreen when
 *       characters are inserted under the current SGR state.</li>
 *   <li>{@link #getAttr(int, int)} — called by the rendering layer
 *       to retrieve per-character attributes for painting.</li>
 *   <li>{@link #eraseLine(int)} / {@link #eraseScreen(int, int)} —
 *       called when the terminal erases content.</li>
 * </ol>
 *
 * <p>Internally the grid is a sparse {@code HashMap&lt;Integer,
 * int[]&gt;} keyed by line number.  Only lines that have been written
 * to carry an attribute row; {@link #getAttr} returns
 * {@link CellAttr#DEFAULT} for absent lines.</p>
 *
 * @see CellAttr
 */
public final class ScreenAttributes {

   /** Default column capacity for new attribute rows. */
   private static final int DEFAULT_COLS = 256;

   /**
    * Sparse map from line number to column attribute array.
    *
    * <p>Using a plain {@code HashMap} because the number of live
    * lines is small (typically &lt; 100) and lookup is O(1).</p>
    */
   private final java.util.HashMap<Integer, int[]> rows =
      new java.util.HashMap<>();

   /** Current column capacity for new rows. */
   private int cols;

   /**
    * Creates a new attribute grid with the given column width.
    *
    * @param columns initial column capacity
    */
   public ScreenAttributes(int columns) {
      this.cols = columns > 0 ? columns : DEFAULT_COLS;
   }

   /** Creates a new attribute grid with default column width. */
   public ScreenAttributes() {
      this(DEFAULT_COLS);
   }

   // ----- single-cell access -----

   /**
    * Sets the packed attribute for a single cell.
    *
    * @param line 1-based line number
    * @param col  0-based column index
    * @param attr packed {@link CellAttr} value
    */
   public void setAttr(int line, int col, int attr) {
      if (col < 0)
         return;
      int[] row = rows.get(line);
      if (null == row) {
         row = new int[Math.max(cols, col + 1)];
         rows.put(line, row);
      } else if (col >= row.length) {
         row = Arrays.copyOf(row, Math.max(cols, col + 1));
         rows.put(line, row);
      }
      row[col] = attr;
   }

   /**
    * Fills a range of columns on a line with the given attribute.
    *
    * @param line     1-based line number
    * @param colStart 0-based start column (inclusive)
    * @param colEnd   0-based end column (exclusive)
    * @param attr     packed {@link CellAttr} value
    */
   public void fillAttr(int line, int colStart, int colEnd, int attr) {
      if (colStart >= colEnd)
         return;
      int[] row = rows.get(line);
      if (null == row) {
         row = new int[Math.max(cols, colEnd)];
         rows.put(line, row);
      } else if (colEnd > row.length) {
         row = Arrays.copyOf(row, Math.max(cols, colEnd));
         rows.put(line, row);
      }
      Arrays.fill(row, colStart, colEnd, attr);
   }

   /**
    * Returns the packed attribute for a single cell.
    *
    * @param line 1-based line number
    * @param col  0-based column index
    * @return packed {@link CellAttr}, or {@link CellAttr#DEFAULT}
    */
   public int getAttr(int line, int col) {
      int[] row = rows.get(line);
      if (null == row || col < 0 || col >= row.length)
         return CellAttr.DEFAULT;
      return row[col];
   }

   /**
    * Returns the raw attribute row for a line, or {@code null} if
    * no attributes have been recorded for that line.
    *
    * @param line 1-based line number
    * @return attribute array, or null
    */
   public int[] getRow(int line) {
      return rows.get(line);
   }

   // ----- bulk operations -----

   /**
    * Erases (resets to DEFAULT) all attributes on a line.
    *
    * @param line 1-based line number
    */
   public void eraseLine(int line) {
      rows.remove(line);
   }

   /**
    * Erases all attributes for lines in the range
    * {@code [startLine, endLine)}.
    *
    * @param startLine first line to erase (inclusive)
    * @param endLine   last line to erase (exclusive)
    */
   public void eraseScreen(int startLine, int endLine) {
      for (int i = startLine; i < endLine; i++)
         rows.remove(i);
   }

   /**
    * Erases attributes from {@code colStart} to end-of-row.
    *
    * @param line     1-based line number
    * @param colStart 0-based start column
    */
   public void eraseToEnd(int line, int colStart) {
      int[] row = rows.get(line);
      if (null != row && colStart < row.length) {
         Arrays.fill(row, colStart, row.length, CellAttr.DEFAULT);
      }
   }

   /** Removes all stored attributes. */
   public void clear() {
      rows.clear();
   }

   /**
    * Removes attribute rows for lines below the given threshold.
    * Use this periodically to discard scrollback attributes that
    * are no longer visible.
    *
    * @param minLine lines below this number are discarded
    */
   public void evictBefore(int minLine) {
      rows.keySet().removeIf(k -> k < minLine);
   }

   /**
    * Shifts all attribute row keys by the given offset.
    * Rows that would go below zero are discarded.
    * Used when scrollback lines are trimmed from the buffer.
    *
    * @param offset amount to add to each line key (negative to shift down)
    */
   public void shiftAllLines(int offset) {
      if (0 == offset)
         return;
      java.util.HashMap<Integer, int[]> shifted =
         new java.util.HashMap<>();
      for (java.util.Map.Entry<Integer, int[]> e : rows.entrySet()) {
         int newKey = e.getKey() + offset;
         if (newKey >= 0)
            shifted.put(newKey, e.getValue());
      }
      rows.clear();
      rows.putAll(shifted);
   }

   /**
    * Updates the column capacity (e.g. on terminal resize).
    *
    * @param newCols new column count
    */
   public void setColumns(int newCols) {
      if (newCols > 0)
         this.cols = newCols;
   }

   /** Returns the number of lines that have attribute data. */
   public int size() {
      return rows.size();
   }

   /**
    * Returns the current column capacity.
    *
    * @return column count
    */
   public int getColumns() {
      return cols;
   }

   /**
    * Creates a deep copy of this attribute grid.
    *
    * <p>Used by the alternate screen buffer to snapshot the main
    * screen's attributes before switching, so that the alternate
    * screen can write freely without polluting the saved state.</p>
    *
    * @return a new ScreenAttributes with cloned row data
    */
   public ScreenAttributes snapshot() {
      ScreenAttributes copy = new ScreenAttributes(cols);
      for (java.util.Map.Entry<Integer, int[]> entry
            : rows.entrySet()) {
         copy.rows.put(entry.getKey(),
            entry.getValue().clone());
      }
      return copy;
   }

   /**
    * Replaces all data in this grid with a deep copy of the
    * given source. Used to restore the main screen's attributes
    * after exiting the alternate screen buffer.
    *
    * @param source the attribute grid to restore from
    */
   public void restoreFrom(ScreenAttributes source) {
      rows.clear();
      for (java.util.Map.Entry<Integer, int[]> entry
            : source.rows.entrySet()) {
         rows.put(entry.getKey(),
            entry.getValue().clone());
      }
      cols = source.cols;
   }

   /**
    * Shifts attribute rows up within a region, discarding the top
    * rows and clearing the bottom ones.  Used when lines are
    * deleted or the screen scrolls up.
    *
    * @param startLine first line of the region (inclusive)
    * @param endLine   last line of the region (exclusive)
    * @param count     number of lines to shift up
    */
   public void shiftLinesUp(int startLine, int endLine, int count) {
      if (count <= 0 || startLine >= endLine)
         return;
      for (int dst = startLine; dst < endLine; dst++) {
         int src = dst + count;
         if (src < endLine) {
            int[] row = rows.get(src);
            if (null != row)
               rows.put(dst, row);
            else
               rows.remove(dst);
         } else {
            rows.remove(dst);
         }
      }
   }

   /**
    * Shifts attribute rows down within a region, discarding the
    * bottom rows and clearing the top ones.  Used when lines are
    * inserted or the screen scrolls down.
    *
    * @param startLine first line of the region (inclusive)
    * @param endLine   last line of the region (exclusive)
    * @param count     number of lines to shift down
    */
   public void shiftLinesDown(int startLine, int endLine, int count) {
      if (count <= 0 || startLine >= endLine)
         return;
      for (int dst = endLine - 1; dst >= startLine; dst--) {
         int src = dst - count;
         if (src >= startLine) {
            int[] row = rows.get(src);
            if (null != row)
               rows.put(dst, row);
            else
               rows.remove(dst);
         } else {
            rows.remove(dst);
         }
      }
   }

   /**
    * Shifts cell attributes left within a row, filling vacated
    * cells on the right with the given attribute.  Used by DCH
    * (Delete Character) to keep attributes aligned after text
    * shifts left.
    *
    * @param line     1-based line number
    * @param col      0-based column where the shift starts
    * @param count    number of cells to remove/shift
    * @param colLimit total column width (cells beyond this are lost)
    * @param fill     attribute to fill vacated cells at the right
    */
   public void shiftCellsLeft(int line, int col, int count,
         int colLimit, int fill) {
      if (count <= 0 || col < 0)
         return;
      int[] row = rows.get(line);
      if (null == row)
         return;
      int len = Math.min(row.length, colLimit);
      for (int dst = col; dst < len; dst++) {
         int src = dst + count;
         row[dst] = (src < len) ? row[src] : fill;
      }
   }

   /**
    * Shifts cell attributes right within a row, filling vacated
    * cells at the insertion point with the given attribute.  Used
    * by ICH (Insert Character) to keep attributes aligned after
    * text shifts right.
    *
    * @param line     1-based line number
    * @param col      0-based column where the shift starts
    * @param count    number of cells to insert
    * @param colLimit total column width (cells shifted past this are lost)
    * @param fill     attribute to fill inserted cells
    */
   public void shiftCellsRight(int line, int col, int count,
         int colLimit, int fill) {
      if (count <= 0 || col < 0)
         return;
      int[] row = rows.get(line);
      if (null == row) {
         row = new int[Math.max(this.cols, colLimit)];
         rows.put(line, row);
      } else if (row.length < colLimit) {
         row = Arrays.copyOf(row, colLimit);
         rows.put(line, row);
      }
      int len = Math.min(row.length, colLimit);
      // Shift right from end
      for (int dst = len - 1; dst >= col; dst--) {
         int src = dst - count;
         row[dst] = (src >= col) ? row[src] : fill;
      }
   }
}
