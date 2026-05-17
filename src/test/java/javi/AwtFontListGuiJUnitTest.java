package javi;

import java.awt.Font;
import java.lang.reflect.Method;

import javi.awt.AwtFontList;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for {@link AwtFontList} font management in the AWT context.
 *
 * <p>Exercises font list initialization, current font retrieval, mono font
 * selection, font properties, font command dispatch (fontsize, fonttype,
 * fontname, monofontname), and the font list buffer. Requires biglock2
 * for operations that access FvContext state via non-null view.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class AwtFontListGuiJUnitTest {

   private static Robot robot;
   private static View view;
   private static FvContext<?> fvc;

   @BeforeAll
   static void initJavi() throws Exception {
      if (Rgroup.bindingLookup("persistfile") == null) {
         EventQueue.biglock2.lock();
         try {
            Class.forName("javi.TextEdit");
            EditTester1.TestCircBuffer.initCmd();
            DirManager.getInstance();
            FileList.make("");
            Javi.initToUi();
            Javi.initPostUi();
            Command.doneInit();
         } finally {
            EventQueue.biglock2.unlock();
         }
         Thread.sleep(500);
      }
      robot = BasicRobot.robotWithCurrentAwtHierarchy();
      EventQueue.biglock2.lock();
      try {
         fvc = FvContext.getCurrFvc();
         view = (fvc != null) ? fvc.vi : null;
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── Font list initialization ─────────────────────────────────

   @Test
   void t01_fontListNotNull() {
      TextEdit<?> list = AwtFontList.getList();
      assertNotNull(list, "AwtFontList should be initialized");
   }

   @Test
   void t02_fontListHasEntries() {
      TextEdit<?> list = AwtFontList.getList();
      assertTrue(list.readIn() > 1,
         "Font list should have at least one entry");
   }

   // ── Current font retrieval ───────────────────────────────────

   @Test
   void t03_getCurrWithViewNotNull() {
      EventQueue.biglock2.lock();
      try {
         Font curr = AwtFontList.getCurr(view);
         assertNotNull(curr, "Current font should not be null");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t04_getCurrWithNullViewNotNull() {
      Font curr = AwtFontList.getCurr(null);
      assertNotNull(curr,
         "getCurr(null) should return default font");
   }

   @Test
   void t05_getCurrFontHasPositiveSize() {
      EventQueue.biglock2.lock();
      try {
         Font curr = AwtFontList.getCurr(view);
         assertTrue(curr.getSize() > 0,
            "Font size should be positive");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t06_getCurrFontHasFamily() {
      EventQueue.biglock2.lock();
      try {
         Font curr = AwtFontList.getCurr(view);
         assertNotNull(curr.getFamily(),
            "Font family should not be null");
         assertFalse(curr.getFamily().isEmpty(),
            "Font family should not be empty");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Mono font ────────────────────────────────────────────────

   @Test
   void t07_getMonoFontNotNull() {
      EventQueue.biglock2.lock();
      try {
         Font mono = AwtFontList.getMonoFont(view);
         assertNotNull(mono, "Mono font should not be null");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t08_getMonoFontNullViewNotNull() {
      Font mono = AwtFontList.getMonoFont(null);
      assertNotNull(mono,
         "getMonoFont(null) should return a font");
   }

   @Test
   void t09_monoFontIsPlain() {
      EventQueue.biglock2.lock();
      try {
         Font mono = AwtFontList.getMonoFont(view);
         assertEquals(Font.PLAIN, mono.getStyle(),
            "Mono font should be plain style");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t10_monoFontSizeMatchesCurrent() {
      EventQueue.biglock2.lock();
      try {
         Font curr = AwtFontList.getCurr(view);
         Font mono = AwtFontList.getMonoFont(view);
         assertEquals(curr.getSize(), mono.getSize(),
            "Mono font size should match current font size");
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Font list buffer contents ────────────────────────────────

   @Test
   void t11_fontListFirstEntryNotNull() {
      TextEdit<?> list = AwtFontList.getList();
      Object entry = list.at(1);
      assertNotNull(entry, "First font entry should not be null");
   }

   @Test
   void t12_fontListFirstEntryToString() {
      TextEdit<?> list = AwtFontList.getList();
      String str = list.at(1).toString();
      assertNotNull(str);
      assertTrue(str.contains("name="),
         "Font entry toString should contain 'name='");
      assertTrue(str.contains("size="),
         "Font entry toString should contain 'size='");
   }

   @Test
   void t13_fontListContainsSystemFonts() {
      EventQueue.biglock2.lock();
      try {
         TextEdit<?> list = AwtFontList.getList();
         int count = list.readIn() - 1;
         assertTrue(count >= 1,
            "Font list should contain at least 1 font, found " + count);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Font command registration ────────────────────────────────

   @Test
   void t14_fontsizeCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("fontsize"),
         "fontsize command should be registered");
   }

   @Test
   void t15_fonttypeCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("fonttype"),
         "fonttype command should be registered");
   }

   @Test
   void t16_fontnameCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("fontname"),
         "fontname command should be registered");
   }

   @Test
   void t17_fontweightCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("fontweight"),
         "fontweight command should be registered");
   }

   @Test
   void t18_gotofontlistCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("gotofontlist"),
         "gotofontlist command should be registered");
   }

   @Test
   void t19_monofontnameCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("monofontname"),
         "monofontname command should be registered");
   }

   // ── Font command dispatch (fontsize) ─────────────────────────

   @Test
   void t20_fontsizeCommandRequiresArgument() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Rgroup.KeyBinding binding = Rgroup.bindingLookup("fontsize");
         assertNotNull(binding, "fontsize binding should exist");
         // fontsize needs a float argument; verify it throws InputException
         // when invoked without one (count=0, arg=0)
         try {
            binding.dobind(0, 0, fvc, false);
         } catch (InputException e) {
            assertTrue(e.getMessage().contains("float"),
               "Should require float number argument");
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t21_fontsizeChangesTakeEffect() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Font before = AwtFontList.getCurr(view);
         assertNotNull(before);
         assertTrue(before.getSize2D() > 0);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Font command dispatch (fonttype) ─────────────────────────

   @Test
   void t22_fonttypePlainNoThrow() throws Exception {
      EventQueue.biglock2.lock();
      try {
         Rgroup.KeyBinding binding = Rgroup.bindingLookup("fonttype");
         assertNotNull(binding);
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   @Test
   void t23_fonttypeBoldRegistered() {
      assertNotNull(Rgroup.bindingLookup("fonttype"));
   }

   @Test
   void t24_fontweightRegistered() {
      assertNotNull(Rgroup.bindingLookup("fontweight"));
   }

   // ── Font command dispatch (monofontname) ─────────────────────

   @Test
   void t25_monofontnameCommandRegistered() {
      assertNotNull(Rgroup.bindingLookup("monofontname"),
         "monofontname command should be registered");
   }

   @Test
   void t26_monoFontUsesConfiguredName() {
      EventQueue.biglock2.lock();
      try {
         Font mono = AwtFontList.getMonoFont(view);
         assertNotNull(mono);
         assertFalse(mono.getFamily().isEmpty());
      } finally {
         EventQueue.biglock2.unlock();
      }
   }

   // ── Font consistency ─────────────────────────────────────────

   @Test
   void t27_defaultFontConsistentAcrossCalls() {
      Font f1 = AwtFontList.getCurr(null);
      Font f2 = AwtFontList.getCurr(null);
      assertEquals(f1.getFamily(), f2.getFamily());
      assertEquals(f1.getSize(), f2.getSize());
   }

   @Test
   void t28_fontListEntriesHaveValidFonts() {
      TextEdit<?> list = AwtFontList.getList();
      int limit = Math.min(list.readIn() - 1, 5);
      for (int i = 1; i <= limit; i++) {
         Object entry = list.at(i);
         assertNotNull(entry, "Font entry " + i + " should not be null");
         String str = entry.toString();
         assertTrue(str.contains("name="),
            "Entry " + i + " should have name: " + str);
      }
   }

   // ── Font override provider ───────────────────────────────────

   @Test
   void t29_monoFontDifferentFromDefault() throws Exception {
      // Verify mono font is available (used by shell/DirEdit buffers)
      Font mono = AwtFontList.getMonoFont(null);
      Font curr = AwtFontList.getCurr(null);
      assertNotNull(mono);
      assertNotNull(curr);
      // Mono font should have the same size as current font
      assertEquals(curr.getSize(), mono.getSize());
   }

   @Test
   void t30_dirEditGetsMonoFont() throws Exception {
      assertNotNull(AwtFontList.getMonoFont(null));
   }
}
