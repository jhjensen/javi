package javi.awt;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.TextAttribute;
import java.text.AttributedCharacterIterator;
import java.util.Map;

import javi.CellAttr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AtView} — the attributed character iterator
 * used for AWT text rendering. Exercises color resolution,
 * terminal attribute mapping, highlight state, and run limits.
 */
class AtViewJUnitTest {

   private AtView atv;

   @BeforeEach
   void setUp() {
      Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
      atv = new AtView(font);
   }

   // ── Color constants ──────────────────────────────────────

   @Nested
   @DisplayName("Color constants")
   class ColorConstants {

      @Test
      @DisplayName("background is black")
      void backgroundIsBlack() {
         assertEquals(Color.black, AtView.background);
      }

      @Test
      @DisplayName("foreground is green")
      void foregroundIsGreen() {
         assertEquals(Color.green, AtView.foreground);
      }

      @Test
      @DisplayName("cursor color is white")
      void cursorColorIsWhite() {
         assertEquals(Color.white, AtView.cursorColor);
      }

      @Test
      @DisplayName("insert cursor is pink")
      void insertCursorIsPink() {
         assertEquals(Color.pink, AtView.insertCursor);
      }

      @Test
      @DisplayName("paraBackground is dark green")
      void paraBackgroundIsDarkGreen() {
         assertEquals(new Color(0, 50, 0), AtView.paraBackground);
      }

      @Test
      @DisplayName("noFile color is dark blue")
      void noFileIsDarkBlue() {
         assertEquals(new Color(0, 0, 60), AtView.noFile);
      }

      @Test
      @DisplayName("interFrame is gray")
      void interFrameIsGray() {
         assertEquals(Color.gray, AtView.interFrame);
      }

      @Test
      @DisplayName("emphasize is red")
      void emphasizeIsRed() {
         assertEquals(Color.red, AtView.emphasize);
      }
   }

   // ── setText / getText ─────────────────────────────────────

   @Nested
   @DisplayName("setText / getText")
   class SetTextTests {

      @Test
      @DisplayName("setText stores text")
      void setTextStoresText() {
         atv.setText("hello world");
         assertEquals("hello world", atv.getText());
      }

      @Test
      @DisplayName("setText resets position to 0")
      void setTextResetsPosition() {
         atv.setText("abc");
         atv.next();
         atv.next();
         atv.setText("xyz");
         assertEquals(0, atv.getIndex());
      }

      @Test
      @DisplayName("length returns text length")
      void lengthReturnsTextLength() {
         atv.setText("abcdef");
         assertEquals(6, atv.length());
      }

      @Test
      @DisplayName("empty text has length 0")
      void emptyTextHasLength0() {
         atv.setText("");
         assertEquals(0, atv.length());
      }
   }

   // ── CharacterIterator methods ─────────────────────────────

   @Nested
   @DisplayName("CharacterIterator traversal")
   class IteratorTests {

      @Test
      @DisplayName("first() returns first char and sets pos=0")
      void firstReturnsFirstChar() {
         atv.setText("ABC");
         assertEquals('A', atv.first());
         assertEquals(0, atv.getIndex());
      }

      @Test
      @DisplayName("current() returns char at pos")
      void currentReturnsCharAtPos() {
         atv.setText("XYZ");
         assertEquals('X', atv.current());
      }

      @Test
      @DisplayName("next() advances position")
      void nextAdvancesPosition() {
         atv.setText("AB");
         assertEquals('B', atv.next());
         assertEquals(1, atv.getIndex());
      }

      @Test
      @DisplayName("next() returns DONE at end")
      void nextReturnsDoneAtEnd() {
         atv.setText("A");
         assertEquals(AttributedCharacterIterator.DONE, atv.next());
      }

      @Test
      @DisplayName("setIndex positions correctly")
      void setIndexWorks() {
         atv.setText("ABCDE");
         atv.setIndex(3);
         assertEquals('D', atv.current());
         assertEquals(3, atv.getIndex());
      }

      @Test
      @DisplayName("getBeginIndex is 0")
      void getBeginIndexIsZero() {
         atv.setText("text");
         assertEquals(0, atv.getBeginIndex());
      }

      @Test
      @DisplayName("getEndIndex is text length")
      void getEndIndexIsLength() {
         atv.setText("hello");
         assertEquals(5, atv.getEndIndex());
      }
   }

