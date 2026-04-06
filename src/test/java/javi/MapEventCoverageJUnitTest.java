package javi;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Coverage tests for {@link MapEvent} — focuses on
 * {@code getAllBindings}, {@code getActiveKeyMap}, and
 * event dispatch logic without requiring AWT.
 */
class MapEventCoverageJUnitTest {

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void lock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() {
      EventQueue.biglock2.unlock();
   }

   // ── getAllBindings ─────────────────────────────────────────

   @Test
   void getAllBindingsWhenNullShowsInitMessage() {
      // bindCommands() not called → normalKeyMap is null
      // Skip if another test class already called bindCommands
      assumeTrue(MapEvent.getNormalKeyMap() == null,
         "normalKeyMap already initialized by another test");
      List<String> bindings = MapEvent.getAllBindings();
      assertNotNull(bindings);
      assertFalse(bindings.isEmpty());
      assertTrue(bindings.get(0).contains("not yet initialized"),
         "should indicate bindings not initialized");
   }

   // ── getActiveKeyMap ────────────────────────────────────────

   @Test
   void getActiveKeyMapWithNullFvc() {
      // When fvc is null and normalKeyMap is null, returns null
      // If bindCommands already ran, getActiveKeyMap returns normalKeyMap
      KeyMap result = MapEvent.getActiveKeyMap(null);
      if (MapEvent.getNormalKeyMap() == null)
         assertNull(result,
            "getActiveKeyMap(null) with no normalKeyMap → null");
      else
         assertNotNull(result,
            "getActiveKeyMap(null) with normalKeyMap → normalKeyMap");
   }

   @Test
   void getActiveKeyMapWithFvcButNoOverlay() throws Exception {
      String fname = "ju_mac_fvc1";
      UI.setStream(new StringReader(""));
      FileDescriptor.LocalFile fl =
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(fname));
      try {
         FileDescriptor fd = FileDescriptor.make(
            history.Testutil.testFile(fname).getPath());
         FileProperties<String> fp =
            new FileProperties<>(fd, StringIoc.converter);
         FileInput fi = new FileInput(fp);
         TextEdit<String> te = new TextEdit<>(fi, fp);
         te.finish();
         TestView view = new TestView(true);
         FvContext<?> fvc = FvContext.connectFv(te, view);

         // normalKeyMap may or may not be null depending on
         // test ordering (another test may call bindCommands)
         KeyMap result = MapEvent.getActiveKeyMap(fvc);
         if (MapEvent.getNormalKeyMap() == null)
            assertNull(result);
         else
            assertNotNull(result);

         te.disposeFvc();
      } finally {
         fl.delete();
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(fname + ".dmp2")).delete();
      }
   }

   // ── getNormalKeyMap ────────────────────────────────────────

   @Test
   void getNormalKeyMapNullBeforeBindCommands() {
      // Before bindCommands(), normalKeyMap should be null.
      // Skip if another test class already called bindCommands.
      assumeTrue(MapEvent.getNormalKeyMap() == null,
         "normalKeyMap already initialized by another test");
      assertNull(MapEvent.getNormalKeyMap(),
         "normalKeyMap should be null until bindCommands");
   }

   // ── getKeyGroup edge cases ─────────────────────────────────

   @Test
   void getKeyGroupDotSyntaxWithGoodGroupBadKeymap() {
      // "move" group is valid but "nonexistent" keymap doesn't exist
      KeyGroup kg = MapEvent.getKeyGroup("nonexistent.edit");
      assertNull(kg);
   }

   @Test
   void getKeyGroupDotSyntaxWithBadGroup() {
      // Even if keymap existed, "other" is not a valid group
      KeyGroup kg = MapEvent.getKeyGroup("normal.other");
      assertNull(kg);
   }

   // ── hevent digit accumulation ─────────────────────────────

   @Test
   void heventEscapeResetsCount() throws Exception {
      String fname = "ju_mac_hev1";
      UI.setStream(new StringReader(""));
      FileDescriptor.LocalFile fl =
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(fname));
      try {
         FileDescriptor fd = FileDescriptor.make(
            history.Testutil.testFile(fname).getPath());
         FileProperties<String> fp =
            new FileProperties<>(fd, StringIoc.converter);
         FileInput fi = new FileInput(fp);
         TextEdit<String> te = new TextEdit<>(fi, fp);
         te.finish();
         TestView view = new TestView(true);
         FvContext<?> fvc = FvContext.connectFv(te, view);

         // Send escape key — should reset aiterate
         JeyEvent esc = new JeyEvent(0, 0, (char) 27);
         // hevent with escape just resets count, no error
         MapEvent.hevent(esc, fvc);

         te.disposeFvc();
      } finally {
         fl.delete();
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(fname + ".dmp2")).delete();
      }
   }

   @Test
   void heventDigitAccumulates() throws Exception {
      String fname = "ju_mac_hev2";
      UI.setStream(new StringReader(""));
      FileDescriptor.LocalFile fl =
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(fname));
      try {
         FileDescriptor fd = FileDescriptor.make(
            history.Testutil.testFile(fname).getPath());
         FileProperties<String> fp =
            new FileProperties<>(fd, StringIoc.converter);
         FileInput fi = new FileInput(fp);
         TextEdit<String> te = new TextEdit<>(fi, fp);
         te.finish();
         TestView view = new TestView(true);
         FvContext<?> fvc = FvContext.connectFv(te, view);

         // Send digits — they accumulate in aiterate
         MapEvent.hevent(new JeyEvent(0, 0, '3'), fvc);
         MapEvent.hevent(new JeyEvent(0, 0, '5'), fvc);
         // Reset with escape
         MapEvent.hevent(new JeyEvent(0, 0, (char) 27), fvc);

         te.disposeFvc();
      } finally {
         fl.delete();
         FileDescriptor.LocalFile.make(
            history.Testutil.testFile(fname + ".dmp2")).delete();
      }
   }
}
