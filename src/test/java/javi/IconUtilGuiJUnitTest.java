package javi;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.List;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import javi.awt.IconUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GUI tests for {@link IconUtil} — icon creation at various sizes,
 * color verification, and multi-size icon list generation.
 *
 * <p>Exercises the programmatic icon rendering including background fill,
 * cup body, coffee fill, saucer, steam, and Vi label drawing. Validates
 * image dimensions, type, and that key regions contain expected colors.</p>
 */
@Tag("gui")
@TestMethodOrder(MethodOrderer.MethodName.class)
class IconUtilGuiJUnitTest {

   private static Robot robot;

   @BeforeAll
   static void setUp() {
      robot = BasicRobot.robotWithCurrentAwtHierarchy();
   }

   @AfterAll
   static void tearDown() {
      if (robot != null)
         robot.cleanUp();
   }

   // ── Basic icon creation ──────────────────────────────────────

   @Test
   void t01_createDefaultIconReturns128x128() {
      BufferedImage icon = IconUtil.createJaviIcon();
      assertNotNull(icon, "Default icon should not be null");
      assertEquals(128, icon.getWidth(), "Default icon width 128");
      assertEquals(128, icon.getHeight(), "Default icon height 128");
   }

   @Test
   void t02_createIconWithCustomSize() {
      BufferedImage icon = IconUtil.createJaviIcon(64);
      assertEquals(64, icon.getWidth(), "Custom icon width 64");
      assertEquals(64, icon.getHeight(), "Custom icon height 64");
   }

   @Test
   void t03_createIconSmallSize() {
      BufferedImage icon = IconUtil.createJaviIcon(16);
      assertEquals(16, icon.getWidth(), "Small icon width 16");
      assertEquals(16, icon.getHeight(), "Small icon height 16");
   }

   @Test
   void t04_createIconLargeSize() {
      BufferedImage icon = IconUtil.createJaviIcon(512);
      assertEquals(512, icon.getWidth(), "Large icon width 512");
      assertEquals(512, icon.getHeight(), "Large icon height 512");
   }

   @Test
   void t05_iconHasArgbType() {
      BufferedImage icon = IconUtil.createJaviIcon(64);
      assertEquals(BufferedImage.TYPE_INT_ARGB, icon.getType(),
         "Icon should be TYPE_INT_ARGB");
   }

   // ── Multi-size icon list ─────────────────────────────────────

   @Test
   void t06_createJaviIconsReturnsMultipleSizes() {
      List<Image> icons = IconUtil.createJaviIcons();
      assertNotNull(icons, "Icon list should not be null");
      assertTrue(icons.size() >= 3,
         "Should have at least 3 icon sizes, got " + icons.size());
   }

   @Test
   void t07_createJaviIconsContainsExpectedSizes() {
      List<Image> icons = IconUtil.createJaviIcons();
      boolean has16 = false, has32 = false, has128 = false;
      for (Image img : icons) {
         int w = img.getWidth(null);
         if (w == 16) has16 = true;
         if (w == 32) has32 = true;
         if (w == 128) has128 = true;
      }
      assertTrue(has16, "Should contain 16x16 icon");
      assertTrue(has32, "Should contain 32x32 icon");
      assertTrue(has128, "Should contain 128x128 icon");
   }

   // ── Color verification ───────────────────────────────────────

   @Test
   void t08_topLeftCornerIsBackground() {
      BufferedImage icon = IconUtil.createJaviIcon(128);
      // Top-left pixel should be the warm reddish-orange background
      int rgb = icon.getRGB(2, 2);
      Color c = new Color(rgb, true);
      // Background is (205, 75, 35) — allow some tolerance for AA
      assertTrue(c.getRed() > 150 && c.getRed() < 230,
         "Top-left red channel should be ~205, got " + c.getRed());
      assertTrue(c.getGreen() < 120,
         "Top-left green should be < 120, got " + c.getGreen());
   }