   // ── Highlight / emphasis state ────────────────────────────

   @Nested
   @DisplayName("Highlight and emphasis")
   class HighlightTests {

      @Test
      @DisplayName("no highlight — all positions use base attrs")
      void noHighlightUsesBaseAttrs() {
         atv.setText("hello");
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         assertEquals(AtView.foreground,
            m.get(TextAttribute.FOREGROUND));
         assertEquals(AtView.background,
            m.get(TextAttribute.BACKGROUND));
      }

      @Test
      @DisplayName("highlight changes background in range")
      void highlightChangesBackground() {
         atv.setText("hello");
         atv.setHighlight(2, 4);
         atv.setIndex(3); // inside highlight
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         Color bg = (Color) m.get(TextAttribute.BACKGROUND);
         // highlighted region uses lightBlue (0,0,128)
         assertEquals(new Color(0, 0, 128), bg);
      }

      @Test
      @DisplayName("position before highlight uses base bg")
      void beforeHighlightUsesBase() {
         atv.setText("hello");
         atv.setHighlight(2, 4);
         atv.setIndex(0);
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         assertEquals(AtView.background,
            m.get(TextAttribute.BACKGROUND));
      }

      @Test
      @DisplayName("position after highlight uses base bg")
      void afterHighlightUsesBase() {
         atv.setText("hello");
         atv.setHighlight(2, 4);
         atv.setIndex(4);
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         assertEquals(AtView.background,
            m.get(TextAttribute.BACKGROUND));
      }

      @Test
      @DisplayName("emphasize adds underline to base attrs")
      void emphasizeAddsUnderline() {
         atv.setText("hello");
         atv.emphasize(true);
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         assertNotNull(m.get(TextAttribute.UNDERLINE),
            "emphasized text should have underline attribute");
      }

      @Test
      @DisplayName("no emphasis has no underline in base")
      void noEmphasisNoUnderline() {
         atv.setText("hello");
         atv.emphasize(false);
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         // base 'by' map should not have UNDERLINE
         Object ul = m.get(TextAttribute.UNDERLINE);
         // 'by' does not have underline in its natural state
         assertTrue(ul == null,
            "non-emphasized base should not have underline");
      }
   }

   // ── Terminal attribute rendering ──────────────────────────

   @Nested
   @DisplayName("Terminal attributes via CellAttr")
   class TerminalAttrTests {

      @Test
      @DisplayName("default attr produces green-on-black")
      void defaultAttrGreenOnBlack() {
         atv.setText("x");
         atv.setTerminalAttrs(
            new int[]{CellAttr.DEFAULT});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         assertEquals(AtView.foreground,
            m.get(TextAttribute.FOREGROUND));
         assertEquals(AtView.background,
            m.get(TextAttribute.BACKGROUND));
      }

      @Test
      @DisplayName("bold default attr produces white fg")
      void boldDefaultProducesWhiteFg() {
         int attr = CellAttr.pack(true, false, false, -1, -1);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         assertEquals(Color.white,
            m.get(TextAttribute.FOREGROUND));
      }

      @Test
      @DisplayName("ANSI red foreground (color 1)")
      void ansiRedFg() {
         int attr = CellAttr.pack(
            false, false, false, 1, -1);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         Color fg = (Color) m.get(TextAttribute.FOREGROUND);
         assertEquals(new Color(205, 0, 0), fg);
      }

      @Test
      @DisplayName("ANSI green foreground (color 2)")
      void ansiGreenFg() {
         int attr = CellAttr.pack(
            false, false, false, 2, -1);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         Color fg = (Color) m.get(TextAttribute.FOREGROUND);
         assertEquals(new Color(0, 205, 0), fg);
      }

      @Test
      @DisplayName("bold + ANSI blue gives bright blue")
      void boldAnsiBlueBrightBlue() {
         int attr = CellAttr.pack(
            true, false, false, 4, -1);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         Color fg = (Color) m.get(TextAttribute.FOREGROUND);
         assertEquals(new Color(92, 92, 255), fg);
      }

      @Test
      @DisplayName("bright color 8-15 range")
      void brightColorRange() {
         // color 8 = bright black (index 0 of ANSI_BRIGHT)
         int attr = CellAttr.pack(
            false, false, false, 8, -1);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         Color fg = (Color) m.get(TextAttribute.FOREGROUND);
         assertEquals(new Color(127, 127, 127), fg);
      }

