package javi.awt;

import java.awt.Font;
import java.lang.reflect.Field;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link StatusBar} — message management, font handling,
 * and dimension calculation. All tests run headlessly (no display
 * required) since StatusBar can be constructed without a peer.
 */
class StatusBarJUnitTest {

   private StatusBar bar;

   @BeforeEach
   void setUp() {
      bar = new StatusBar();
      bar.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
   }

   @SuppressWarnings("unchecked")
   private ArrayList<String> getMessages() throws Exception {
      Field f = StatusBar.class.getDeclaredField("messeges");
      f.setAccessible(true);
      return (ArrayList<String>) f.get(bar);
   }

   // ── Construction ──────────────────────────────────────────

   @Nested
   @DisplayName("Construction")
   class Construction {

      @Test
      @DisplayName("newly created StatusBar has green background")
      void backgroundIsGreen() {
         assertEquals(AtView.foreground, bar.getBackground());
      }

      @Test
      @DisplayName("messages list is empty on creation")
      void messagesEmptyOnCreation() throws Exception {
         assertTrue(getMessages().isEmpty());
      }
   }

   // ── isFocusable ───────────────────────────────────────────

   @Test
   @DisplayName("isFocusable returns false")
   void notFocusable() {
      assertFalse(bar.isFocusable());
   }

   // ── addline ───────────────────────────────────────────────

   @Nested
   @DisplayName("addline")
   class AddLine {

      @Test
      @DisplayName("addline appends to messages")
      void addlineAppendsMessage() throws Exception {
         bar.addline("first");
         ArrayList<String> msgs = getMessages();
         assertEquals(1, msgs.size());
         assertEquals("first", msgs.get(0));
      }

      @Test
      @DisplayName("addline multiple accumulates messages")
      void addlineAccumulates() throws Exception {
         bar.addline("one");
         bar.addline("two");
         bar.addline("three");
         ArrayList<String> msgs = getMessages();
         assertEquals(3, msgs.size());
         assertEquals("one", msgs.get(0));
         assertEquals("two", msgs.get(1));
         assertEquals("three", msgs.get(2));
      }
   }

   // ── setline ───────────────────────────────────────────────

   @Nested
   @DisplayName("setline")
   class SetLine {

      @Test
      @DisplayName("setline replaces all messages with one")
      void setlineReplacesAll() throws Exception {
         bar.addline("old1");
         bar.addline("old2");
         bar.setline("new");
         ArrayList<String> msgs = getMessages();
         assertEquals(1, msgs.size());
         assertEquals("new", msgs.get(0));
      }
   }

   // ── clearlines ────────────────────────────────────────────

   @Nested
   @DisplayName("clearlines")
   class ClearLines {

      @Test
      @DisplayName("clearlines empties messages")
      void clearlinesEmptiesMessages() throws Exception {
         bar.addline("data");
         bar.clearlines();
         assertTrue(getMessages().isEmpty());
      }

      @Test
      @DisplayName("clearlines on empty is safe")
      void clearlinesOnEmptyIsSafe() throws Exception {
         bar.clearlines();
         assertTrue(getMessages().isEmpty());
      }
   }

   // ── setFont ───────────────────────────────────────────────

   @Nested
   @DisplayName("setFont")
   class SetFontTests {

      @Test
      @DisplayName("setFont resets charheight to zero")
      void setFontResetsCharHeight() throws Exception {
         // Pre-condition: force charheight to be computed
         Field ch = StatusBar.class.getDeclaredField("charheight");
         ch.setAccessible(true);
         ch.setInt(bar, 15);

         bar.setFont(
            new Font(Font.SANS_SERIF, Font.BOLD, 18));

         assertEquals(0, ch.getInt(bar),
            "charheight should be reset to 0 after setFont");
      }

      @Test
      @DisplayName("getFont returns the most recently set font")
      void getFontReturnsSetFont() {
         Font f = new Font(Font.SERIF, Font.ITALIC, 20);
         bar.setFont(f);
         assertEquals(f, bar.getFont());
      }
   }

   // ── sizeChanged flag ──────────────────────────────────────

   @Nested
   @DisplayName("sizeChanged flag")
   class SizeChangedFlag {

      @Test
      @DisplayName("addline sets sizeChanged")
      void addlineSetsFlag() throws Exception {
         Field sc =
            StatusBar.class.getDeclaredField("sizeChanged");
         sc.setAccessible(true);
         sc.setBoolean(bar, false);

         bar.addline("trigger");

         assertTrue(sc.getBoolean(bar));
      }

      @Test
      @DisplayName("clearlines sets sizeChanged when non-empty")
      void clearlinesSetsFlag() throws Exception {
         bar.addline("trigger");
         Field sc =
            StatusBar.class.getDeclaredField("sizeChanged");
         sc.setAccessible(true);
         sc.setBoolean(bar, false);

         bar.clearlines();

         assertTrue(sc.getBoolean(bar));
      }
   }
}