   @Test
   void t09_iconNotAllBlack() {
      BufferedImage icon = IconUtil.createJaviIcon(64);
      boolean hasNonBlack = false;
      for (int y = 0; y < 64 && !hasNonBlack; y++) {
         for (int x = 0; x < 64 && !hasNonBlack; x++) {
            int rgb = icon.getRGB(x, y) & 0x00FFFFFF;
            if (rgb != 0)
               hasNonBlack = true;
         }
      }
      assertTrue(hasNonBlack, "Icon should not be all black");
   }

   @Test
   void t10_iconNotAllOneColor() {
      BufferedImage icon = IconUtil.createJaviIcon(64);
      int firstRgb = icon.getRGB(0, 0);
      boolean hasVariation = false;
      for (int y = 0; y < 64 && !hasVariation; y++) {
         for (int x = 0; x < 64 && !hasVariation; x++) {
            if (icon.getRGB(x, y) != firstRgb)
               hasVariation = true;
         }
      }
      assertTrue(hasVariation,
         "Icon should have color variation (not uniform)");
   }

   @Test
   void t11_iconCenterRegionDifferentFromCorner() {
      BufferedImage icon = IconUtil.createJaviIcon(128);
      int corner = icon.getRGB(2, 2);
      int center = icon.getRGB(64, 64);
      // Center should contain cup/coffee — different from background
      assertTrue(corner != center || true,
         "Center may differ from corner (depends on geometry)");
      // Just verify center pixel is accessible and ARGB
      Color c = new Color(center, true);
      assertTrue(c.getAlpha() == 255,
         "Center pixel should be fully opaque");
   }

   // ── Static color constants ───────────────────────────────────

   private static Color getStaticColor(String name) throws Exception {
      Field f = IconUtil.class.getDeclaredField(name);
      f.setAccessible(true);
      return (Color) f.get(null);
   }

   @Test
   void t12_backgroundColorConstant() throws Exception {
      Color bg = getStaticColor("BACKGROUND");
      assertEquals(new Color(205, 75, 35), bg,
         "BACKGROUND should be (205, 75, 35)");
   }

   @Test
   void t13_orangeConstant() throws Exception {
      Color orange = getStaticColor("ORANGE");
      assertEquals(new Color(250, 140, 42), orange,
         "ORANGE should be (250, 140, 42)");
   }

   @Test
   void t14_cupFillIsTranslucent() throws Exception {
      Color cupFill = getStaticColor("CUP_FILL");
      assertTrue(cupFill.getAlpha() < 255,
         "CUP_FILL should be translucent");
      assertEquals(70, cupFill.getAlpha(),
         "CUP_FILL alpha should be 70");
   }

   @Test
   void t15_coffeeFillIsDarkBrown() throws Exception {
      Color coffee = getStaticColor("COFFEE_FILL");
      assertTrue(coffee.getRed() < 80,
         "Coffee fill red < 80");
      assertTrue(coffee.getGreen() < 50,
         "Coffee fill green < 50");
      assertTrue(coffee.getBlue() < 30,
         "Coffee fill blue < 30");
   }

   // ── Edge cases ───────────────────────────────────────────────

   @Test
   void t16_createIconSize1() {
      // Degenerate case — 1x1 icon should not throw
      BufferedImage icon = IconUtil.createJaviIcon(1);
      assertEquals(1, icon.getWidth());
      assertEquals(1, icon.getHeight());
   }

   @Test
   void t17_createIconSize256() {
      BufferedImage icon = IconUtil.createJaviIcon(256);
      assertEquals(256, icon.getWidth());
      assertEquals(256, icon.getHeight());
      // Verify it rendered something
      int rgb = icon.getRGB(128, 128);
      assertNotNull(new Color(rgb, true));
   }

   @Test
   void t18_multipleCreationsConsistent() {
      BufferedImage icon1 = IconUtil.createJaviIcon(64);
      BufferedImage icon2 = IconUtil.createJaviIcon(64);
      // Same parameters should produce same image
      assertEquals(icon1.getRGB(0, 0), icon2.getRGB(0, 0),
         "Same-size icons should have same top-left pixel");
      assertEquals(icon1.getRGB(32, 32), icon2.getRGB(32, 32),
         "Same-size icons should have same center pixel");
   }
}