      @Test
      @DisplayName("256-color cube index 16 (r=0,g=0,b=0)")
      void colorCubeIndex16() {
         int attr = CellAttr.pack(
            false, false, false, 16, -1);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         Color fg = (Color) m.get(TextAttribute.FOREGROUND);
         assertEquals(new Color(0, 0, 0), fg);
      }

      @Test
      @DisplayName("256-color cube index 196 (r=5,g=0,b=0)")
      void colorCubeRed196() {
         // 196 = 16 + 5*36 + 0*6 + 0 = 196
         int attr = CellAttr.pack(
            false, false, false, 196, -1);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         Color fg = (Color) m.get(TextAttribute.FOREGROUND);
         assertEquals(new Color(255, 0, 0), fg);
      }

      @Test
      @DisplayName("greyscale index 232 (grey level 8)")
      void grayscale232() {
         int attr = CellAttr.pack(
            false, false, false, 232, -1);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         Color fg = (Color) m.get(TextAttribute.FOREGROUND);
         assertEquals(new Color(8, 8, 8), fg);
      }

      @Test
      @DisplayName("greyscale index 254 (grey level 228)")
      void grayscale254() {
         int attr = CellAttr.pack(
            false, false, false, 254, -1);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         Color fg = (Color) m.get(TextAttribute.FOREGROUND);
         assertEquals(new Color(228, 228, 228), fg);
      }

      @Test
      @DisplayName("reverse video swaps fg and bg")
      void reverseVideoSwapsFgBg() {
         int attr = CellAttr.pack(
            false, false, true, 1, 4);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         // reversed: fg=blue(4), bg=red(1)
         Color fg = (Color) m.get(TextAttribute.FOREGROUND);
         Color bg = (Color) m.get(TextAttribute.BACKGROUND);
         assertEquals(new Color(0, 0, 238), fg);
         assertEquals(new Color(205, 0, 0), bg);
      }

      @Test
      @DisplayName("underline attr adds UNDERLINE to term map")
      void underlineAddsUnderlineAttr() {
         int attr = CellAttr.pack(
            false, true, false, -1, -1);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         assertNotNull(m.get(TextAttribute.UNDERLINE));
      }

      @Test
      @DisplayName("no underline does not have UNDERLINE key")
      void noUnderlineRemovesKey() {
         int attr = CellAttr.pack(
            false, false, false, -1, -1);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         assertFalse(m.containsKey(TextAttribute.UNDERLINE));
      }

      @Test
      @DisplayName("background ANSI color (color 3 = yellow)")
      void backgroundAnsiColor() {
         int attr = CellAttr.pack(
            false, false, false, -1, 3);
         atv.setText("x");
         atv.setTerminalAttrs(new int[]{attr});
         Map<AttributedCharacterIterator.Attribute, Object> m =
            atv.getAttributes();
         Color bg = (Color) m.get(TextAttribute.BACKGROUND);
         assertEquals(new Color(205, 205, 0), bg);
      }
   }

   // ── Run limit computation ─────────────────────────────────

   @Nested
   @DisplayName("getRunLimit")
   class RunLimitTests {

      @Test
      @DisplayName("no highlight — run limit is text end")
      void noHighlightRunLimitIsEnd() {
         atv.setText("hello");
         assertEquals(5, atv.getRunLimit());
      }

      @Test
      @DisplayName("highlight — run limit stops at highlight start")
      void runLimitStopsAtHighlightStart() {
         atv.setText("hello");
         atv.setHighlight(2, 4);
         atv.setIndex(0);
         assertEquals(2, atv.getRunLimit());
      }

      @Test
      @DisplayName("inside highlight — run limit is highlight end")
      void runLimitInsideHighlight() {
         atv.setText("hello");
         atv.setHighlight(2, 4);
         atv.setIndex(2);
         assertEquals(4, atv.getRunLimit());
      }

      @Test
      @DisplayName("after highlight — run limit is text end")
      void runLimitAfterHighlight() {
         atv.setText("hello");
         atv.setHighlight(2, 4);
         atv.setIndex(4);
         assertEquals(5, atv.getRunLimit());
      }

