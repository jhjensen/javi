package javi.awt;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AwtFontList} — static constants and the
 * default mono font name selection based on OS.
 */
class AwtFontListJUnitTest {

   // ── typest array ──────────────────────────────────────────

   @Nested
   @DisplayName("typest constants")
   class TypestConstants {

      @Test
      @DisplayName("typest has four entries")
      void typestHasFourEntries() {
         assertEquals(4, AwtFontList.typest.length);
      }

      @Test
      @DisplayName("typest contains expected font types")
      void typestContainsExpectedTypes() {
         assertArrayEquals(
            new String[]{"plain", "bold", "italic", "bold+italic"},
            AwtFontList.typest);
      }

      @Test
      @DisplayName("typest entries are non-null")
      void typestEntriesNotNull() {
         for (String t : AwtFontList.typest)
            assertNotNull(t, "typest entry should not be null");
      }
   }

   // ── defaultMonoName ───────────────────────────────────────

   @Nested
   @DisplayName("defaultMonoName")
   class DefaultMonoName {

      @Test
      @DisplayName("returns a non-empty platform font name")
      void returnsNonEmptyName() throws Exception {
         Method m = AwtFontList.class
            .getDeclaredMethod("defaultMonoName");
         m.setAccessible(true);
         String name = (String) m.invoke(null);
         assertNotNull(name);
         assertTrue(name.length() > 0,
            "default mono name should not be empty");
      }

      @Test
      @DisplayName("returns Menlo on macOS")
      void returnsMenloOnMac() throws Exception {
         String os = System.getProperty("os.name", "")
            .toLowerCase();
         if (!os.contains("mac"))
            return; // skip on non-Mac
         Method m = AwtFontList.class
            .getDeclaredMethod("defaultMonoName");
         m.setAccessible(true);
         String name = (String) m.invoke(null);
         assertEquals("Menlo", name);
      }
   }
}
