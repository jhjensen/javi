package javi.awt;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that text rendering hints are applied to Graphics2D contexts.
 * Tests the applyTextRenderingHints() method in OldView.
 */
class TextRenderingHintsJUnitTest {

   private String savedMode;
   private int savedContrast;

   @BeforeEach
   void saveState() {
      savedMode = OldView.antialiasMode;
      savedContrast = OldView.lcdContrast;
   }

   @AfterEach
   void restoreState() {
      OldView.antialiasMode = savedMode;
      OldView.lcdContrast = savedContrast;
   }

   private Graphics2D createTestGraphics() {
      BufferedImage img = new BufferedImage(
         100, 30, BufferedImage.TYPE_INT_RGB);
      return img.createGraphics();
   }

   @Test
   @DisplayName("mode=on sets text antialiasing on Graphics2D")
   void hintsAppliedToGraphics2D() {
      Graphics2D g = createTestGraphics();
      try {
         OldView.antialiasMode = "on";
         OldView.applyTextRenderingHints(g);

         Object after = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);
         assertNotNull(after,
            "text antialiasing hint should be set after apply");
         assertTrue(
            after != RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT
               && after != RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
            "text antialiasing should be enabled (got " + after + ")");
      } finally {
         g.dispose();
      }
   }

   @Test
   @DisplayName("applyTextRenderingHints is idempotent")
   void hintsIdempotent() {
      Graphics2D g = createTestGraphics();
      try {
         OldView.antialiasMode = "on";
         OldView.applyTextRenderingHints(g);
         Object first = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);

         OldView.applyTextRenderingHints(g);
         Object second = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);

         assertTrue(first == second,
            "applying hints twice should yield same result");
      } finally {
         g.dispose();
      }
   }

   @Test
   @DisplayName("mode=on produces a known active AA mode")
   void hintValueIsKnownAAMode() {
      Graphics2D g = createTestGraphics();
      try {
         OldView.antialiasMode = "on";
         OldView.applyTextRenderingHints(g);
         Object hint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);

         assertTrue(
            hint == RenderingHints.VALUE_TEXT_ANTIALIAS_ON
               || hint == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB
               || hint == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HBGR
               || hint == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VRGB
               || hint == RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_VBGR
               || hint == RenderingHints.VALUE_TEXT_ANTIALIAS_GASP,
            "hint should be an active AA mode, got: " + hint);
      } finally {
         g.dispose();
      }
   }

   @Test
   @DisplayName("mode=off sets TEXT_ANTIALIAS_OFF")
   void disabledSetsOff() {
      Graphics2D g = createTestGraphics();
      try {
         OldView.antialiasMode = "off";
         OldView.applyTextRenderingHints(g);
         Object hint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);
         assertTrue(
            hint == RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
            "when off, hint should be OFF, got: " + hint);
      } finally {
         g.dispose();
      }
   }

   @Test
   @DisplayName("toggling mode changes hint applied")
   void toggleChangesHint() {
      Graphics2D g = createTestGraphics();
      try {
         OldView.antialiasMode = "on";
         OldView.applyTextRenderingHints(g);
         Object onHint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);

         OldView.antialiasMode = "off";
         OldView.applyTextRenderingHints(g);
         Object offHint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);

         assertTrue(onHint != offHint,
            "on and off should produce different hints");
         assertTrue(
            offHint == RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
            "off hint should be OFF");
      } finally {
         g.dispose();
      }
   }

   @Test
   @DisplayName("mode=lcd forces LCD_HRGB")
   void lcdModeForcesSublixel() {
      Graphics2D g = createTestGraphics();
      try {
         OldView.antialiasMode = "lcd";
         OldView.applyTextRenderingHints(g);
         Object hint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);
         assertEquals(RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, hint,
            "lcd mode should set LCD_HRGB");
      } finally {
         g.dispose();
      }
   }

   @Test
   @DisplayName("mode=grayscale forces VALUE_TEXT_ANTIALIAS_ON")
   void grayscaleModeForced() {
      Graphics2D g = createTestGraphics();
      try {
         OldView.antialiasMode = "grayscale";
         OldView.applyTextRenderingHints(g);
         Object hint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);
         assertEquals(RenderingHints.VALUE_TEXT_ANTIALIAS_ON, hint,
            "grayscale mode should set ANTIALIAS_ON");
      } finally {
         g.dispose();
      }
   }

   @Test
   @DisplayName("LCD contrast is applied when AA is active")
   void lcdContrastApplied() {
      Graphics2D g = createTestGraphics();
      try {
         OldView.antialiasMode = "lcd";
         OldView.lcdContrast = 200;
         OldView.applyTextRenderingHints(g);
         Object contrast = g.getRenderingHint(
            RenderingHints.KEY_TEXT_LCD_CONTRAST);
         assertEquals(200, contrast,
            "LCD contrast should be set to configured value");
      } finally {
         g.dispose();
      }
   }

   @Test
   @DisplayName("LCD contrast not applied when AA is off")
   void lcdContrastNotAppliedWhenOff() {
      Graphics2D g = createTestGraphics();
      try {
         OldView.antialiasMode = "off";
         OldView.lcdContrast = 200;
         OldView.applyTextRenderingHints(g);
         Object contrast = g.getRenderingHint(
            RenderingHints.KEY_TEXT_LCD_CONTRAST);
         // When off, contrast hint should not be set (remains null/default)
         assertTrue(contrast == null || !Integer.valueOf(200).equals(contrast),
            "LCD contrast should not be applied when AA is off");
      } finally {
         g.dispose();
      }
   }

   @Test
   @DisplayName("changing lcdContrast value takes effect")
   void lcdContrastChange() {
      Graphics2D g = createTestGraphics();
      try {
         OldView.antialiasMode = "on";
         OldView.lcdContrast = 120;
         OldView.applyTextRenderingHints(g);
         Object c1 = g.getRenderingHint(
            RenderingHints.KEY_TEXT_LCD_CONTRAST);
         assertEquals(120, c1);

         OldView.lcdContrast = 220;
         OldView.applyTextRenderingHints(g);
         Object c2 = g.getRenderingHint(
            RenderingHints.KEY_TEXT_LCD_CONTRAST);
         assertEquals(220, c2);
      } finally {
         g.dispose();
      }
   }
}