      @Test
      @DisplayName("terminal attrs — run limit at attr change")
      void termRunLimitAtAttrChange() {
         int red = CellAttr.pack(
            false, false, false, 1, -1);
         int blue = CellAttr.pack(
            false, false, false, 4, -1);
         atv.setText("abcd");
         atv.setTerminalAttrs(new int[]{red, red, blue, blue});
         atv.setIndex(0);
         assertEquals(2, atv.getRunLimit());
      }

      @Test
      @DisplayName("terminal attrs — homogeneous run to end")
      void termRunLimitHomogeneous() {
         int attr = CellAttr.pack(
            false, false, false, 1, -1);
         atv.setText("abc");
         atv.setTerminalAttrs(new int[]{attr, attr, attr});
         atv.setIndex(0);
         assertEquals(3, atv.getRunLimit());
      }

      @Test
      @DisplayName("terminal attrs shorter than text")
      void termAttrsShorterThanText() {
         int red = CellAttr.pack(
            false, false, false, 1, -1);
         atv.setText("abcde");
         atv.setTerminalAttrs(new int[]{red, red});
         atv.setIndex(0);
         // first 2 chars are red, rest are DEFAULT
         assertEquals(2, atv.getRunLimit());
      }

      @Test
      @DisplayName("getRunLimit(BACKGROUND) stops at highlight")
      void runLimitBgStopsAtHighlight() {
         atv.setText("hello");
         atv.setHighlight(2, 4);
         atv.setIndex(0);
         assertEquals(2, atv.getRunLimit(TextAttribute.BACKGROUND));
      }

      @Test
      @DisplayName("getRunLimit(FONT) is text end")
      void runLimitFontIsEnd() {
         atv.setText("hello");
         atv.setHighlight(2, 4);
         atv.setIndex(0);
         assertEquals(5, atv.getRunLimit(TextAttribute.FONT));
      }
   }

   // ── toString / text manipulation ──────────────────────────

   @Nested
   @DisplayName("toString and text manipulation")
   class ToStringTests {

      @Test
      @DisplayName("toString includes position")
      void toStringIncludesPos() {
         atv.setText("hello");
         String s = atv.toString();
         assertTrue(s.contains("pos = 0"), s);
      }

      @Test
      @DisplayName("toString shows highlight when set")
      void toStringShowsHighlight() {
         atv.setText("hello");
         atv.setHighlight(2, 4);
         String s = atv.toString();
         assertTrue(s.contains("highlight(2,4)"), s);
      }

      @Test
      @DisplayName("toString shows emphasized")
      void toStringShowsEmphasized() {
         atv.setText("hello");
         atv.emphasize(true);
         String s = atv.toString();
         assertTrue(s.contains("emphasized"), s);
      }

      @Test
      @DisplayName("line2 returns false first time, true second")
      void line2Behavior() {
         atv.setText("hello world more");
         assertFalse(atv.line2(5));
         assertTrue(atv.line2(5));
      }

      @Test
      @DisplayName("deTab expands tabs")
      void deTabExpandsTabs() {
         atv.setText("a\tb");
         atv.deTab(8);
         String result = atv.getText();
         assertFalse(result.contains("\t"),
            "tabs should be expanded");
         assertTrue(result.length() > 3,
            "expanded text should be longer");
      }

      @Test
      @DisplayName("deTab with no tabs is no-op")
      void deTabNoTabsNoOp() {
         atv.setText("hello");
         atv.deTab(8);
         assertEquals("hello", atv.getText());
      }
   }

   // ── addOlineText ──────────────────────────────────────────

   @Nested
   @DisplayName("addOlineText (overlay text)")
   class OlineTests {

      @Test
      @DisplayName("addOlineText inserts at offset")
      void addOlineTextInsertsAtOffset() {
         atv.setText("hello world");
         atv.addOlineText("XX", 5, false);
         String result = atv.getText();
         assertTrue(result.contains("XX"),
            "overlay text should appear in result");
      }

      @Test
      @DisplayName("addOlineText overwrite mode")
      void addOlineTextOverwrite() {
         atv.setText("hello world");
         atv.addOlineText("XX", 5, true);
         String result = atv.getText();
         assertTrue(result.contains("XX"));
         // overwrite should not increase length
         assertEquals(11, result.length());
      }
   }
}
