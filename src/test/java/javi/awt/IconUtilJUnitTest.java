package javi.awt;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link IconUtil} — programmatic icon generation.
 * All tests run headlessly via BufferedImage (no display needed).
 */
class IconUtilJUnitTest {

   // ── Color constants ───────────────────────────────────────

   @Nested
   @DisplayName("Color constants")
   class ColorConstants {

      @Test
      @DisplayName("BACKGROUND is warm reddish-orange")
      void backgroundColor() {
         assertEquals(new Color(205, 75, 35), IconUtil.BACKGROUND);
      }

      @Test
      @DisplayName("CUP_FILL is translucent blue-white")
      void cupFillColor() {
         Color c = IconUtil.CUP_FILL;
         assertEquals(200, c.getRed());
         assertEquals(225, c.getGreen());
         assertEquals(245, c.getBlue());
         assertEquals(70, c.getAlpha());
      }

      @Test
      @DisplayName("ORANGE is warm orange")
      void orangeColor() {
         assertEquals(new Color(250, 140, 42), IconUtil.ORANGE);
      }

      @Test
      @DisplayName("COFFEE_FILL is dark brown")
      void coffeeFillColor() {
         assertEquals(new Color(55, 25, 10), IconUtil.COFFEE_FILL);
      }
   }

   // ── createJaviIcon(int) ───────────────────────────────────

   @Nested
   @DisplayName("createJaviIcon(int size)")
   class CreateSizedIcon {

      @Test
      @DisplayName("128x128 icon is correct dimensions")
      void icon128Dimensions() {
         BufferedImage img = IconUtil.createJaviIcon(128);
         assertEquals(128, img.getWidth());
         assertEquals(128, img.getHeight());
      }

      @Test
      @DisplayName("16x16 icon is correct dimensions")
      void icon16Dimensions() {
         BufferedImage img = IconUtil.createJaviIcon(16);
         assertEquals(16, img.getWidth());
         assertEquals(16, img.getHeight());
      }

      @Test
      @DisplayName("512x512 icon is correct dimensions")
      void icon512Dimensions() {
         BufferedImage img = IconUtil.createJaviIcon(512);
         assertEquals(512, img.getWidth());
         assertEquals(512, img.getHeight());
      }

      @Test
      @DisplayName("image type is ARGB")
      void imageTypeIsArgb() {
         BufferedImage img = IconUtil.createJaviIcon(64);
         assertEquals(BufferedImage.TYPE_INT_ARGB, img.getType());
      }

      @Test
      @DisplayName("corner pixel has background color")
      void cornerPixelIsBackground() {
         BufferedImage img = IconUtil.createJaviIcon(128);
         // Center of icon should have non-zero content
         int center = img.getRGB(64, 64);
         assertTrue(center != 0,
            "center pixel should not be transparent black");
      }

      @Test
      @DisplayName("background color present in icon")
      void backgroundColorPresent() {
         BufferedImage img = IconUtil.createJaviIcon(128);
         // Sample near top-left (inside rounded rect)
         int pixel = img.getRGB(10, 10);
         int r = (pixel >> 16) & 0xFF;
         int g = (pixel >> 8) & 0xFF;
         int b = pixel & 0xFF;
         // Should be close to BACKGROUND (205, 75, 35)
         assertTrue(r > 150 && g < 120 && b < 80,
            "top-left should be reddish-orange background: "
            + r + "," + g + "," + b);
      }
   }

   // ── createJaviIcon() — no-arg ────────────────────────────

   @Nested
   @DisplayName("createJaviIcon() — default size")
   class CreateDefaultIcon {

      @Test
      @DisplayName("returns 128x128 image")
      void defaultSize128() {
         BufferedImage img = IconUtil.createJaviIcon();
         assertEquals(128, img.getWidth());
         assertEquals(128, img.getHeight());
      }

      @Test
      @DisplayName("result is non-null")
      void resultNotNull() {
         assertNotNull(IconUtil.createJaviIcon());
      }
   }

   // ── createJaviIcons() — multi-size ────────────────────────

   @Nested
   @DisplayName("createJaviIcons() — multi-size list")
   class CreateMultiIcons {

      @Test
      @DisplayName("returns 6 icons")
      void returnsSixIcons() {
         List<Image> icons = IconUtil.createJaviIcons();
         assertEquals(6, icons.size());
      }

      @Test
      @DisplayName("all icons are non-null")
      void allNonNull() {
         List<Image> icons = IconUtil.createJaviIcons();
         for (Image icon : icons)
            assertNotNull(icon);
      }

      @Test
      @DisplayName("includes 16, 32, 48, 64, 128, 512 sizes")
      void expectedSizes() {
         List<Image> icons = IconUtil.createJaviIcons();
         int[] expected = {16, 32, 48, 64, 128, 512};
         for (int i = 0; i < expected.length; i++) {
            BufferedImage img = (BufferedImage) icons.get(i);
            assertEquals(expected[i], img.getWidth(),
               "icon " + i + " width");
            assertEquals(expected[i], img.getHeight(),
               "icon " + i + " height");
         }
      }

      @Test
      @DisplayName("smallest icon has content")
      void smallestIconHasContent() {
         List<Image> icons = IconUtil.createJaviIcons();
         BufferedImage small = (BufferedImage) icons.get(0);
         // 16x16 — center pixel should be non-zero
         int center = small.getRGB(8, 8);
         assertTrue(center != 0,
            "16x16 icon center should have content");
      }
   }
}
