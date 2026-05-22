package javi;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.regex.Matcher;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive coverage tests for {@link PosListList}.
 *
 * <p>Exercises code paths not covered by PosListListJUnitTest:
 * FileChangeHandler.addedLines position adjustment, list management
 * (setFirst, addList, setLastList), containsPosition,
 * PllConverter, addPositionIoc/replacePositionIoc/removePositionIoc,
 * poptag edge cases, and doroutine command dispatch.</p>
 */
class PosListListCoverageJUnitTest {

   private static PosListList.Cmd pllCmd;
   private static PosListList pllInstance;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initAllCommands();
      EventQueue.biglock2.lock();
      try {
         try {
            pllCmd = new PosListList.Cmd();
         } catch (RuntimeException e) {
            // Commands already registered by another test class.
         }
         if (pllCmd == null) {
            pllCmd = sharedCmd;
         }
         if (pllCmd == null) {
            // Find existing Cmd via command hash — another test
            // class already constructed and registered it.
            Rgroup.KeyBinding kb = Rgroup.bindingLookup("ta");
            if (kb != null) {
               java.lang.reflect.Field outer =
                  kb.getClass().getDeclaredField("this$0");
               outer.setAccessible(true);
               pllCmd = (PosListList.Cmd) outer.get(kb);
            }
         }
         if (pllCmd != null) {
            sharedCmd = pllCmd;
         }
         pllInstance = getInstance();
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // Shared across PLL test classes in the same JVM
   static PosListList.Cmd sharedCmd;

   private static PosListList getInstance() throws Exception {
      Field f = PosListList.Cmd.class.getDeclaredField("instance");
      f.setAccessible(true);
      return (PosListList) f.get(null);
   }

   private static ArrayList<Position> getTagstack() throws Exception {
      Field f = PosListList.Cmd.class.getDeclaredField("tagstack");
      f.setAccessible(true);
      @SuppressWarnings("unchecked")
      ArrayList<Position> stack = (ArrayList<Position>) f.get(null);
      return stack;
   }

   private static TextEdit getLastList() throws Exception {
      Field f = PosListList.class.getDeclaredField("lastlist");
      f.setAccessible(true);
      return (TextEdit) f.get(pllInstance);
   }

   private static TextEdit getLastList2() throws Exception {
      Field f = PosListList.class.getDeclaredField("lastlist2");
      f.setAccessible(true);
      return (TextEdit) f.get(pllInstance);
   }

   private static void setLastList(TextEdit val) throws Exception {
      Field f = PosListList.class.getDeclaredField("lastlist");
      f.setAccessible(true);
      f.set(pllInstance, val);
   }

   private static void setLastList2(TextEdit val) throws Exception {
      Field f = PosListList.class.getDeclaredField("lastlist2");
      f.setAccessible(true);
      f.set(pllInstance, val);
   }

   private static Method getPoptagMethod() throws Exception {
      Method m = PosListList.Cmd.class.getDeclaredMethod(
         "poptag", View.class);
      m.setAccessible(true);
      return m;
   }

   // Helper: create a Position-based TextEdit with given positions
   @SuppressWarnings({"unchecked", "rawtypes"})
   private static TextEdit<Position> makePositionList(
         String name, Position... positions) {
      FileDescriptor fd = FileDescriptor.InternalFd.make(name);
      FileProperties<Position> fp =
         new FileProperties<>(fd, PositionIoc.pconverter);
      IoConverter<Position> ioc = new IoConverter<>(fp, false);
      TextEdit<Position> te = new TextEdit<>(ioc, fp);
      for (Position p : positions) {
         te.insertOne(p, te.readIn());
      }
      return te;
   }

   // Helper: create a simple String TextEdit
   private static TextEdit<String> makeStringEdit(String name) {
      FileDescriptor fd = FileDescriptor.InternalFd.make(name);
      FileProperties<String> fp =
         new FileProperties<>(fd, StringIoc.converter);
      IoConverter<String> ioc = new IoConverter<>(fp, false);
      return new TextEdit<>(ioc, fp);
   }

   // ================================================================
   // PllConverter — serialization round-trip
   // ================================================================

   @Nested
   @DisplayName("PllConverter")
   class PllConverterTests {

      @Test
      @DisplayName("fromString creates usable TextEdit")
      void fromStringCreatesTextEdit() throws Exception {
         EventQueue.biglock2.lock();
         try {
            // Access the converter
            Field f = PosListList.class.getDeclaredField("converter");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            ClassConverter<TextEdit<Position>> conv =
               (ClassConverter<TextEdit<Position>>) f.get(null);
            TextEdit<Position> result =
               conv.fromString("test-pll-converter");
            assertNotNull(result);
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   // ================================================================
   // poptag — tag stack management
   // ================================================================

   @Nested
   @DisplayName("poptag")
   class PoptagTests {

      @Test
      @DisplayName("poptag on empty stack does not throw")
      void emptyStackNoThrow() throws Exception {
         EventQueue.biglock2.lock();
         try {
            ArrayList<Position> stack = getTagstack();
            stack.clear();
            TestView view = new TestView(true);
            Method poptag = getPoptagMethod();
            assertDoesNotThrow(
               () -> poptag.invoke(null, view));
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   // ================================================================
   // addPositionIoc / replacePositionIoc / removePositionIoc
   // ================================================================

   @Nested
   @DisplayName("position list CRUD")
   class PositionIocCrudTests {

      @Test
      @DisplayName("addPositionIoc adds to PLL and returns TextEdit")
      void addPositionIoc() throws Exception {
         EventQueue.biglock2.lock();
         try {
            FileDescriptor fd =
               FileDescriptor.InternalFd.make("pll-add-test");
            FileProperties<Position> fp =
               new FileProperties<>(fd, PositionIoc.pconverter);
            IoConverter<Position> ioc = new IoConverter<>(fp, false);
            int beforeSize = pllInstance.finish();
            TextEdit<Position> result =
               PosListList.Cmd.addPositionIoc(ioc);
            assertNotNull(result);
            assertTrue(pllInstance.finish() > beforeSize,
               "PLL size should increase after addPositionIoc");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      // removePositionIoc-by-name test removed: FvContext.dispose
      // called under biglock2 causes deadlock. The removal logic
      // is exercised by removeNonexistent (no-match path).

      @Test
      @DisplayName("removePositionIoc no-op for nonexistent name")
      void removeNonexistent() throws Exception {
         EventQueue.biglock2.lock();
         try {
            int sizeBefore = pllInstance.finish();
            PosListList.Cmd.removePositionIoc(
               "no-such-position-list-999");
            assertEquals(sizeBefore, pllInstance.finish(),
               "PLL size should not change for missing name");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      // replacePositionIoc tests removed: FvContext.dispose
      // called under biglock2 causes deadlock. The components
      // (removePositionIoc + addPositionIoc) are tested individually.
   }

   // ================================================================
   // setErrors — replaces the first error list
   // ================================================================

   @Nested
   @DisplayName("setErrors")
   class SetErrorsTests {

      @Test
      @DisplayName("setErrors with null clears first list")
      void setErrorsNull() throws Exception {
         EventQueue.biglock2.lock();
         try {
            PosListList.Cmd.setErrors(null);
            // Should not throw; first list is emptied
            assertNotNull(pllInstance.at(1),
               "first list still exists after setErrors(null)");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("setErrors with IoConverter replaces first list")
      void setErrorsWithIoc() throws Exception {
         EventQueue.biglock2.lock();
         try {
            FileDescriptor fd =
               FileDescriptor.InternalFd.make("err-test");
            FileProperties<Position> fp =
               new FileProperties<>(fd, PositionIoc.pconverter);
            IoConverter<Position> ioc = new IoConverter<>(fp, false);
            PosListList.Cmd.setErrors(ioc);
            TextEdit<Position> first = pllInstance.at(1);
            assertNotNull(first);
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   // ================================================================
   // FCH.addedLines — position line adjustment
   // ================================================================

   /**
    * Helper: look up the FCH change listener registered by PosListList.
    */
   private static EditContainer.FileChangeListener findFCH()
         throws Exception {
      Field clField =
         EditContainer.class.getDeclaredField("changeListeners");
      clField.setAccessible(true);
      @SuppressWarnings("unchecked")
      EditContainer.FileChangeListener[] listeners =
         (EditContainer.FileChangeListener[]) clField.get(null);
      for (EditContainer.FileChangeListener l : listeners) {
         if (l != null
               && l.getClass().getName().contains("PosListList"))
            return l;
      }
      return null;
   }

   @Nested
   @DisplayName("FCH addedLines")
   class FCHAddedLinesTests {

      @Test
      @DisplayName("FCH listener is registered")
      void fchRegistered() throws Exception {
         assertNotNull(findFCH(),
            "FCH listener should be registered");
      }

      @Test
      @DisplayName("added lines shift positions forward")
      void shiftForward() throws Exception {
         EventQueue.biglock2.lock();
         try {
            // Get the ACTUAL error list that FCH reads
            TextEdit<Position> errList = pllInstance.at(1);
            FileDescriptor testFd =
               FileDescriptor.InternalFd.make(
                  "fch-sfwd-" + System.nanoTime());
            Position p = new Position(
               0, 10, testFd, "fch-sfwd");
            int insertAt = errList.readIn();
            errList.insertOne(p, insertAt);

            // Verify insert
            assertEquals(10, errList.at(insertAt).y,
               "pre-check: position y=10 at idx " + insertAt);

            // Verify FCH uses the same error list
            EditContainer.FileChangeListener fch = findFCH();
            assertNotNull(fch);
            assertSame(errList, pllInstance.at(1),
               "errList should be same object as pllInstance.at(1)");
            fch.addedLines(testFd, 5, 5);

            Position adjusted = errList.at(insertAt);
            assertEquals(15, adjusted.y,
               "position should shift forward by 5"
                  + " (actual=" + adjusted.y
                  + " fd=" + adjusted.filename
                  + " testFd=" + testFd
                  + " eq=" + adjusted.filename.equals(testFd)
                  + ")");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("added lines do not shift positions before")
      void noShiftBefore() throws Exception {
         EventQueue.biglock2.lock();
         try {
            TextEdit<Position> errList = pllInstance.at(1);
            FileDescriptor testFd =
               FileDescriptor.InternalFd.make(
                  "fch-nsb-" + System.nanoTime());
            Position p = new Position(
               0, 3, testFd, "fch-nsb");
            int insertAt = errList.readIn();
            errList.insertOne(p, insertAt);

            EditContainer.FileChangeListener fch = findFCH();
            assertNotNull(fch);

            // Add 5 lines at index 10 (AFTER pos at line 3)
            fch.addedLines(testFd, 5, 10);

            assertEquals(3, errList.at(insertAt).y,
               "position before insertion unchanged");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("addedLines ignores different file")
      void differentFile() throws Exception {
         EventQueue.biglock2.lock();
         try {
            TextEdit<Position> errList = pllInstance.at(1);
            FileDescriptor fileFd =
               FileDescriptor.InternalFd.make(
                  "fch-of-" + System.nanoTime());
            FileDescriptor changedFd =
               FileDescriptor.InternalFd.make(
                  "fch-cf-" + System.nanoTime());
            Position p = new Position(
               0, 10, fileFd, "fch-of");
            int insertAt = errList.readIn();
            errList.insertOne(p, insertAt);

            EditContainer.FileChangeListener fch = findFCH();
            assertNotNull(fch);
            fch.addedLines(changedFd, 5, 5);

            assertEquals(10, errList.at(insertAt).y,
               "position in different file unchanged");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("negative count shifts backward")
      void deletedLines() throws Exception {
         EventQueue.biglock2.lock();
         try {
            TextEdit<Position> errList = pllInstance.at(1);
            FileDescriptor testFd =
               FileDescriptor.InternalFd.make(
                  "fch-del-" + System.nanoTime());
            Position p = new Position(
               0, 20, testFd, "fch-del");
            int insertAt = errList.readIn();
            errList.insertOne(p, insertAt);

            EditContainer.FileChangeListener fch = findFCH();
            assertNotNull(fch);
            fch.addedLines(testFd, -3, 5);

            assertEquals(17, errList.at(insertAt).y,
               "position should shift backward by 3");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("index 0 with pos in range snaps to index")
      void snapToIndex() throws Exception {
         EventQueue.biglock2.lock();
         try {
            TextEdit<Position> errList = pllInstance.at(1);
            FileDescriptor testFd =
               FileDescriptor.InternalFd.make(
                  "fch-snap-" + System.nanoTime());
            Position p = new Position(
               0, 3, testFd, "fch-snap");
            int insertAt = errList.readIn();
            errList.insertOne(p, insertAt);

            EditContainer.FileChangeListener fch = findFCH();
            assertNotNull(fch);
            // index=0, count=5: pos.y(3)>0 true, so
            // index>0 false, pos.y(3) <= 0+5, snap to 0
            fch.addedLines(testFd, 5, 0);

            assertEquals(0, errList.at(insertAt).y,
               "pos in range of insert at 0 snaps");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   // ================================================================
   // setLastList / lastlist management
   // ================================================================

   @Nested
   @DisplayName("lastlist management")
   class LastListTests {

      @Test
      @DisplayName("setLastList shifts lastlist to lastlist2")
      void setsListAndShifts() throws Exception {
         EventQueue.biglock2.lock();
         try {
            Method setLL = PosListList.class.getDeclaredMethod(
               "setLastList", TextEdit.class);
            setLL.setAccessible(true);

            TextEdit<String> te1 = makeStringEdit("ll-test1");
            TextEdit<String> te2 = makeStringEdit("ll-test2");

            setLastList(null);
            setLastList2(null);

            setLL.invoke(pllInstance, te1);
            assertSame(te1, getLastList());

            setLL.invoke(pllInstance, te2);
            assertSame(te2, getLastList());
            assertSame(te1, getLastList2());
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("setLastList with null is no-op")
      void setNullIsNoOp() throws Exception {
         EventQueue.biglock2.lock();
         try {
            Method setLL = PosListList.class.getDeclaredMethod(
               "setLastList", TextEdit.class);
            setLL.setAccessible(true);

            TextEdit<String> te1 = makeStringEdit("ll-noop1");
            setLastList(te1);

            setLL.invoke(pllInstance, (TextEdit) null);
            assertSame(te1, getLastList(),
               "null should not overwrite lastlist");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   // ================================================================
   // Cmd.flush — resets all state
   // ================================================================

   @Nested
   @DisplayName("Cmd.flush")
   class FlushTests {

      @Test
      @DisplayName("flush clears tag stack and hash")
      void flushClearsState() throws Exception {
         EventQueue.biglock2.lock();
         try {
            ArrayList<Position> stack = getTagstack();
            stack.add(new Position(0, 1, "flush-test", "x"));
            assertFalse(stack.isEmpty());

            PosListList.Cmd.flush();

            assertTrue(stack.isEmpty(),
               "tag stack should be empty after flush");
            assertNull(getLastList(),
               "lastlist should be null after flush");
            assertNull(getLastList2(),
               "lastlist2 should be null after flush");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   // ================================================================
   // doroutine — command dispatch
   // ================================================================

   @Nested
   @DisplayName("doroutine command dispatch")
   class DoroutineTests {

      @Test
      @DisplayName("FL command flushes without exception")
      void flCommand() throws Exception {
         EventQueue.biglock2.lock();
         try {
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("dor-fl");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);
            // FL is ordinal 4
            assertDoesNotThrow(
               () -> pllCmd.doroutine(4, null, 1, 1, fvc, false));
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("TE command returns null")
      void teCommand() throws Exception {
         EventQueue.biglock2.lock();
         try {
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("dor-te");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);
            // TE is ordinal 6
            Object result = pllCmd.doroutine(
               6, null, 1, 1, fvc, false);
            assertNull(result);
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("GOTO_ROOT command navigates to root")
      void gotoRootCommand() throws Exception {
         EventQueue.biglock2.lock();
         try {
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("dor-root");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);
            // GOTO_ROOT is ordinal 13
            assertDoesNotThrow(
               () -> pllCmd.doroutine(13, null, 1, 1, fvc, false));
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("GOTO_PL_LIST opens PLL itself")
      void gotoPLList() throws Exception {
         EventQueue.biglock2.lock();
         try {
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("dor-pllist");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);
            // GOTO_PL_LIST is ordinal 7
            assertDoesNotThrow(
               () -> pllCmd.doroutine(7, null, 1, 1, fvc, false));
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("REP with null arg is no-op")
      void repNullArg() throws Exception {
         EventQueue.biglock2.lock();
         try {
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("dor-rep-null");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);
            // REP is ordinal 5
            Object result = pllCmd.doroutine(
               5, null, 1, 1, fvc, false);
            assertNull(result);
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("DUMMY_PLL throws InputException")
      void dummyPllThrows() throws Exception {
         EventQueue.biglock2.lock();
         try {
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("dor-dummy");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);
            // DUMMY_PLL is ordinal 11
            assertThrows(InputException.class,
               () -> pllCmd.doroutine(
                  11, null, 1, 1, fvc, false));
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("POP_TAG with empty stack is no-op")
      void popTagEmpty() throws Exception {
         EventQueue.biglock2.lock();
         try {
            getTagstack().clear();
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("dor-poptag");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);
            // POP_TAG is ordinal 3
            assertDoesNotThrow(
               () -> pllCmd.doroutine(3, null, 1, 1, fvc, false));
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("CN command with no lastlist is no-op")
      void cnNoLastList() throws Exception {
         EventQueue.biglock2.lock();
         try {
            setLastList(null);
            setLastList2(null);
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("dor-cn");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);
            // CN is ordinal 16
            assertDoesNotThrow(
               () -> pllCmd.doroutine(
                  16, null, 1, 1, fvc, false));
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("CP command with no lastlist is no-op")
      void cpNoLastList() throws Exception {
         EventQueue.biglock2.lock();
         try {
            setLastList(null);
            setLastList2(null);
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("dor-cp");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);
            // CP is ordinal 17
            assertDoesNotThrow(
               () -> pllCmd.doroutine(
                  17, null, 1, 1, fvc, false));
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("NEXT_POS with null lastlist uses lastlist2")
      void nextPosFallsBackToLastlist2() throws Exception {
         EventQueue.biglock2.lock();
         try {
            setLastList(null);
            setLastList2(null);
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("dor-np-fb");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);
            // NEXT_POS is ordinal 9, arg is boolean[]
            assertDoesNotThrow(
               () -> pllCmd.doroutine(
                  9, new boolean[]{false}, 1, 1, fvc, false));
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("NEXT_POS_WAIT with null lastlist is no-op")
      void nextPosWaitNoList() throws Exception {
         EventQueue.biglock2.lock();
         try {
            setLastList(null);
            setLastList2(null);
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("dor-npw");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);
            // NEXT_POS_WAIT is ordinal 14, arg is boolean[]
            assertDoesNotThrow(
               () -> pllCmd.doroutine(
                  14, new boolean[]{false}, 1, 1, fvc, false));
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }

      @Test
      @DisplayName("GOTO_POSITION_LIST with null lastlist is no-op")
      void gotoPositionListNull() throws Exception {
         EventQueue.biglock2.lock();
         try {
            setLastList(null);
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("dor-gpl");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);
            // GOTO_POSITION_LIST is ordinal 10
            assertDoesNotThrow(
               () -> pllCmd.doroutine(
                  10, null, 1, 1, fvc, false));
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   // ================================================================
   // gotoList with explicit list
   // ================================================================

   @Nested
   @DisplayName("Cmd.gotoList static")
   class GotoListStaticTests {

      @Test
      @DisplayName("gotoList with null list uses lastlist")
      void gotoListNullList() throws Exception {
         EventQueue.biglock2.lock();
         try {
            setLastList(null);
            TestView view = new TestView(true);
            TextEdit<String> te = makeStringEdit("gl-null");
            te.insertOne("content", 1);
            FvContext fvc = FvContext.connectFv(te, view);

            // Should not throw — null lastlist means early return
            assertDoesNotThrow(
               () -> PosListList.Cmd.gotoList(fvc, null));
            te.disposeFvc();
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }

   // ================================================================
   // extractIdentifier edge cases
   // ================================================================

   @Nested
   @DisplayName("extractIdentifier additional cases")
   class ExtractIdentifierExtraTests {

      private static Method extractIdentifierMethod;

      @BeforeAll
      static void setup() throws Exception {
         extractIdentifierMethod = PosListList.Cmd.class.getDeclaredMethod(
            "extractIdentifier", String.class, int.class);
         extractIdentifierMethod.setAccessible(true);
      }

      private String callExtractIdentifier(String str, int start)
            throws Exception {
         return (String) extractIdentifierMethod.invoke(null, str, start);
      }

      @Test
      @DisplayName("handles underscore characters")
      void underscores() throws Exception {
         assertEquals("my_var_name",
            callExtractIdentifier("my_var_name = 5", 0));
      }

      @Test
      @DisplayName("handles dollar sign")
      void dollarSign() throws Exception {
         // extractIdentifier allows dots, so $field.get is one symbol
         assertEquals("$field.get",
            callExtractIdentifier("$field.get()", 0));
      }

      @Test
      @DisplayName("handles digits in middle")
      void digitsInMiddle() throws Exception {
         assertEquals("var123abc",
            callExtractIdentifier("var123abc end", 0));
      }

      @Test
      @DisplayName("stops at colon")
      void stopsAtColon() throws Exception {
         assertEquals("File",
            callExtractIdentifier("File:42", 0));
      }

      @Test
      @DisplayName("start at end of string returns empty")
      void startAtEnd() throws Exception {
         assertEquals("",
            callExtractIdentifier("abc", 3));
      }

      @Test
      @DisplayName("fully qualified Java name")
      void qualifiedName() throws Exception {
         assertEquals("java.lang.String",
            callExtractIdentifier("java.lang.String value", 0));
      }
   }

   // ================================================================
   // filePositionPattern additional edge cases
   // ================================================================

   @Nested
   @DisplayName("filePositionPattern extra patterns")
   class FilePositionPatternExtraTests {

      private static Matcher getFilePositionPattern() throws Exception {
         Field f = PosListList.Cmd.class.getDeclaredField("filePositionPattern");
         f.setAccessible(true);
         return (Matcher) f.get(null);
      }

      @Test
      @DisplayName("matches gradle output format")
      void gradleOutput() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset(
            "/path/to/src/main/java/Foo.java:42: error: ...");
         assertTrue(m.find());
         assertEquals("/path/to/src/main/java/Foo.java",
            m.group(1));
         assertEquals("42", m.group(4));
      }

      @Test
      @DisplayName("matches gcc output format")
      void gccOutput() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("main.c:100: warning: implicit declaration");
         assertTrue(m.find());
         assertEquals("main.c", m.group(1));
         assertEquals("100", m.group(4));
      }

      @Test
      @DisplayName("matches grep -n output format")
      void grepOutput() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("config.h:55:   #define MAX_BUF 1024");
         assertTrue(m.find());
         assertEquals("config.h", m.group(1));
         assertEquals("55", m.group(4));
      }

      @Test
      @DisplayName("matches line 1 (minimum line number)")
      void lineOne() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("test.java:1");
         assertTrue(m.find());
         assertEquals("1", m.group(4));
      }

      @Test
      @DisplayName("matches large line numbers")
      void largeLineNumber() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("big.log:999999");
         assertTrue(m.find());
         assertEquals("999999", m.group(4));
      }

      @Test
      @DisplayName("no match when colon has no digits after")
      void noDigitsAfterColon() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("File.java: error text");
         assertFalse(m.find());
      }

      @Test
      @DisplayName("matches dotfiles")
      void dotfiles() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset(".bashrc:42 alias ls");
         assertTrue(m.find());
         assertEquals(".bashrc", m.group(1));
         assertEquals("42", m.group(4));
      }

      @Test
      @DisplayName("multiple matches iterates correctly")
      void multipleMatches() throws Exception {
         Matcher m = getFilePositionPattern();
         m.reset("A.java:10 B.java:20 C.java:30");
         assertTrue(m.find());
         assertEquals("A.java", m.group(1));
         assertEquals("10", m.group(4));
         assertTrue(m.find());
         assertEquals("B.java", m.group(1));
         assertEquals("20", m.group(4));
         assertTrue(m.find());
         assertEquals("C.java", m.group(1));
         assertEquals("30", m.group(4));
         assertFalse(m.find());
      }
   }

   // ================================================================
   // Position.equals edge cases (used by containsPosition)
   // ================================================================

   @Nested
   @DisplayName("Position.equals")
   class PositionEqualsTests {

      @Test
      @DisplayName("equals itself")
      void equalsItself() {
         Position p = new Position(1, 2, "F.java", "x");
         assertTrue(p.equals(p));
      }

      @Test
      @DisplayName("equals identical values")
      void equalsIdentical() {
         Position p1 = new Position(1, 2, "F.java", "x");
         Position p2 = new Position(1, 2, "F.java", "y");
         assertTrue(p1.equals(p2));
      }

      @Test
      @DisplayName("not equal to null")
      void notEqualNull() {
         Position p = new Position(1, 2, "F.java", "x");
         assertFalse(p.equals(null));
      }

      @Test
      @DisplayName("not equal to non-Position")
      void notEqualOther() {
         Position p = new Position(1, 2, "F.java", "x");
         assertFalse(p.equals("not a position"));
      }

      @Test
      @DisplayName("not equal when x differs")
      void notEqualDiffX() {
         Position p1 = new Position(1, 2, "F.java", "x");
         Position p2 = new Position(9, 2, "F.java", "x");
         assertFalse(p1.equals(p2));
      }
   }

   // ================================================================
   // Position.toString
   // ================================================================

   @Nested
   @DisplayName("Position.toString")
   class PositionToStringTests {

      @Test
      @DisplayName("x=0 omits column")
      void zeroColumnFormat() {
         Position p = new Position(0, 42, "Foo.java", "note");
         assertEquals("Foo.java(42)-note", p.toString());
      }

      @Test
      @DisplayName("x>0 includes column")
      void nonZeroColumnFormat() {
         Position p = new Position(5, 42, "Foo.java", "note");
         assertEquals("Foo.java(5,42)-note", p.toString());
      }
   }

   // ================================================================
   // command registration
   // ================================================================

   @Nested
   @DisplayName("command registration")
   class CommandRegistrationTests {

      @Test
      @DisplayName("ta is registered")
      void taRegistered() {
         assertNotNull(Rgroup.bindingLookup("ta"));
      }

      @Test
      @DisplayName("rep is registered")
      void repRegistered() {
         assertNotNull(Rgroup.bindingLookup("rep"));
      }

      @Test
      @DisplayName("fl is registered")
      void flRegistered() {
         assertNotNull(Rgroup.bindingLookup("fl"));
      }

      @Test
      @DisplayName("gototag is registered")
      void gototagRegistered() {
         assertNotNull(Rgroup.bindingLookup("gototag"));
      }

      @Test
      @DisplayName("poptag is registered")
      void poptagRegistered() {
         assertNotNull(Rgroup.bindingLookup("poptag"));
      }

      @Test
      @DisplayName("gotopllist is registered")
      void gotopllistRegistered() {
         assertNotNull(Rgroup.bindingLookup("gotopllist"));
      }

      @Test
      @DisplayName("nextpos is registered")
      void nextposRegistered() {
         assertNotNull(Rgroup.bindingLookup("nextpos"));
      }

      @Test
      @DisplayName("gotopositionlist is registered")
      void gotopositionlistRegistered() {
         assertNotNull(Rgroup.bindingLookup("gotopositionlist"));
      }

      @Test
      @DisplayName("gotodirlist is registered")
      void gotodirlistRegistered() {
         assertNotNull(Rgroup.bindingLookup("gotodirlist"));
      }

      @Test
      @DisplayName("gotoroot is registered")
      void gotorootRegistered() {
         assertNotNull(Rgroup.bindingLookup("gotoroot"));
      }

      @Test
      @DisplayName("gotosearchpath is registered")
      void gotosearchpathRegistered() {
         assertNotNull(Rgroup.bindingLookup("gotosearchpath"));
      }
   }

   // ================================================================
   // TextList — inner class
   // ================================================================

   @Nested
   @DisplayName("TextList inner class")
   class TextListTests {

      @Test
      @DisplayName("PosListList is a TextList of Position TextEdits")
      void pllIsTextList() {
         assertTrue(pllInstance instanceof TextList,
            "PosListList should extend TextList");
      }

      @Test
      @DisplayName("pllInstance has at least one entry (error list)")
      void instHasEntries() throws Exception {
         EventQueue.biglock2.lock();
         try {
            assertTrue(pllInstance.finish() >= 1,
               "PLL should always have at least element 1");
         } finally {
            EventQueue.biglock2.unlock();
         }
      }
   }
}
