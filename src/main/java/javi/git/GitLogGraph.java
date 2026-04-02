package javi.git;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compute lane assignments for a topologically-ordered commit list.
 *
 * <p>Each commit is assigned a column (lane) for rendering. Lanes
 * are allocated as new branches appear and freed when branches merge.
 * This produces the column layout needed for a SourceTree-style
 * graph display.</p>
 */
public final class GitLogGraph {

   /** Lane assignment for a single commit row. */
   public static final class Row {
      public final GitLogEntry entry;
      public final int lane;
      public final int[] parentLanes;
      public final int activeLaneCount;

      Row(GitLogEntry entry, int lane, int[] parentLanes,
            int activeLaneCount) {
         this.entry = entry;
         this.lane = lane;
         this.parentLanes = parentLanes;
         this.activeLaneCount = activeLaneCount;
      }
   }

   private GitLogGraph() {
   }

   /**
    * Assign lanes to each commit for graph rendering.
    *
    * <p>Walks the commit list (assumed topological order, newest first)
    * and assigns each commit to a lane column. Merge commits draw
    * lines from parent lanes to the commit lane.</p>
    *
    * @param entries topologically ordered commits (newest first)
    * @return list of Row objects with lane assignments
    */
   public static List<Row> assignLanes(List<GitLogEntry> entries) {
      ArrayList<Row> rows = new ArrayList<>(entries.size());
      // activeLanes: maps SHA -> lane index for commits we expect next
      // Initially empty; first commit gets lane 0.
      ArrayList<String> lanes = new ArrayList<>();
      Map<String, Integer> shaToLane = new HashMap<>();

      for (GitLogEntry entry : entries) {
         int myLane;
         Integer existing = shaToLane.remove(entry.sha);
         if (null != existing) {
            myLane = existing;
         } else {
            // New branch head — find a free lane
            myLane = findFreeLane(lanes);
            if (myLane == lanes.size()) {
               lanes.add(entry.sha);
            } else {
               lanes.set(myLane, entry.sha);
            }
         }

         // Assign parents to lanes
         int[] parentLanes = new int[entry.parents.size()];
         for (int i = 0; i < entry.parents.size(); i++) {
            String parentSha = entry.parents.get(i);
            Integer parentLane = shaToLane.get(parentSha);
            if (null != parentLane) {
               // Parent already has a lane from another child
               parentLanes[i] = parentLane;
            } else if (i == 0) {
               // First parent inherits current lane
               parentLanes[i] = myLane;
               lanes.set(myLane, parentSha);
               shaToLane.put(parentSha, myLane);
            } else {
               // Additional parents (merge) get new lanes
               int newLane = findFreeLane(lanes);
               if (newLane == lanes.size()) {
                  lanes.add(parentSha);
               } else {
                  lanes.set(newLane, parentSha);
               }
               parentLanes[i] = newLane;
               shaToLane.put(parentSha, newLane);
            }
         }

         // If this commit has no parents (root), free the lane
         if (entry.parents.isEmpty()) {
            lanes.set(myLane, null);
         }

         rows.add(new Row(entry, myLane, parentLanes, countActive(lanes)));
      }
      return rows;
   }

   private static int findFreeLane(ArrayList<String> lanes) {
      for (int i = 0; i < lanes.size(); i++) {
         if (null == lanes.get(i)) {
            return i;
         }
      }
      return lanes.size();
   }

   private static int countActive(ArrayList<String> lanes) {
      int count = 0;
      for (String s : lanes) {
         if (null != s) {
            count++;
         }
      }
      return count;
   }
}
