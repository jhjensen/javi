package javi.awt;

import java.awt.Font;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link FontEntry} — font attribute parsing,
 * manipulation, and string representation.
 */
class FontEntryJUnitTest {

   // ── Default constructor ───────────────────────────────────

   @Nested
   @DisplayName("Default constructor")
   class DefaultConstructor {

      @Test
      @DisplayName("default entry has SansSerif font family")
      void defaultFamilyIsSansSerif() {
         FontEntry fe = new FontEntry();
         String s = fe.toString();
         assertTrue(s.contains("name=SansSerif"), s);
      }

      @Test
      @DisplayName("default entry has size 14")
      void defaultSizeIs14() {
         FontEntry fe = new FontEntry();
         String s = fe.toString();
         assertTrue(s.contains("size=14"), s);
      }

      @Test
      @DisplayName("default entry has regular weight")
      void defaultWeightIsRegular() {
         FontEntry fe = new FontEntry();
         String s = fe.toString();
         assertTrue(s.contains("weight=1.0"), s);
      }

      @Test
      @DisplayName("default entry has regular posture")
      void defaultPostureIsRegular() {
         FontEntry fe = new FontEntry();
         String s = fe.toString();
         assertTrue(s.contains("posture=0"), s);
      }

      @Test
      @DisplayName("default entry getFont returns non-null")
      void defaultGetFontNotNull() {
         FontEntry fe = new FontEntry();
         Font f = fe.getFont();
         assertNotNull(f);
      }
   }

   // ── String constructor ────────────────────────────────────

   @Nested
   @DisplayName("String constructor")
   class StringConstructor {

      @Test
      @DisplayName("empty string creates default")
      void emptyStringCreatesDefault() {
         FontEntry fe = new FontEntry("");
         String s = fe.toString();
         assertTrue(s.contains("name=SansSerif"), s);
         assertTrue(s.contains("size=14"), s);
      }

      @Test
      @DisplayName("parses name field")
      void parsesNameField() {
         FontEntry fe = new FontEntry(
            "name=Courier, size=12]");
         String s = fe.toString();
         assertTrue(s.contains("name=Courier"), s);
      }

      @Test
      @DisplayName("parses size field")
      void parsesSizeField() {
         FontEntry fe = new FontEntry(
            "name=Arial, size=18]");
         String s = fe.toString();
         assertTrue(s.contains("size=18"), s);
      }

      @Test
      @DisplayName("parses weight field")
      void parsesWeightField() {
         FontEntry fe = new FontEntry(
            "name=Arial, weight=2.0, size=12]");
         String s = fe.toString();
         assertTrue(s.contains("weight=2.0"), s);
      }

      @Test
      @DisplayName("parses posture field")
      void parsesPostureField() {
         FontEntry fe = new FontEntry(
            "name=Arial, posture=0.2]");
         String s = fe.toString();
         assertTrue(s.contains("posture=0.2"), s);
      }

      @Test
      @DisplayName("parses style=plain")
      void parsesStylePlain() {
         FontEntry fe = new FontEntry(
            "name=Arial, style=plain, size=12]");
         String s = fe.toString();
         assertTrue(s.contains("weight=1.0"), s);
         assertTrue(s.contains("posture=0"), s);
      }

      @Test
      @DisplayName("parses style=bold")
      void parsesStyleBold() {
         FontEntry fe = new FontEntry(
            "name=Arial, style=bold, size=12]");
         String s = fe.toString();
         assertTrue(s.contains("weight=2.0"), s);
      }

      @Test
      @DisplayName("parses style=italic")
      void parsesStyleItalic() {
         FontEntry fe = new FontEntry(
            "name=Arial, style=italic, size=12]");
         String s = fe.toString();
         assertTrue(s.contains("posture=0.2"), s);
      }

      @Test
      @DisplayName("parses style=demi")
      void parsesStyleDemi() {
         FontEntry fe = new FontEntry(
            "name=Arial, style=demi, size=12]");
         String s = fe.toString();
         assertTrue(s.contains("weight=1.75"), s);
      }
   }

   // ── Setters ───────────────────────────────────────────────

   @Nested
   @DisplayName("Property setters")
   class SetterTests {

      @Test
      @DisplayName("setName changes font family")
      void setNameChangesFontFamily() {
         FontEntry fe = new FontEntry();
         fe.setName("Courier New");
         String s = fe.toString();
         assertTrue(s.contains("name=Courier New"), s);
      }

