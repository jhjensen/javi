package javi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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
    * Callback invoked before a fold toggle or open operation.
    * Returns true if the handler consumed the action (caller
    * should skip the default toggle). The handler receives the
    * buffer line number and the current FvContext.
    */
   @FunctionalInterface
   public interface FoldToggleHandler {
      boolean onToggle(int line, FvContext<?> fvc)
         throws IOException, InputException;
   }

   private FoldToggleHandler toggleHandler;

   /** Set a handler to intercept fold toggle/open actions. */
   public void setToggleHandler(FoldToggleHandler h) {
      this.toggleHandler = h;
   }

   /** Get the current fold toggle handler, or null. */
   public FoldToggleHandler getToggleHandler() {
      return toggleHandler;
   }

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
    * Find the innermost fold containing the given line
    * (either at the start line or inside the range).
    * Returns null if none.
    */
   public FoldRange findFold(int line) {
      FoldRange best = null;
      for (FoldRange f : folds) {
         if (line >= f.startLine && line <= f.endLine) {
            if (best == null || f.span() < best.span())
               best = f;
         }
      }
      return best;
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
    * fold, skips past it. Handles nested collapsed folds
    * by looping until the result is truly visible.
    */
   public int nextVisible(int bufLine) {
      int result = bufLine;
      FoldRange f = findFoldAtStart(result);
      if (f != null && f.collapsed)
         result = f.endLine + 1;
      else
         result = result + 1;
      // Skip past any enclosing collapsed folds
      boolean changed = true;
      while (changed) {
         changed = false;
         for (FoldRange fr : folds) {
            if (fr.collapsed && result > fr.startLine
                  && result <= fr.endLine) {
               result = fr.endLine + 1;
               changed = true;
            }
         }
      }
      return result;
   }

   /**
    * Return the previous visible buffer line before bufLine.
    * If the previous line is inside a collapsed fold, jumps
    * to the fold's start line. Handles nested collapsed
    * folds by looping until the result is truly visible.
    *
    * @return previous visible line, or 0 if before start
    */
   public int prevVisible(int bufLine) {
      int prev = bufLine - 1;
      if (prev < 1)
         return 0;
      // Skip past all enclosing collapsed folds
      boolean changed = true;
      while (changed) {
         changed = false;
         for (FoldRange f : folds) {
            if (f.collapsed && prev > f.startLine
                  && prev <= f.endLine) {
               prev = f.startLine;
               changed = true;
            }
         }
      }
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

   /**
    * Adjust all fold ranges after lines are inserted or
    * deleted. Called from the FileChangeListener mechanism.
    *
    * @param index 0-based position from EditContainer
    * @param count positive for insert, negative for delete
    */
   public void adjustForEdit(int index, int count) {
      if (folds.isEmpty() || count == 0)
         return;
      List<FoldRange> newFolds = new ArrayList<>();
      for (FoldRange f : folds) {
         int ns = adjustLine(f.startLine, index, count);
         int ne = adjustLine(f.endLine, index, count);
         if (ne > ns) {
            FoldRange nf = new FoldRange(ns, ne);
            nf.collapsed = f.collapsed;
            newFolds.add(nf);
         }
      }
      folds.clear();
      folds.addAll(newFolds);
   }

   private static int adjustLine(
         int line, int index, int count) {
      if (count > 0)
         return line > index ? line + count : line;
      int delStart = index + 1;
      int delEnd = index - count;
      if (line < delStart)
         return line;
      if (line > delEnd)
         return line + count;
      return delStart;
   }

   /**
    * Return the fold gutter indicator character for the given
    * buffer line, or '\0' if no indicator should be shown.
    *
    * <ul>
    * <li>{@code '+'} — start of a collapsed fold</li>
    * <li>{@code '-'} — start of an open fold</li>
    * <li>{@code '|'} — inside the body of an open fold</li>
    * </ul>
    */
   public char getFoldIndicator(int bufLine) {
      // Check startLine matches first (fold headers) —
      // an inner fold start takes priority over being
      // inside an outer fold.
      for (FoldRange f : folds) {
         if (bufLine == f.startLine)
            return f.collapsed ? '+' : '-';
      }
      for (FoldRange f : folds) {
         if (!f.collapsed
               && bufLine > f.startLine
               && bufLine <= f.endLine)
            return '|';
      }
      return '\0';
   }

   /** Summary string for status bar display. */
   public String statusSummary() {
      if (folds.isEmpty())
         return "no folds";
      long closed = folds.stream()
         .filter(f -> f.collapsed).count();
      return folds.size() + " folds (" + closed + " closed)";
   }

   /**
    * Save fold state to a .foldstate file alongside the
    * source file. Format: one fold per line as
    * {@code startLine:endLine:collapsed}.
    *
    * @param canonPath canonical path of the source file
    */
   public void saveFolds(String canonPath) {
      if (canonPath == null || folds.isEmpty())
         return;
      File foldFile = foldStateFile(canonPath);
      try (BufferedWriter bw =
            new BufferedWriter(new FileWriter(foldFile))) {
         for (FoldRange f : folds) {
            bw.write(f.startLine + ":"
               + f.endLine + ":" + f.collapsed);
            bw.newLine();
         }
      } catch (IOException e) {
         // best-effort — fold persistence is non-critical
      }
   }

   /**
    * Load fold state from a .foldstate file. Returns null
    * if no fold state file exists or it cannot be read.
    *
    * @param canonPath canonical path of the source file
    * @return loaded FoldModel, or null
    */
   public static FoldModel loadFolds(String canonPath) {
      if (canonPath == null)
         return null;
      File foldFile = foldStateFile(canonPath);
      if (!foldFile.exists() || !foldFile.canRead())
         return null;
      FoldModel model = new FoldModel();
      try (BufferedReader br =
            new BufferedReader(new FileReader(foldFile))) {
         String line;
         while ((line = br.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty())
               continue;
            String[] parts = trimmed.split(":");
            if (parts.length != 3)
               continue;
            try {
               int start = Integer.parseInt(parts[0]);
               int end = Integer.parseInt(parts[1]);
               boolean collapsed =
                  Boolean.parseBoolean(parts[2]);
               if (end > start) {
                  model.addFold(start, end);
                  if (collapsed) {
                     FoldRange fr =
                        model.findFoldAtStart(start);
                     if (fr != null)
                        fr.collapsed = true;
                  }
               }
            } catch (NumberFormatException e) {
               continue; // skip malformed line
            }
         }
      } catch (IOException e) {
         return null;
      }
      return model.isEmpty() ? null : model;
   }

   /**
    * Delete the fold state file for the given source file.
    *
    * @param canonPath canonical path of the source file
    */
   public static void deleteFoldState(String canonPath) {
      if (canonPath == null)
         return;
      File foldFile = foldStateFile(canonPath);
      if (foldFile.exists())
         foldFile.delete();
   }

   /**
    * Derive the fold state file path from a source file's
    * canonical path.
    */
   static File foldStateFile(String canonPath) {
      return new File(canonPath + ".foldstate");
   }
}
