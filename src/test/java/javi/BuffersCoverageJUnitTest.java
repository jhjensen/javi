package javi;

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage for {@link Buffers} — fold span tracking,
 * uppercase append with ArrayList, multi-line deleted patterns,
 * CircBuffer wrapping edge cases, and appendCurrBuf.
 */
class BuffersCoverageJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   /** Minimal CircBuffer for testing — setclip is a no-op. */
   private static final class TestCircBuffer extends Buffers.CircBuffer {
      @Override
      public void setclip() {
         // no-op
      }
   }

   private void initBuffers() {
      Buffers.init(new TestCircBuffer());
   }

   // ── Register state model ────────────────────────────────

   @Test
   @DisplayName("RegisterState stores named register and fold span")
   void registerStateStoresNamedAndFoldSpan() {
      Buffers.RegisterState state = new Buffers.RegisterState();
      state.putNamed('a', "alpha");
      state.setFoldSpan(7);

      assertEquals("alpha", state.getNamed('a'));
      assertEquals(1, state.namedCount());
      assertEquals(7, state.getFoldSpan());
   }

   @Test
   @DisplayName("init clears named registers")
   void initClearsNamedRegisters() {
      initBuffers();
      Buffers.deleted('a', "value");
      assertEquals(1, Buffers.namedRegisterCount());

      initBuffers();

      assertEquals(0, Buffers.namedRegisterCount());
      assertNull(Buffers.getbuf('a'));
   }

   // ── Fold span tracking ────────────────────────────────────

   @Test
   @DisplayName("initial fold span is 0")
   void initialFoldSpanZero() {
      Buffers.clearFoldSpan();
      assertEquals(0, Buffers.getLastFoldSpan());
   }

   @Test
   @DisplayName("setLastFoldSpan stores value")
   void setFoldSpanStores() {
      Buffers.setLastFoldSpan(42);
      assertEquals(42, Buffers.getLastFoldSpan());
   }

   @Test
   @DisplayName("clearFoldSpan resets to 0")
   void clearFoldSpanResetsToZero() {
      Buffers.setLastFoldSpan(99);
      Buffers.clearFoldSpan();
      assertEquals(0, Buffers.getLastFoldSpan());
   }

   @Test
   @DisplayName("setLastFoldSpan with negative value")
   void setFoldSpanNegative() {
      Buffers.setLastFoldSpan(-1);
      assertEquals(-1, Buffers.getLastFoldSpan());
   }

   // ── Named buffer store/retrieve with lowercase ────────────

   @Test
   @DisplayName("deleted(lowercase) stores string, getbuf retrieves")
   void lowercaseStoreRetrieve() {
      initBuffers();
      Buffers.deleted('a', "hello");
      assertEquals("hello", Buffers.getbuf('a'));
   }

   @Test
   @DisplayName("deleted(lowercase) overwrites previous value")
   void lowercaseOverwrites() {
      initBuffers();
      Buffers.deleted('b', "first");
      Buffers.deleted('b', "second");
      assertEquals("second", Buffers.getbuf('b'));
   }

   @Test
   @DisplayName("getbuf for unset register returns null")
   void getbufUnsetReturnsNull() {
      initBuffers();
      assertNull(Buffers.getbuf('z'));
   }

   // ── Uppercase append (String to String) ───────────────────

   @Test
   @DisplayName("uppercase append concatenates strings")
   void uppercaseAppendStrings() {
      initBuffers();
      Buffers.deleted('c', "first");
      Buffers.deleted('C', "second");
      assertEquals("firstsecond", Buffers.getbuf('c'));
   }

   @Test
   @DisplayName("uppercase append to empty register creates new")
   void uppercaseAppendToEmpty() {
      initBuffers();
      Buffers.deleted('D', "value");
      assertEquals("value", Buffers.getbuf('d'));
   }

   // ── ArrayList deleted ─────────────────────────────────────

   @Test
   @DisplayName("deleted(char, ArrayList) stores list")
   void deletedArrayListStores() {
      initBuffers();
      ArrayList<String> lines = new ArrayList<>();
      lines.add("line1");
      lines.add("line2");
      Buffers.deleted('e', lines);
      Object result = Buffers.getbuf('e');
      assertNotNull(result);
      assertTrue(result instanceof ArrayList);
   }

   @Test
   @DisplayName("deleted(char, ArrayList) with uppercase appends to list")
   void deletedArrayListUppercaseAppends() {
      initBuffers();
      ArrayList<String> first = new ArrayList<>();
      first.add("a");
      Buffers.deleted('f', first);
      ArrayList<String> second = new ArrayList<>();
      second.add("b");
      Buffers.deleted('F', second);
      Object result = Buffers.getbuf('f');
      assertTrue(result instanceof ArrayList);
      @SuppressWarnings("unchecked")
      ArrayList<String> list = (ArrayList<String>) result;
      assertTrue(list.size() >= 2);
      assertTrue(list.contains("a"));
      assertTrue(list.contains("b"));
   }

   @Test
   @DisplayName("deleted(char, ArrayList) uppercase over string prepends")
   void deletedArrayListUppercaseOverString() {
      initBuffers();
      Buffers.deleted('g', "existing");
      ArrayList<String> newLines = new ArrayList<>();
      newLines.add("added");
      Buffers.deleted('G', newLines);
      Object result = Buffers.getbuf('g');
      assertTrue(result instanceof ArrayList);
      @SuppressWarnings("unchecked")
      ArrayList<String> list = (ArrayList<String>) result;
      assertEquals("existing", list.get(0));
      assertEquals("added", list.get(1));
   }

   @Test
   @DisplayName("deleted with null string is no-op")
   void deletedNullStringNoOp() {
      initBuffers();
      Buffers.deleted('h', "before");
      Buffers.deleted('h', (String) null);
      assertEquals("before", Buffers.getbuf('h'));
   }

   @Test
   @DisplayName("deleted with null ArrayList is no-op")
   void deletedNullArrayListNoOp() {
      initBuffers();
      Buffers.deleted('i', "before");
      Buffers.deleted('i', (ArrayList<String>) null);
      assertEquals("before", Buffers.getbuf('i'));
   }

   // ── CircBuffer (delete buffer via '0') ────────────────────

   @Test
   @DisplayName("deleted('0', string) goes to CircBuffer")
   void deleteBufferStoresString() {
      initBuffers();
      Buffers.deleted('0', "deleted line");
      Object result = Buffers.getbuf('0');
      assertEquals("deleted line", result);
   }

   @Test
   @DisplayName("CircBuffer wraps after 10 entries")
   void circBufferWraps() {
      initBuffers();
      for (int i = 0; i < 12; i++)
         Buffers.deleted('0', "line" + i);
      // getbuf('0') returns most recent, '1' returns previous, etc.
      assertEquals("line11", Buffers.getbuf('0'));
      assertEquals("line10", Buffers.getbuf('1'));
   }

   @Test
   @DisplayName("deleted('0', ArrayList) stores in CircBuffer")
   void deleteBufferStoresArrayList() {
      initBuffers();
      ArrayList<String> lines = new ArrayList<>();
      lines.add("multi1");
      lines.add("multi2");
      Buffers.deleted('0', lines);
      Object result = Buffers.getbuf('0');
      assertTrue(result instanceof ArrayList);
   }

   // ── myToString ────────────────────────────────────────────

   @Test
   @DisplayName("myToString with String returns same string")
   void myToStringString() {
      assertEquals("hello", Buffers.CircBuffer.myToString("hello"));
   }

   @Test
   @DisplayName("myToString with ArrayList joins with newlines")
   void myToStringArrayList() {
      ArrayList<String> lines = new ArrayList<>();
      lines.add("a");
      lines.add("b");
      String result = Buffers.CircBuffer.myToString(lines);
      assertEquals("a\nb\n", result);
   }

   @Test
   @DisplayName("myToString with other object calls toString")
   void myToStringOtherObject() {
      String result = Buffers.CircBuffer.myToString(Integer.valueOf(42));
      assertEquals("42", result);
   }

   @Test
   @DisplayName("myToString with single-element list")
   void myToStringSingleElementList() {
      ArrayList<String> lines = new ArrayList<>();
      lines.add("only");
      String result = Buffers.CircBuffer.myToString(lines);
      assertEquals("only\n", result);
   }

   // ── appendCurrBuf ─────────────────────────────────────────

   @Test
   @DisplayName("appendCurrBuf with string appends to builder")
   void appendCurrBufString() {
      initBuffers();
      Buffers.deleted('0', "data");
      StringBuilder sb = new StringBuilder();
      Buffers.appendCurrBuf(sb, false);
      assertEquals("data", sb.toString());
   }

   @Test
   @DisplayName("appendCurrBuf with ArrayList multiline")
   void appendCurrBufArrayListMultiline() {
      initBuffers();
      ArrayList<String> lines = new ArrayList<>();
      lines.add("line1");
      lines.add("line2");
      Buffers.deleted('0', lines);
      StringBuilder sb = new StringBuilder();
      Buffers.appendCurrBuf(sb, false);
      assertTrue(sb.toString().contains("line1"));
      assertTrue(sb.toString().contains("line2"));
      assertTrue(sb.toString().contains("\n"));
   }

   @Test
   @DisplayName("appendCurrBuf singleline joins with spaces")
   void appendCurrBufSingleline() {
      initBuffers();
      ArrayList<String> lines = new ArrayList<>();
      lines.add("a");
      lines.add("b");
      Buffers.deleted('0', lines);
      StringBuilder sb = new StringBuilder();
      Buffers.appendCurrBuf(sb, true);
      assertTrue(sb.toString().contains("a"));
      assertTrue(sb.toString().contains("b"));
      assertFalse(sb.toString().contains("\n"));
   }

   @Test
   @DisplayName("appendCurrBuf with null buffer is no-op")
   void appendCurrBufNullBuffer() {
      initBuffers();
      // Don't add anything to delete buffer
      // getbuf('0') after init should return null or empty
      StringBuilder sb = new StringBuilder("existing");
      Buffers.appendCurrBuf(sb, false);
      // Should not throw and existing content preserved
      assertTrue(sb.toString().startsWith("existing"));
   }

   // ── getbuf case mapping ───────────────────────────────────

   @Test
   @DisplayName("getbuf('A') returns same as getbuf('a')")
   void getbufUppercaseMapsToLowercase() {
      initBuffers();
      Buffers.deleted('j', "value");
      assertEquals(Buffers.getbuf('j'), Buffers.getbuf('J'));
   }

   // ── CircBuffer.flush ──────────────────────────────────────

   @Test
   @DisplayName("CircBuffer flush clears all entries")
   void circBufferFlush() {
      TestCircBuffer cb = new TestCircBuffer();
      Buffers.init(cb);
      Buffers.deleted('0', "item");
      cb.flush();
      // After flush, get(0) should return null
      assertNull(cb.get(0));
   }

   // ── Unnamed register ("") and linewise tracking ──────────

   @Test
   @DisplayName("recordYank(String) updates unnamed register charwise")
   void recordYankStringUpdatesUnnamed() {
      initBuffers();
      Buffers.recordYank('0', "hello");
      assertEquals("hello", Buffers.getbuf('"'));
      assertFalse(Buffers.isUnnamedLinewise());
   }

   @Test
   @DisplayName("recordYank(ArrayList) updates unnamed register linewise")
   void recordYankArrayListUpdatesUnnamedLinewise() {
      initBuffers();
      ArrayList<String> lines = new ArrayList<>();
      lines.add("a");
      lines.add("b");
      Buffers.recordYank('0', lines);
      Object unnamed = Buffers.getbuf('"');
      assertTrue(unnamed instanceof ArrayList);
      assertTrue(Buffers.isUnnamedLinewise());
   }

   @Test
   @DisplayName("recordDelete(String) populates small-delete register")
   void recordDeleteStringPopulatesSmallDelete() {
      initBuffers();
      Buffers.recordDelete('0', "x");
      assertEquals("x", Buffers.getbuf('-'));
      assertEquals("x", Buffers.getbuf('"'));
      assertFalse(Buffers.isUnnamedLinewise());
   }

   @Test
   @DisplayName("recordDelete(ArrayList) does not touch small-delete")
   void recordDeleteLinewiseLeavesSmallDelete() {
      initBuffers();
      Buffers.recordDelete('0', "tiny");
      ArrayList<String> lines = new ArrayList<>();
      lines.add("big line");
      Buffers.recordDelete('0', lines);
      // small-delete still holds prior charwise delete
      assertEquals("tiny", Buffers.getbuf('-'));
      // unnamed reflects the most recent op (linewise)
      assertTrue(Buffers.isUnnamedLinewise());
   }

   @Test
   @DisplayName("recordYank does not populate small-delete register")
   void recordYankDoesNotPopulateSmallDelete() {
      initBuffers();
      Buffers.recordYank('0', "yanked");
      assertNull(Buffers.getbuf('-'));
   }

   @Test
   @DisplayName("unnamed register reflects most recent op (yank then delete)")
   void unnamedRegisterReflectsMostRecent() {
      initBuffers();
      Buffers.recordYank('0', "first");
      Buffers.recordDelete('0', "second");
      assertEquals("second", Buffers.getbuf('"'));
   }

   @Test
   @DisplayName("recordYank/recordDelete with null inputs are no-ops")
   void recordWithNullInputsAreNoOps() {
      initBuffers();
      Buffers.recordYank('0', "stay");
      Buffers.recordYank('0', (String) null);
      Buffers.recordYank('0', (ArrayList<String>) null);
      Buffers.recordDelete('0', (String) null);
      Buffers.recordDelete('0', (ArrayList<String>) null);
      assertEquals("stay", Buffers.getbuf('"'));
   }

   @Test
   @DisplayName("init clears unnamed and small-delete registers")
   void initClearsUnnamedAndSmallDelete() {
      initBuffers();
      Buffers.recordDelete('0', "scratch");
      assertNotNull(Buffers.getbuf('"'));
      assertNotNull(Buffers.getbuf('-'));

      initBuffers();
      assertNull(Buffers.getbuf('"'));
      assertNull(Buffers.getbuf('-'));
      assertFalse(Buffers.isUnnamedLinewise());
   }

   // ── Numbered delete history ring rotation ─────────────────

   @Test
   @DisplayName("recordDelete rotates numbered registers \"1-\"9")
   void recordDeleteRotatesNumberedRegisters() {
      initBuffers();
      for (int i = 1; i <= 5; i++)
         Buffers.recordDelete('0', "d" + i);
      assertEquals("d5", Buffers.getbuf('0'));
      assertEquals("d4", Buffers.getbuf('1'));
      assertEquals("d3", Buffers.getbuf('2'));
      assertEquals("d2", Buffers.getbuf('3'));
      assertEquals("d1", Buffers.getbuf('4'));
   }

   @Test
   @DisplayName("recordYank also rotates numbered ring (javi semantics)")
   void recordYankRotatesNumberedRing() {
      initBuffers();
      Buffers.recordDelete('0', "del1");
      Buffers.recordYank('0', "yank1");
      // Most recent op (yank) is at slot 0; prior delete at slot 1
      assertEquals("yank1", Buffers.getbuf('0'));
      assertEquals("del1", Buffers.getbuf('1'));
   }

   @Test
   @DisplayName("ring wraps after 10 recordDelete calls")
   void ringWrapsAfterTen() {
      initBuffers();
      for (int i = 0; i < 12; i++)
         Buffers.recordDelete('0', "v" + i);
      // After 12 inserts in a size-10 ring the oldest slot holds the
      // most recent value too (wrap covered slot 0 twice already).
      assertEquals("v11", Buffers.getbuf('0'));
      assertEquals("v10", Buffers.getbuf('1'));
      assertEquals("v9", Buffers.getbuf('2'));
   }

   @Test
   @DisplayName("recordDelete with named register also writes ring")
   void recordDeleteNamedAlsoWritesUnnamed() {
      initBuffers();
      Buffers.recordDelete('a', "named");
      assertEquals("named", Buffers.getbuf('a'));
      assertEquals("named", Buffers.getbuf('"'));
   }

   // ── :registers command entry ──────────────────────────────

   @Test
   @DisplayName(":registers command is registered in command table")
   void registersCommandIsRegistered() {
      Rgroup.CommandEntry entry = Rgroup.getCommandEntry("registers");
      assertNotNull(entry, ":registers should be a registered command");
      assertEquals("registers", entry.name());
      assertEquals("edit", entry.category());
      assertNotNull(entry.description());
   }

   // ── getRegisterSummary output format ──────────────────────

   @Test
   @DisplayName("getRegisterSummary includes header line")
   void registerSummaryHeader() {
      initBuffers();
      String summary = Buffers.getRegisterSummary();
      assertTrue(summary.startsWith("--- Registers ---\n"),
         "summary should start with header");
   }

   @Test
   @DisplayName("getRegisterSummary shows unnamed register")
   void registerSummaryShowsUnnamed() {
      initBuffers();
      Buffers.recordYank('0', "hello");
      String summary = Buffers.getRegisterSummary();
      assertTrue(summary.contains("\"\""), "should contain unnamed reg");
      assertTrue(summary.contains("hello"), "should show yank content");
   }

   @Test
   @DisplayName("getRegisterSummary shows named registers a-z")
   void registerSummaryShowsNamed() {
      initBuffers();
      Buffers.deleted('m', "myvalue");
      String summary = Buffers.getRegisterSummary();
      assertTrue(summary.contains("\"m"), "should show register m");
      assertTrue(summary.contains("myvalue"), "should show m content");
   }

   @Test
   @DisplayName("getRegisterSummary shows small-delete register")
   void registerSummaryShowsSmallDelete() {
      initBuffers();
      Buffers.recordDelete('0', "partial");
      String summary = Buffers.getRegisterSummary();
      assertTrue(summary.contains("\"-"), "should show small-delete reg");
      assertTrue(summary.contains("partial"), "should show delete content");
   }

   @Test
   @DisplayName("getRegisterSummary shows linewise indicator")
   void registerSummaryLinewiseIndicator() {
      initBuffers();
      ArrayList<String> lines = new ArrayList<>();
      lines.add("line1");
      lines.add("line2");
      Buffers.recordYank('0', lines);
      String summary = Buffers.getRegisterSummary();
      // Unnamed register should show 'l' for linewise
      assertTrue(summary.contains("\"\"  l"),
         "unnamed should show 'l' for linewise");
   }

   @Test
   @DisplayName("getRegisterSummary shows charwise indicator")
   void registerSummaryCharwiseIndicator() {
      initBuffers();
      Buffers.recordYank('0', "chartext");
      String summary = Buffers.getRegisterSummary();
      // Unnamed register should show 'c' for charwise
      assertTrue(summary.contains("\"\"  c"),
         "unnamed should show 'c' for charwise");
   }

   @Test
   @DisplayName("getRegisterSummary truncates long values")
   void registerSummaryTruncatesLong() {
      initBuffers();
      String longText = "x".repeat(80);
      Buffers.recordYank('0', longText);
      String summary = Buffers.getRegisterSummary();
      assertTrue(summary.contains("..."), "long values should be truncated");
      // Should not contain the full 80-char text as-is
      assertFalse(summary.contains(longText),
         "full long text should not appear");
   }

   // ── Black-hole register ───────────────────────────────────

   @Test
   @DisplayName("black-hole register discards yank")
   void blackHoleDiscardsYank() {
      initBuffers();
      Buffers.recordYank('_', "discarded");
      assertNull(Buffers.getbuf('_'), "_ always returns null");
      assertNull(Buffers.getbuf('"'),
         "unnamed should not be set by black-hole yank");
   }

   @Test
   @DisplayName("black-hole register discards delete")
   void blackHoleDiscardsDelete() {
      initBuffers();
      Buffers.recordDelete('_', "gone");
      assertNull(Buffers.getbuf('_'), "_ always returns null");
      assertNull(Buffers.getbuf('"'),
         "unnamed should not be set by black-hole delete");
   }

   // ── Clipboard register (* and +) via TestCircBuffer ───────

   /** CircBuffer that stores clipboard text for testing. */
   private static final class ClipCircBuffer extends Buffers.CircBuffer {
      String clipboard = null;

      @Override
      public void setclip() {
         // no-op
      }

      @Override
      public String readClipboard() {
         return clipboard;
      }

      @Override
      public void writeClipboard(String text) {
         clipboard = text;
      }
   }

   @Test
   @DisplayName("recordYank to * writes system clipboard")
   void yankToStarWritesClipboard() {
      ClipCircBuffer clip = new ClipCircBuffer();
      Buffers.init(clip);
      Buffers.recordYank('*', "clipboard text");
      assertEquals("clipboard text", clip.clipboard);
      // Also sets unnamed register
      assertEquals("clipboard text", Buffers.getbuf('"'));
   }

   @Test
   @DisplayName("recordYank to + writes system clipboard")
   void yankToPlusWritesClipboard() {
      ClipCircBuffer clip = new ClipCircBuffer();
      Buffers.init(clip);
      Buffers.recordYank('+', "plus text");
      assertEquals("plus text", clip.clipboard);
   }

   @Test
   @DisplayName("getbuf(*) reads from system clipboard")
   void getbufStarReadsClipboard() {
      ClipCircBuffer clip = new ClipCircBuffer();
      Buffers.init(clip);
      clip.clipboard = "from system";
      assertEquals("from system", Buffers.getbuf('*'));
   }

   @Test
   @DisplayName("getbuf(+) reads from system clipboard")
   void getbufPlusReadsClipboard() {
      ClipCircBuffer clip = new ClipCircBuffer();
      Buffers.init(clip);
      clip.clipboard = "plus system";
      assertEquals("plus system", Buffers.getbuf('+'));
   }

   @Test
   @DisplayName("recordDelete to * writes clipboard and small-delete")
   void deleteToStarWritesClipboardAndSmall() {
      ClipCircBuffer clip = new ClipCircBuffer();
      Buffers.init(clip);
      Buffers.recordDelete('*', "del clip");
      assertEquals("del clip", clip.clipboard);
      assertEquals("del clip", Buffers.getbuf('"'));
   }

   @Test
   @DisplayName("getRegisterSummary shows clipboard register")
   void registerSummaryShowsClipboard() {
      ClipCircBuffer clip = new ClipCircBuffer();
      Buffers.init(clip);
      clip.clipboard = "sys clip content";
      String summary = Buffers.getRegisterSummary();
      assertTrue(summary.contains("\"*"), "should show * register");
      assertTrue(summary.contains("sys clip content"),
         "should show clipboard content");
   }
}