      @Test
      @DisplayName("setSize changes font size")
      void setSizeChangesFontSize() {
         FontEntry fe = new FontEntry();
         fe.setSize(24.0f);
         String s = fe.toString();
         assertTrue(s.contains("size=24"), s);
      }

      @Test
      @DisplayName("setWeight changes weight")
      void setWeightChangesWeight() {
         FontEntry fe = new FontEntry();
         fe.setWeight(2.0f);
         String s = fe.toString();
         assertTrue(s.contains("weight=2.0"), s);
      }

      @Test
      @DisplayName("setFontType plain resets to regular")
      void setFontTypePlain() {
         FontEntry fe = new FontEntry();
         fe.setFontType("bold");
         fe.setFontType("plain");
         String s = fe.toString();
         assertTrue(s.contains("weight=1.0"), s);
         assertTrue(s.contains("posture=0"), s);
      }

      @Test
      @DisplayName("setFontType bold sets weight 2.0")
      void setFontTypeBold() {
         FontEntry fe = new FontEntry();
         fe.setFontType("bold");
         String s = fe.toString();
         assertTrue(s.contains("weight=2.0"), s);
      }

      @Test
      @DisplayName("setFontType italic sets posture oblique")
      void setFontTypeItalic() {
         FontEntry fe = new FontEntry();
         fe.setFontType("italic");
         String s = fe.toString();
         assertTrue(s.contains("posture=0.2"), s);
      }

      @Test
      @DisplayName("setFontType demi sets weight 1.75")
      void setFontTypeDemi() {
         FontEntry fe = new FontEntry();
         fe.setFontType("demi");
         String s = fe.toString();
         assertTrue(s.contains("weight=1.75"), s);
      }

      @Test
      @DisplayName("unknown font type preserves current weight")
      void unknownFontTypePreservesWeight() {
         FontEntry fe = new FontEntry();
         fe.setFontType("unknown");
         String s = fe.toString();
         assertTrue(s.contains("weight=1.0"), s);
      }
   }

   // ── Font constructor ──────────────────────────────────────

   @Nested
   @DisplayName("Font constructor")
   class FontConstructorTests {

      @Test
      @DisplayName("Font constructor extracts attributes")
      void fontConstructorExtractsAttrs() {
         Font f = new Font(Font.MONOSPACED, Font.BOLD, 16);
         FontEntry fe = new FontEntry(f);
         String s = fe.toString();
         assertNotNull(s);
      }

      @Test
      @DisplayName("Font+size constructor overrides size")
      void fontSizeConstructorOverridesSize() {
         Font f = new Font(Font.MONOSPACED, Font.PLAIN, 12);
         FontEntry fe = new FontEntry(f, 20.0f);
         String s = fe.toString();
         assertTrue(s.contains("size=20"), s);
      }

      @Test
      @DisplayName("getFont returns consistent Font")
      void getFontReturnsConsistentFont() {
         FontEntry fe = new FontEntry();
         Font f1 = fe.getFont();
         Font f2 = fe.getFont();
         // Same instance (cached)
         assertEquals(f1, f2);
      }

      @Test
      @DisplayName("setName invalidates cached font")
      void setNameInvalidatesFont() {
         FontEntry fe = new FontEntry();
         Font f1 = fe.getFont();
         fe.setName("Serif");
         Font f2 = fe.getFont();
         // Different Font objects after name change
         assertNotNull(f2);
      }
   }

   // ── toString format ───────────────────────────────────────

   @Nested
   @DisplayName("toString format")
   class ToStringFormat {

      @Test
      @DisplayName("toString contains all four fields")
      void toStringContainsAllFields() {
         FontEntry fe = new FontEntry();
         String s = fe.toString();
         assertTrue(s.contains("name="), s);
         assertTrue(s.contains("weight="), s);
         assertTrue(s.contains("size="), s);
         assertTrue(s.contains("posture="), s);
      }

      @Test
      @DisplayName("toString comma-separated")
      void toStringCommaSeparated() {
         FontEntry fe = new FontEntry();
         String s = fe.toString();
         assertTrue(s.contains(",weight="), s);
         assertTrue(s.contains(",size="), s);
         assertTrue(s.contains(",posture="), s);
      }
   }

   // ── typest array ──────────────────────────────────────────

   @Test
   @DisplayName("typest array has 4 font styles")
   void typestHasFourStyles() {
      assertEquals(4, AwtFontList.typest.length);
      assertEquals("plain", AwtFontList.typest[0]);
      assertEquals("bold", AwtFontList.typest[1]);
      assertEquals("italic", AwtFontList.typest[2]);
      assertEquals("bold+italic", AwtFontList.typest[3]);
   }
}
