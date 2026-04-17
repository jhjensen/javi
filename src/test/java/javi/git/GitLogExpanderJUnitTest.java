package javi.git;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GitLogExpander} — expansion state tracking.
 *
 * <p>Tests the toggle/collapse/expand state machine and metadata.
 * Does NOT test {@code buildContent()} since it calls
 * {@code GitProcess.execute()}.</p>
 */
class GitLogExpanderJUnitTest {

   private GitLogExpander expander;

   @BeforeEach
   void setUp() {
      expander = new GitLogExpander();
   }

   // ── toggle ───────────────────────────────────────────────

   @Nested
   @DisplayName("toggle()")
   class Toggle {

      @Test
      @DisplayName("collapsed to stat (level 1)")
      void collapsedToStat() {
         assertEquals(1, expander.toggle("abc1234"));
      }

      @Test
      @DisplayName("stat to diff (level 2)")
      void statToDiff() {
         expander.toggle("abc1234"); // -> 1
         assertEquals(2, expander.toggle("abc1234"));
      }

      @Test
      @DisplayName("diff to collapsed (level 0)")
      void diffToCollapsed() {
         expander.toggle("abc1234"); // -> 1
         expander.toggle("abc1234"); // -> 2
         assertEquals(0, expander.toggle("abc1234"));
      }

      @Test
      @DisplayName("independent SHAs track independently")
      void independentShas() {
         expander.toggle("aaa"); // -> 1
         expander.toggle("bbb"); // -> 1
         expander.toggle("aaa"); // -> 2
         assertEquals(2, expander.getLevel("aaa"));
         assertEquals(1, expander.getLevel("bbb"));
      }
   }

   // ── getLevel ─────────────────────────────────────────────

   @Nested
   @DisplayName("getLevel()")
   class GetLevel {

      @Test
      @DisplayName("unknown SHA returns 0")
      void unknownShaReturnsZero() {
         assertEquals(0, expander.getLevel("unknown"));
      }

      @Test
      @DisplayName("after toggle returns 1")
      void afterToggle() {
         expander.toggle("abc");
         assertEquals(1, expander.getLevel("abc"));
      }
   }

   // ── collapseAll ──────────────────────────────────────────

   @Test
   @DisplayName("collapseAll clears all expansions")
   void collapseAllClearsAll() {
      expander.toggle("aaa");
      expander.toggle("bbb");
      assertTrue(expander.hasExpansions());
      expander.collapseAll();
      assertFalse(expander.hasExpansions());
      assertEquals(0, expander.getLevel("aaa"));
   }

   // ── expandAll ────────────────────────────────────────────

   @Test
   @DisplayName("expandAll sets SHAs found in lines to level 2")
   void expandAllSetsLevel2() {
      // Lines with graph format SHAs
      expander.expandAll(java.util.List.of(
         "* abc1234 Fix bug",
         "* def5678 Add feature",
         "| |")); // no SHA
      assertEquals(2, expander.getLevel("abc1234"));
      assertEquals(2, expander.getLevel("def5678"));
      assertEquals(2, expander.expansionCount());
   }

   // ── hasExpansions / expansionCount ───────────────────────

   @Test
   @DisplayName("empty expander has no expansions")
   void emptyHasNone() {
      assertFalse(expander.hasExpansions());
      assertEquals(0, expander.expansionCount());
   }

   @Test
   @DisplayName("expansionCount tracks active expansions")
   void countTracksActive() {
      expander.toggle("a");
      expander.toggle("b");
      assertEquals(2, expander.expansionCount());
      // Toggle a through to collapsed
      expander.toggle("a"); // -> 2
      expander.toggle("a"); // -> 0
      assertEquals(1, expander.expansionCount());
   }
}
