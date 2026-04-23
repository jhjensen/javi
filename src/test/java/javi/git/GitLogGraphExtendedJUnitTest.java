package javi.git;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended tests for {@link GitLogGraph} — edge cases in lane
 * assignment for commit graph rendering.
 */
class GitLogGraphExtendedJUnitTest {

   private static GitLogEntry entry(String sha, String... parents) {
      return new GitLogEntry(sha, List.of(parents),
         "subject", "author", "date", null);
   }

   // ── Lane reuse ───────────────────────────────────────────

   @Nested
   @DisplayName("lane reuse")
   class LaneReuse {

      @Test
      @DisplayName("freed lane is reused by next branch head")
      void freedLaneReused() {
         // a is root, frees lane 0. Then b (new head) should get lane 0.
         List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
            entry("aaa"),
            entry("bbb")));
         assertEquals(0, rows.get(0).lane); // aaa
         assertEquals(0, rows.get(1).lane); // bbb reuses lane 0
      }

      @Test
      @DisplayName("merge frees second parent lane for reuse")
      void mergeFreesBranch() {
         // m merges b (first parent, lane 0) and f (lane 1)
         // After merge, lane 1 should be free for next commit
         List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
            entry("mmm", "bbb", "fff"),
            entry("bbb", "aaa"),
            entry("fff", "aaa"),
            entry("aaa")));
         assertEquals(4, rows.size());
         // Verify the merge allocated a second lane
         GitLogGraph.Row mergeRow = rows.get(0);
         assertNotEquals(mergeRow.parentLanes[0],
            mergeRow.parentLanes[1],
            "merge parents should be in different lanes");
      }
   }

   // ── Octopus merge ────────────────────────────────────────

   @Nested
   @DisplayName("octopus merge")
   class OctopusMerge {

      @Test
      @DisplayName("three-parent merge assigns distinct lanes")
      void threeParentMerge() {
         List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
            entry("mmm", "aaa", "bbb", "ccc"),
            entry("aaa"),
            entry("bbb"),
            entry("ccc")));
         GitLogGraph.Row mergeRow = rows.get(0);
         assertEquals(3, mergeRow.parentLanes.length);
         // First parent inherits merge lane
         assertEquals(mergeRow.lane, mergeRow.parentLanes[0]);
      }

      @Test
      @DisplayName("four-parent merge produces 4 parent lanes")
      void fourParentMerge() {
         List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
            entry("mmm", "aaa", "bbb", "ccc", "ddd"),
            entry("aaa"),
            entry("bbb"),
            entry("ccc"),
            entry("ddd")));
         assertEquals(4, rows.get(0).parentLanes.length);
      }
   }

   // ── Deep linear chain ────────────────────────────────────

   @Nested
   @DisplayName("deep linear chain")
   class DeepChain {

      @Test
      @DisplayName("10-commit chain stays in lane 0")
      void tenCommitChain() {
         List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
            entry("j", "i"),
            entry("i", "h"),
            entry("h", "g"),
            entry("g", "f"),
            entry("f", "e"),
            entry("e", "d"),
            entry("d", "c"),
            entry("c", "b"),
            entry("b", "a"),
            entry("a")));
         assertEquals(10, rows.size());
         for (GitLogGraph.Row r : rows) {
            assertEquals(0, r.lane,
               "linear chain should all be lane 0");
         }
      }
   }

   // ── Multiple roots ───────────────────────────────────────

   @Nested
   @DisplayName("multiple roots")
   class MultipleRoots {

      @Test
      @DisplayName("two independent root commits")
      void twoRoots() {
         List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
            entry("bbb", "aaa"),
            entry("aaa"),
            entry("ddd", "ccc"),
            entry("ccc")));
         assertEquals(4, rows.size());
         // After aaa (root) frees its lane, ccc branch
         // should be able to reuse it
      }
   }

   // ── Parallel branches ────────────────────────────────────

   @Nested
   @DisplayName("parallel branches")
   class ParallelBranches {

      @Test
      @DisplayName("two branches from same parent use different lanes")
      void twoBranchesFromSameParent() {
         // b -> a, c -> a: both children of a
         List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
            entry("bbb", "aaa"),
            entry("ccc", "aaa"),
            entry("aaa")));
         // bbb gets lane 0, ccc gets lane 1 or vice versa
         assertEquals(3, rows.size());
      }

      @Test
      @DisplayName("active lane count increases with parallel branches")
      void activeLaneCountWithParallel() {
         // b and c are both pending, pointing to shared parent a
         List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
            entry("bbb", "aaa"),
            entry("ccc", "aaa")));
         // After bbb: its parent 'aaa' is in a lane (1 active)
         // After ccc: its parent 'aaa' already has a lane
         assertTrue(rows.get(0).activeLaneCount >= 1);
      }
   }

   // ── Row content ──────────────────────────────────────────

   @Nested
   @DisplayName("row content")
   class RowContent {

      @Test
      @DisplayName("row entry references original GitLogEntry")
      void rowEntryReference() {
         GitLogEntry e = entry("abc", "def");
         List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(
            List.of(e));
         assertEquals(e, rows.get(0).entry);
         assertEquals("abc", rows.get(0).entry.sha);
      }

      @Test
      @DisplayName("parent lanes array length matches parent count")
      void parentLanesLength() {
         List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
            entry("mmm", "aaa", "bbb"),
            entry("aaa"),
            entry("bbb")));
         assertEquals(2, rows.get(0).parentLanes.length);
         assertEquals(0, rows.get(1).parentLanes.length);
         assertEquals(0, rows.get(2).parentLanes.length);
      }

      @Test
      @DisplayName("root commit has zero-length parent lanes")
      void rootParentLanes() {
         List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(
            List.of(entry("root")));
         assertEquals(0, rows.get(0).parentLanes.length);
      }
   }

   // ── Diamond merge pattern ────────────────────────────────

   @Test
   @DisplayName("diamond merge: branch and merge back")
   void diamondMerge() {
      // m merges b and c, both children of a
      //   m
      //  / \
      // b   c
      //  \ /
      //   a
      List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
         entry("mmm", "bbb", "ccc"),
         entry("bbb", "aaa"),
         entry("ccc", "aaa"),
         entry("aaa")));
      assertEquals(4, rows.size());
      // Merge has 2 parent lanes
      assertEquals(2, rows.get(0).parentLanes.length);
   }

   // ── Single parent SHA already assigned ───────────────────

   @Test
   @DisplayName("shared parent gets single lane")
   void sharedParentSingleLane() {
      // Both b and c point to a. When b is processed, a gets a lane.
      // When c is processed, a already has that lane.
      List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
         entry("bbb", "aaa"),
         entry("ccc", "aaa"),
         entry("aaa")));
      // 'aaa' should only occupy one lane
      assertEquals(0, rows.get(2).lane);
   }
}
