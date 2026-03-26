package javi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks fold ranges for a single buffer. Folds are contiguous
 * ranges of lines that can be collapsed to a single display line.
 *
 * <p>Lines are 1-based (matching javi buffer conventions).
 * Folds are stored sorted by start line and must not partially
 * overlap — a fold is either entirely inside another or
 * entirely outside.</p>
 */
public final class FoldModel {

   private final List<FoldRange> folds = new ArrayList<>();

   /**
    * A single fold range. Start and end are inclusive, 1-based.
    */
   public static final class FoldRange
         implements Comparable<FoldRange> {
      public final int startLine;
      public final int endLine;
      public boolean collapsed;

      FoldRange(int start, int end) {
         this.startLine = start;
         this.endLine = end;
         this.collapsed = false;
      }

      /** Lines hidden when collapsed. */
      int hiddenLines() {
         return collapsed ? (endLine - startLine) : 0;
      }

      /** Total lines spanned (inclusive). */
      int span() {
         return endLine - startLine + 1;
      }

      public int compareTo(FoldRange other) {
         return Integer.compare(startLine, other.startLine);
      }

      public String toString() {
         return "Fold[" + startLine + "-" + endLine
            + (collapsed ? ",closed" : ",open") + "]";
      }
   }

   /** Add a fold. Must span at least 2 lines. */
   public void addFold(int start, int end) {
      if (end <= start)
         return;
      FoldRange fr = new FoldRange(start, end);
      int idx = Collections.binarySearch(folds, fr);
      if (idx < 0)
         idx = -(idx + 1);
      folds.add(idx, fr);
   }

   /** Remove the fold whose startLine matches the given line. */
   public void removeFold(int line) {
      folds.removeIf(f -> f.startLine == line);
   }

   /**
    * Toggle the fold containing the given line.
    * Returns the affected fold, or null if none found.
    */
   public FoldRange toggleFold(int line) {
      FoldRange f = findFold(line);
      if (f != null)
         f.collapsed = !f.collapsed;
      return f;
   }

   /**
    * Open the fold containing the given line.
    * Returns the affected fold, or null if none found.
    */
   public FoldRange openFold(int line) {
      FoldRange f = findFold(line);
      if (f != null)
         f.collapsed = false;
      return f;
   }

   /**
    * Close the fold containing the given line.
    * Returns the affected fold, or null if none found.
    */
   public FoldRange closeFold(int line) {
      FoldRange f = findFold(line);
      if (f != null)
         f.collapsed = true;
      return f;
   }

   /** Open all folds. */
   public void openAll() {
      for (FoldRange f : folds)
         f.collapsed = false;
   }

   /** Close all folds. */
   public void closeAll() {
      for (FoldRange f : folds)
         f.collapsed = true;
   }

   /** True if the given line is hidden inside a collapsed fold. */
   public boolean isFolded(int line) {
      for (FoldRange f : folds) {
         if (f.collapsed && line > f.startLine
               && line <= f.endLine)
            return true;
      }
      return false;
   }

   /**
    * Find the fold containing the given line (either at the
    * start line or inside the range). Returns null if none.
    */
   public FoldRange findFold(int line) {
      for (FoldRange f : folds) {
         if (line >= f.startLine && line <= f.endLine)
            return f;
      }
      return null;
   }

   /**
    * Find the fold whose start line exactly matches.
    * Returns null if none.
    */
   public FoldRange findFoldAtStart(int line) {
      for (FoldRange f : folds) {
         if (f.startLine == line)
            return f;
      }
      return null;
   }

   /** Number of visible lines given the current fold state. */
   public int getVisibleLineCount(int totalLines) {
      int hidden = 0;
      for (FoldRange f : folds)
         hidden += f.hiddenLines();
      return totalLines - hidden;
   }

   /**
    * Map a screen (visible) line to the actual buffer line.
    * Screen lines are 1-based. Collapsed folds count as
    * one visible line (the start line).
    */
   public int mapScreenToBuffer(int screenLine) {
      int visible = 0;
      int bufLine = 0;
      while (visible < screenLine) {
         bufLine++;
         visible++;
         if (visible == screenLine)
            return bufLine;
         FoldRange f = findFoldAtStart(bufLine);
         if (f != null && f.collapsed)
            bufLine = f.endLine;
      }
      return bufLine;
   }

   /**
    * Map a buffer line to the visible screen line.
    * Returns -1 if the line is hidden inside a collapsed fold.
    */
   public int mapBufferToScreen(int bufferLine) {
      if (isFolded(bufferLine))
         return -1;
      int visible = 0;
      for (int bl = 1; bl <= bufferLine; bl++) {
         if (isFolded(bl))
            continue;
         visible++;
      }
      return visible;
   }

   /**
    * Return the next visible buffer line after bufLine.
    * If bufLine is the start of a collapsed fold, skips to
    * endLine + 1. If the next line is inside a collapsed
    * fold, skips past it.
    */
   public int nextVisible(int bufLine) {
      FoldRange f = findFoldAtStart(bufLine);
      if (f != null && f.collapsed)
         return f.endLine + 1;
      int next = bufLine + 1;
      FoldRange enc = findFold(next);
      if (enc != null && enc.collapsed && next > enc.startLine)
         return enc.endLine + 1;
      return next;
   }

   /**
    * Return the previous visible buffer line before bufLine.
    * If the previous line is inside a collapsed fold, jumps
    * to the fold's start line.
    *
    * @return previous visible line, or 0 if before start
    */
   public int prevVisible(int bufLine) {
      int prev = bufLine - 1;
      if (prev < 1)
         return 0;
      FoldRange f = findFold(prev);
      if (f != null && f.collapsed && prev > f.startLine)
         return f.startLine;
      return prev;
   }

   /**
    * Build fold summary text for display when a fold is
    * collapsed. Format: "+--  N lines: first-line-text".
    */
   public static String foldSummaryText(
         int startLine, int endLine, String firstLineText) {
      int count = endLine - startLine;
      String trimmed = firstLineText.trim();
      return "+--  " + count + " lines: " + trimmed;
   }

   /** Returns unmodifiable view of all folds. */
   public List<FoldRange> getFolds() {
      return Collections.unmodifiableList(folds);
   }

   /** Number of folds. */
   public int size() {
      return folds.size();
   }

   /** True if no folds are defined. */
   public boolean isEmpty() {
      return folds.isEmpty();
   }

   /** Clear all folds. */
   public void clear() {
      folds.clear();
   }

   /** Summary string for status bar display. */
   public String statusSummary() {
      if (folds.isEmpty())
         return "no folds";
      long closed = folds.stream()
         .filter(f -> f.collapsed).count();
      return folds.size() + " folds (" + closed + " closed)";
   }
}
