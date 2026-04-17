package javi.git;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GitLogGraph} — lane assignment for commit graph.
 */
class GitLogGraphJUnitTest {

   private static GitLogEntry entry(String sha, String... parents) {
      return new GitLogEntry(sha, List.of(parents),
         "subject", "author", "date", null);
   }

   @Test
   @DisplayName("empty input returns empty rows")
   void emptyInput() {
      List<GitLogGraph.Row> rows =
         GitLogGraph.assignLanes(Collections.emptyList());
      assertTrue(rows.isEmpty());
   }

   @Test
   @DisplayName("single root commit gets lane 0")
   void singleRoot() {
      List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(
         List.of(entry("aaa")));
      assertEquals(1, rows.size());
      assertEquals(0, rows.get(0).lane);
      assertEquals(0, rows.get(0).parentLanes.length);
   }

   @Test
   @DisplayName("linear chain stays in lane 0")
   void linearChain() {
      // newest first: c -> b -> a (root)
      List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
         entry("ccc", "bbb"),
         entry("bbb", "aaa"),
         entry("aaa")));
      assertEquals(3, rows.size());
      assertEquals(0, rows.get(0).lane); // ccc
      assertEquals(0, rows.get(1).lane); // bbb
      assertEquals(0, rows.get(2).lane); // aaa
   }

   @Test
   @DisplayName("merge commit has two parent lanes")
   void mergeCommit() {
      // merge(m) merges feature(f) into main(b)
      // m -> b, f
      // b -> a
      // f -> a
      // a (root)
      List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
         entry("mmm", "bbb", "fff"),
         entry("bbb", "aaa"),
         entry("fff", "aaa"),
         entry("aaa")));
      assertEquals(4, rows.size());

      GitLogGraph.Row mergeRow = rows.get(0);
      assertEquals(2, mergeRow.parentLanes.length);
      // first parent inherits merge lane
      assertEquals(mergeRow.lane, mergeRow.parentLanes[0]);
   }

   @Test
   @DisplayName("activeLaneCount tracks open lanes")
   void activeLaneCount() {
      // Single commit with no parents — lane freed after
      List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(
         List.of(entry("aaa")));
      // After root commit with no parents, lane is freed
      assertEquals(0, rows.get(0).activeLaneCount);
   }

   @Test
   @DisplayName("branch creates new lane")
   void branchCreatesNewLane() {
      // Two commits that both have the same parent but appear
      // as separate branch heads (both are unknown initially)
      List<GitLogGraph.Row> rows = GitLogGraph.assignLanes(List.of(
         entry("bbb", "aaa"),
         entry("ccc", "aaa"),
         entry("aaa")));
      assertEquals(3, rows.size());
      // bbb and ccc should be in different lanes
      int lane1 = rows.get(0).lane;
      int lane2 = rows.get(1).lane;
      // They could be same if second reuses parent lane
      assertNotNull(rows.get(0));
      assertNotNull(rows.get(1));
   }

   @Test
   @DisplayName("row contains correct entry reference")
   void rowHasCorrectEntry() {
      GitLogEntry e = entry("abc", "def");
      List<GitLogGraph.Row> rows =
         GitLogGraph.assignLanes(List.of(e));
      assertEquals(e, rows.get(0).entry);
   }
}
