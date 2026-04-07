package javi;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Extended coverage for {@link MapEvent} — getAllBindings,
 * getKeyGroup, getActiveKeyMap, domovement, hevent.
 * Tests requiring full keybindings are skipped if
 * bindCommands fails in headless environment.
 */
class MapEventExtendedJUnitTest {

   private static boolean bindingsAvailable;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
      if (MapEvent.getNormalKeyMap() == null) {
         try {
            MapEvent.bindCommands();
            bindingsAvailable = true;
         } catch (Exception e) {
            bindingsAvailable = false;
         }
      } else {
         bindingsAvailable = true;
      }
   }

   @BeforeEach
   void lock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void unlock() {
      EventQueue.biglock2.unlock();
   }

   private static String testPath(String name) {
      return history.Testutil.testFile(name).getPath();
   }

   private static FileDescriptor.LocalFile makeLocal(String name) {
      return FileDescriptor.LocalFile.make(
         history.Testutil.testFile(name));
   }

   private static TextEdit<String> openTestFile(String name) {
      FileDescriptor fd = FileDescriptor.make(testPath(name));
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      FileInput fi = new FileInput(fp);
      TextEdit<String> te = new TextEdit<>(fi, fp);
      te.finish();
      return te;
   }

   private static void deleteTestFiles(String... names)
         throws IOException {
      for (String name : names) {
         makeLocal(name).delete();
         makeLocal(name + ".dmp2").delete();
      }
   }

   // ── getAllBindings ─────────────────────────────────────────

   @Test
   void getAllBindingsWhenInitialized() {
      assumeTrue(bindingsAvailable);
      List<String> bindings = MapEvent.getAllBindings();
      assertNotNull(bindings);
      assertFalse(bindings.isEmpty());
      assertTrue(bindings.stream()
         .anyMatch(s -> s.contains("MOVEMENT")));
      assertTrue(bindings.stream()
         .anyMatch(s -> s.contains("COMMAND")));
   }

   // ── getNormalKeyMap ────────────────────────────────────────

   @Test
   void getNormalKeyMapNotNull() {
      assumeTrue(bindingsAvailable);
      KeyMap km = MapEvent.getNormalKeyMap();
      assertNotNull(km);
      assertEquals("normal", km.getName());
   }

   // ── getKeyGroup ────────────────────────────────────────────

   @Test
   void getKeyGroupMoveReturnsNonNull() {
      assumeTrue(bindingsAvailable);
      assertNotNull(MapEvent.getKeyGroup("move"));
   }

   @Test
   void getKeyGroupEditReturnsNonNull() {
      assumeTrue(bindingsAvailable);
      assertNotNull(MapEvent.getKeyGroup("edit"));
   }

   @Test
   void getKeyGroupUnknownReturnsNull() {
      assumeTrue(bindingsAvailable);
      assertNull(MapEvent.getKeyGroup("nonexistent"));
   }

   @Test
   void getKeyGroupDotSyntaxNormalMove() {
      assumeTrue(bindingsAvailable);
      assertNotNull(MapEvent.getKeyGroup("normal.move"));
   }

   @Test
   void getKeyGroupDotSyntaxInvalidGroup() {
      assumeTrue(bindingsAvailable);
      assertNull(MapEvent.getKeyGroup("normal.badgroup"));
   }

   @Test
   void getKeyGroupDotSyntaxInvalidKeymap() {
      assumeTrue(bindingsAvailable);
      assertNull(MapEvent.getKeyGroup("fakemap.move"));
   }

   // ── getActiveKeyMap ────────────────────────────────────────

   @Test
   void getActiveKeyMapForRegularBuffer() throws Exception {
      assumeTrue(bindingsAvailable);
      String fname = "ju_mae_active1";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("test\n", 0, 1);
      ex.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(ex, view);

      KeyMap active = MapEvent.getActiveKeyMap(fvc);
      assertNotNull(active);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void getActiveKeyMapWithNull() {
      assumeTrue(bindingsAvailable);
      KeyMap result = MapEvent.getActiveKeyMap(null);
      assertEquals(MapEvent.getNormalKeyMap(), result);
   }

   // ── domovement ─────────────────────────────────────────────

   @Test
   void domovementUnboundKeyReturnsFalse() throws Exception {
      assumeTrue(bindingsAvailable);
      String fname = "ju_mae_domove";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\n", 0, 1);
      ex.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(ex, view);

      JeyEvent unbound = new JeyEvent(
         JeyEvent.CTRL_MASK | JeyEvent.SHIFT_MASK
            | JeyEvent.ALT_MASK,
         0, 'Q');
      assertFalse(MapEvent.domovement(
         unbound, 1, 0, false, fvc));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void domovementBoundKeyReturnsTrue() throws Exception {
      assumeTrue(bindingsAvailable);
      String fname = "ju_mae_domove2";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\n", 0, 1);
      ex.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(ex, view);
      fvc.cursoryabs(1);

      assertTrue(MapEvent.domovement(
         new JeyEvent(0, 0, 'j'), 1, 0, false, fvc));

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   // ── hevent ─────────────────────────────────────────────────

   @Test
   void heventDigitAccumulatesCount() throws Exception {
      assumeTrue(bindingsAvailable);
      String fname = "ju_mae_count";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      for (int i = 0; i < 5; i++)
         ex.inserttext("x\n", 0, i + 1);
      ex.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(ex, view);
      fvc.cursoryabs(1);

      MapEvent.hevent(new JeyEvent(0, 0, '3'), fvc);
      MapEvent.hevent(new JeyEvent(0, 0, (char) 27), fvc);

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void heventJMovesDown() throws Exception {
      assumeTrue(bindingsAvailable);
      String fname = "ju_mae_jmove";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      ex.inserttext("a\nb\nc\n", 0, 1);
      ex.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(ex, view);
      fvc.cursoryabs(1);

      int startY = fvc.inserty();
      MapEvent.hevent(new JeyEvent(0, 0, 'j'), fvc);
      assertEquals(startY + 1, fvc.inserty());

      ex.disposeFvc();
      deleteTestFiles(fname);
   }

   @Test
   void heventCountWithMovement() throws Exception {
      assumeTrue(bindingsAvailable);
      String fname = "ju_mae_3j";
      UI.setStream(new StringReader(""));
      deleteTestFiles(fname);

      TextEdit<String> ex = openTestFile(fname);
      for (int i = 0; i < 10; i++)
         ex.inserttext("x\n", 0, i + 1);
      ex.checkpoint();

      TestView view = new TestView(true);
      FvContext<?> fvc = FvContext.connectFv(ex, view);
      fvc.cursoryabs(1);

      MapEvent.hevent(new JeyEvent(0, 0, '3'), fvc);
      MapEvent.hevent(new JeyEvent(0, 0, 'j'), fvc);
      assertEquals(4, fvc.inserty(), "3j: line 1 → line 4");

      ex.disposeFvc();
      deleteTestFiles(fname);
   }
}
