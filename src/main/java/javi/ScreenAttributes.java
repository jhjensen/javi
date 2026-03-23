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
}
