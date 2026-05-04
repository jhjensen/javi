package javi;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for {@code javi.awt.StatusBar} — the AWT Canvas that
 * renders status messages and editor state at the bottom of the frame.
 *
 * <p>Tests message management, font metrics, preferred size, paint
 * stability, and visibility via the UI facade and reflection. The
 * StatusBar is package-private in javi.awt so access is indirect.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class StatusBarGuiJUnitTest {

   private static Robot robot;
   private static Object statusBar;
   private static Class<?> sbClass;

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

      UI ui = UI.getInstance();
      Class<?> awtIfClass = ui.getClass();
      Field sbField = awtIfClass.getDeclaredField("statusBar");
      sbField.setAccessible(true);
      statusBar = sbField.get(ui);
      sbClass = statusBar.getClass();
      assertNotNull(statusBar, "StatusBar instance should exist");
   }

   @AfterAll
   static void tearDownAll() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── Helpers (all via getDeclaredMethod + setAccessible) ──────

   private static Method getMethod(String name, Class<?>... params)
         throws Exception {
      Method m = sbClass.getDeclaredMethod(name, params);
      m.setAccessible(true);
      return m;
   }

   private static void addline(String line) throws Exception {
      getMethod("addline", String.class).invoke(statusBar, line);
   }

   private static void setline(String line) throws Exception {
      getMethod("setline", String.class).invoke(statusBar, line);
   }

   private static void clearlines() throws Exception {
      getMethod("clearlines").invoke(statusBar);
   }

   @SuppressWarnings("unchecked")
   private static ArrayList<String> getMessages() throws Exception {
      Field f = sbClass.getDeclaredField("messeges");
      f.setAccessible(true);
      return (ArrayList<String>) f.get(statusBar);
   }

   private static int getIntField(String name) throws Exception {
      Field f = sbClass.getDeclaredField(name);
      f.setAccessible(true);
      return f.getInt(statusBar);
   }

   private static boolean getSizeChanged() throws Exception {
      Field f = sbClass.getDeclaredField("sizeChanged");
      f.setAccessible(true);
      return f.getBoolean(statusBar);
   }

   // Use Component.class methods to avoid access checks on the
   // package-private StatusBar subclass
   private static void setVisible(boolean v) throws Exception {
      Method m = sbClass.getDeclaredMethod("setVisible", boolean.class);
      m.setAccessible(true);
      m.invoke(statusBar, v);
   }

   private static Dimension getPreferredSize() throws Exception {
      Method m = sbClass.getDeclaredMethod("getPreferredSize");
      m.setAccessible(true);
      return (Dimension) m.invoke(statusBar);
   }

   private static void setFont(Font f) throws Exception {
      Method m = sbClass.getDeclaredMethod("setFont", Font.class);
      m.setAccessible(true);
      m.invoke(statusBar, f);
   }

   private static void doPaint(Graphics g) throws Exception {
      Method m = sbClass.getDeclaredMethod("paint", Graphics.class);
      m.setAccessible(true);
      m.invoke(statusBar, g);
   }

   // ── Basic instance tests ─────────────────────────────────────

   @Test
   void t01_statusBarIsCanvas() {
      assertTrue(statusBar instanceof Canvas,
         "StatusBar should extend Canvas");
   }

   @Test
   void t02_statusBarNotFocusable() throws Exception {
      Method m = sbClass.getDeclaredMethod("isFocusable");
      m.setAccessible(true);
      assertFalse((boolean) m.invoke(statusBar),
         "StatusBar should not be focusable");
   }

   // ── Message management ───────────────────────────────────────

   @Test
   void t03_addlineSingleMessage() throws Exception {
      clearlines();
      addline("test message one");
      ArrayList<String> msgs = getMessages();
      assertEquals(1, msgs.size());
      assertEquals("test message one", msgs.get(0));
      clearlines();
   }

   @Test
   void t04_addlineMultipleMessages() throws Exception {
      clearlines();
      addline("line A");
      addline("line B");
      addline("line C");
      ArrayList<String> msgs = getMessages();
      assertEquals(3, msgs.size());
      assertEquals("line A", msgs.get(0));
      assertEquals("line C", msgs.get(2));
      clearlines();
   }

   @Test
   void t05_setlineReplacesAll() throws Exception {
      clearlines();
      addline("old1");
      addline("old2");
      setline("replacement");
      ArrayList<String> msgs = getMessages();
      assertEquals(1, msgs.size());
      assertEquals("replacement", msgs.get(0));
      clearlines();
   }

   @Test
   void t06_clearlinesRemovesAll() throws Exception {
      addline("x");
      addline("y");
      clearlines();
      assertEquals(0, getMessages().size());
   }

   @Test
   void t07_clearEmptyIsIdempotent() throws Exception {
      clearlines();
      clearlines();
      assertEquals(0, getMessages().size());
   }

   // ── Font metrics initialization ──────────────────────────────

   @Test
   void t08_setFontResetsMetrics() throws Exception {
      Font testFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);
      setFont(testFont);
      assertEquals(0, getIntField("charheight"),
         "setFont should reset charheight to 0");
      // Trigger re-initialization via getPreferredSize
      Canvas canvas = (Canvas) statusBar;
      canvas.setSize(400, 20);
      getPreferredSize();
      assertTrue(getIntField("charheight") > 0,
         "charheight should be positive after getPreferredSize");
   }

   // ── Preferred size computation ───────────────────────────────

   @Test
   void t09_preferredSizeEmptyMessages() throws Exception {
      clearlines();
      Canvas canvas = (Canvas) statusBar;
      canvas.setSize(400, 20);
      Dimension pref = getPreferredSize();
      assertNotNull(pref);
      assertTrue(pref.height > 0,
         "Preferred height with no messages should be positive");
   }

   @Test
   void t10_preferredSizeGrowsWithMessages() throws Exception {
      clearlines();
      Canvas canvas = (Canvas) statusBar;
      canvas.setSize(400, 20);
      Dimension empty = getPreferredSize();

      addline("Hello world");
      Dimension one = getPreferredSize();
      assertTrue(one.height >= empty.height);

      addline("Second line");
      Dimension two = getPreferredSize();
      assertTrue(two.height >= one.height);
      clearlines();
   }

   @Test
   void t11_preferredSizeWrapsLongLines() throws Exception {
      clearlines();
      Canvas canvas = (Canvas) statusBar;
      canvas.setSize(100, 20);
      addline("A".repeat(200));
      Dimension pref = getPreferredSize();
      int ch = getIntField("charheight");
      assertTrue(pref.height > ch * 2,
         "Long line should wrap, height=" + pref.height);
      clearlines();
   }

   // ── Visibility toggling ──────────────────────────────────────

   @Test
   void t12_setVisibleFalseClearsMessages() throws Exception {
      addline("visible test");
      setVisible(false);
      assertEquals(0, getMessages().size(),
         "setVisible(false) should clear messages");
      setVisible(true);
   }

   @Test
   void t13_setVisibleTrueNoOpWhenAlreadyVisible() throws Exception {
      setVisible(true);
      addline("already visible");
      assertEquals(1, getMessages().size());
      clearlines();
   }

   // ── Paint stability ──────────────────────────────────────────

   @Test
   void t14_paintWithNoMessagesDoesNotCrash() throws Exception {
      clearlines();
      Canvas canvas = (Canvas) statusBar;
      canvas.setSize(400, 40);
      BufferedImage img = new BufferedImage(
         400, 40, BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      g.setFont(canvas.getFont());
      doPaint(g);
      g.dispose();
      assertNotNull(statusBar);
   }

   @Test
   void t15_paintWithMessagesDoesNotCrash() throws Exception {
      addline("paint test line 1");
      addline("paint test line 2");
      Canvas canvas = (Canvas) statusBar;
      canvas.setSize(400, 80);
      BufferedImage img = new BufferedImage(
         400, 80, BufferedImage.TYPE_INT_RGB);
      Graphics g = img.createGraphics();
      g.setFont(canvas.getFont());
      doPaint(g);
      g.dispose();
      assertNotNull(statusBar);
      clearlines();
   }

   // ── addline makes visible ────────────────────────────────────

   @Test
   void t16_addlineMakesVisible() throws Exception {
      setVisible(false);
      addline("makes visible");
      assertTrue(((Canvas) statusBar).isVisible(),
         "addline should make StatusBar visible");
      clearlines();
   }

   @Test
   void t17_setlineMakesVisible() throws Exception {
      setVisible(false);
      setline("setline visible");
      assertTrue(((Canvas) statusBar).isVisible(),
         "setline should make StatusBar visible");
      clearlines();
   }

   // ── sizeChanged flag ─────────────────────────────────────────

   @Test
   void t18_addlineSetsChangedFlag() throws Exception {
      clearlines();
      addline("flag test");
      assertTrue(getSizeChanged(),
         "sizeChanged should be true after addline");
      clearlines();
   }

   @Test
   void t19_setlineSetsChangedFlag() throws Exception {
      clearlines();
      setline("flag test 2");
      assertTrue(getSizeChanged(),
         "sizeChanged should be true after setline");
      clearlines();
   }
}
