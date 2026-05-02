package javi.awt;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that text rendering hints are applied to Graphics2D contexts.
 * Tests the applyTextRenderingHints() method extracted from OldView.
 */
class TextRenderingHintsJUnitTest {

   @Test
   @DisplayName("applyTextRenderingHints sets text antialiasing on Graphics2D")
   void hintsAppliedToGraphics2D() {
      BufferedImage img = new BufferedImage(
         100, 30, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      try {
         // Before applying hints, TEXT_ANTIALIASING is default
         Object before = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);
         assertTrue(
            before == null
               || before == RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT,
            "before applyTextRenderingHints, AA should be default");

         OldView.applyTextRenderingHints(g);

         // After applying hints, TEXT_ANTIALIASING must not be DEFAULT or OFF
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
      BufferedImage img = new BufferedImage(
         100, 30, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      try {
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
   @DisplayName("hint value is one of the known antialiasing modes")
   void hintValueIsKnownAAMode() {
      BufferedImage img = new BufferedImage(
         100, 30, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      try {
         OldView.applyTextRenderingHints(g);
         Object hint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);

         // Must be one of the active antialiasing modes
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
   @DisplayName("antialiasEnabled=false sets TEXT_ANTIALIAS_OFF")
   void disabledSetsOff() {
      BufferedImage img = new BufferedImage(
         100, 30, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      boolean saved = OldView.antialiasEnabled;
      try {
         OldView.antialiasEnabled = false;
         OldView.applyTextRenderingHints(g);
         Object hint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);
         assertTrue(
            hint == RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
            "when disabled, hint should be OFF, got: " + hint);
      } finally {
         OldView.antialiasEnabled = saved;
         g.dispose();
      }
   }

   @Test
   @DisplayName("toggling antialiasEnabled changes hint applied")
   void toggleChangesHint() {
      BufferedImage img = new BufferedImage(
         100, 30, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      boolean saved = OldView.antialiasEnabled;
      try {
         OldView.antialiasEnabled = true;
         OldView.applyTextRenderingHints(g);
         Object onHint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);

         OldView.antialiasEnabled = false;
         OldView.applyTextRenderingHints(g);
         Object offHint = g.getRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING);

         assertTrue(onHint != offHint,
            "on and off should produce different hints");
         assertTrue(
            offHint == RenderingHints.VALUE_TEXT_ANTIALIAS_OFF,
            "off hint should be OFF");
      } finally {
         OldView.antialiasEnabled = saved;
         g.dispose();
      }
   }
}
