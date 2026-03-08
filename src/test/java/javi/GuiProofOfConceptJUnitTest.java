package javi;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proof-of-concept AssertJ Swing GUI test for T3.
 *
 * <p>Disabled by default because CI runs headless (no display).
 * Run locally with a display to validate AssertJ Swing wiring.</p>
 *
 * <p>To enable: remove {@code @Disabled} and run from a graphical
 * desktop/VNC session.</p>
 */
@Disabled("T3: requires graphical display — headless CI skip")
class GuiProofOfConceptJUnitTest {

   @Test
   void assertJSwingDependencyAvailable() {
      // Verify the AssertJ Swing classes are on the classpath
      try {
         Class.forName("org.assertj.swing.core.BasicRobot");
         Class.forName("org.assertj.swing.fixture.FrameFixture");
      } catch (ClassNotFoundException e) {
         fail("AssertJ Swing not on classpath: " + e.getMessage());
      }
   }

   @Test
   void canCreateRobotInstance() {
      // This requires a real display — disabled in headless CI
      org.assertj.swing.core.Robot robot =
            org.assertj.swing.core.BasicRobot.robotWithCurrentAwtHierarchy();
      assertNotNull(robot);
      robot.cleanUp();
   }
}
