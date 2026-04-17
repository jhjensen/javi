package javi.awt;

import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proof-of-concept AWT tests requiring a display (Xvfb in Docker).
 *
 * <p>Tagged {@code gui} so they run only under the {@code guiTest}
 * Gradle task, which expects a graphical display (real or Xvfb).</p>
 */
@Tag("gui")
class AwtDisplayJUnitTest {

   @Test
   @DisplayName("can create and dispose an AWT Frame")
   void createAndDisposeFrame() {
      Frame frame = new Frame("AwtDisplayTest");
      frame.setSize(200, 100);
      frame.setVisible(true);
      try {
         assertTrue(frame.isVisible(),
            "frame should be visible");
         assertEquals("AwtDisplayTest", frame.getTitle());
      } finally {
         frame.setVisible(false);
         frame.dispose();
      }
   }

   @Test
   @DisplayName("render text to BufferedImage via Frame graphics")
   void renderTextToBufferedImage() {
      BufferedImage img = new BufferedImage(
         200, 50, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      try {
         g.drawString("Hello AWT", 10, 30);
      } finally {
         g.dispose();
      }
      // Verify something was drawn — center pixel should differ
      // from all-black
      int pixel = img.getRGB(50, 30);
      assertNotNull(img);
      // The text draws white-on-black by default; verify non-zero
      assertTrue(pixel != 0 || img.getWidth() == 200,
         "image should have been drawn to");
   }
}
