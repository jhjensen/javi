package javi;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;

import javi.awt.IconUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link IconUtil} icon generation.
 */
class IconUtilJUnitTest {

   @Test
   @DisplayName("createJaviIcon(128) produces 128x128 image")
   void icon128HasCorrectSize() {
      BufferedImage img = IconUtil.createJaviIcon(128);
      assertNotNull(img);
      assertEquals(128, img.getWidth());
      assertEquals(128, img.getHeight());
   }

   @Test
   @DisplayName("createJaviIcon(512) produces 512x512 image")
   void icon512HasCorrectSize() {
      BufferedImage img = IconUtil.createJaviIcon(512);
      assertNotNull(img);
      assertEquals(512, img.getWidth());
      assertEquals(512, img.getHeight());
   }

   @Test
   @DisplayName("createJaviIcon(16) works at small size")
   void icon16HasCorrectSize() {
      BufferedImage img = IconUtil.createJaviIcon(16);
      assertNotNull(img);
      assertEquals(16, img.getWidth());
      assertEquals(16, img.getHeight());
   }

   @Test
   @DisplayName("default icon is 128x128")
   void defaultIconIs128() {
      BufferedImage img = IconUtil.createJaviIcon();
      assertEquals(128, img.getWidth());
      assertEquals(128, img.getHeight());
   }

   @Test
   @DisplayName("icon list contains 6 standard sizes")
   void iconListHasSixSizes() {
      List<Image> icons = IconUtil.createJaviIcons();
      assertEquals(6, icons.size());
   }

   @Test
   @DisplayName("icon has reddish-orange background")
   void iconBackgroundIsOrangeRed() {
      BufferedImage img = IconUtil.createJaviIcon(128);
      int cornerRgb = img.getRGB(64, 4) & 0x00FFFFFF;
      // Background is reddish-orange — red channel should be > 150
      int red = (cornerRgb >> 16) & 0xFF;
      assertTrue(red > 150,
         "background red channel should be > 150, got " + red);
   }

   @Test
   @DisplayName("icon has non-trivial content (not all one color)")
   void iconHasContent() {
      BufferedImage img = IconUtil.createJaviIcon(128);
      int bg = img.getRGB(5, 5);
      int center = img.getRGB(64, 50);
      assertTrue(bg != center,
         "background and center should differ (cup vs background)");
   }

   @Test
   @DisplayName("icon contains orange pixels for saucer/text")
   void iconContainsOrangePixels() {
      BufferedImage img = IconUtil.createJaviIcon(128);
      // Scan the lower half for orange-ish pixels
      boolean foundOrange = false;
      for (int y = 64; y < 128 && !foundOrange; y++) {
         for (int x = 30; x < 90; x++) {
            Color c = new Color(img.getRGB(x, y), true);
            if (c.getAlpha() > 200
                  && c.getRed() > 170
                  && c.getGreen() < 150
                  && c.getBlue() < 80) {
               foundOrange = true;
               break;
            }
         }
      }
      assertTrue(foundOrange,
         "icon should contain orange pixels for stem/saucer");
   }

   @Test
   @DisplayName("badge overlay area (top-right) retains icon content")
   void badgeAreaRetainsIconContent() {
      BufferedImage img = IconUtil.createJaviIcon(128);
      // The badge sits in the system-managed overlay; verify the
      // icon itself has content in the top-right quadrant.
      int topRightPixel = img.getRGB(100, 10);
      int alpha = (topRightPixel >> 24) & 0xFF;
      assertTrue(alpha > 0,
         "top-right should have non-transparent icon content");
   }
}
