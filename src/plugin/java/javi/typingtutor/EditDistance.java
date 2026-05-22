package javi.typingtutor;

/**
 * Computes Levenshtein edit distance between two strings.
 *
 * <p>The edit distance counts the minimum number of single-character
 * insertions, deletions, and substitutions needed to transform one
 * string into another. This gives a more accurate error count than
 * simple character-by-character comparison, handling cases where the
 * user inserts or deletes a character (which would otherwise cause
 * all subsequent positions to mismatch).</p>
 */
final class EditDistance {

   private EditDistance() { }

   /**
    * Compute the Levenshtein distance between two strings.
    *
    * @param expected the reference string
    * @param typed the user-typed string
    * @return minimum edit operations (insertions + deletions +
    *         substitutions) to transform typed into expected
    */
   static int distance(String expected, String typed) {
      int m = expected.length();
      int n = typed.length();

      // Use two rows instead of full matrix for O(min(m,n)) space
      int[] prev = new int[n + 1];
      int[] curr = new int[n + 1];

      for (int j = 0; j <= n; j++)
         prev[j] = j;

      for (int i = 1; i <= m; i++) {
         curr[0] = i;
         for (int j = 1; j <= n; j++) {
            int cost = expected.charAt(i - 1) == typed.charAt(j - 1)
               ? 0 : 1;
            curr[j] = Math.min(Math.min(
               prev[j] + 1,        // deletion
               curr[j - 1] + 1),   // insertion
               prev[j - 1] + cost  // substitution
            );
         }
         int[] tmp = prev;
         prev = curr;
         curr = tmp;
      }
      return prev[n];
   }

   /**
    * Count the number of correct characters using the edit distance
    * alignment. Returns the number of characters in the expected
    * string that were correctly typed (i.e. matched in the optimal
    * alignment).
    *
    * <p>Correct chars = expected.length() - editDistance.
    * This accounts for substitutions, insertions, and deletions.</p>
    *
    * @param expected the reference string
    * @param typed the user-typed string
    * @return number of correctly-typed characters (>= 0)
    */
   static int correctChars(String expected, String typed) {
      int dist = distance(expected, typed);
      int correct = expected.length() - dist;
      return Math.max(correct, 0);
   }

   /**
    * Computes an alignment between expected and typed strings using
    * the Levenshtein DP matrix traceback. Returns a boolean array of
    * length {@code typed.length()} where {@code true} means the
    * character at that position correctly matches an aligned character
    * in the expected string.
    *
    * <p>This prevents a single inserted character from causing all
    * subsequent characters to appear as errors. Only the actual
    * mistyped, inserted, or substituted characters are marked
    * incorrect.</p>
    *
    * @param expected the reference string
    * @param typed the user-typed string
    * @return per-character correctness for the typed string
    */
   static boolean[] alignCorrectness(String expected, String typed) {
      int m = expected.length();
      int n = typed.length();
      boolean[] correct = new boolean[n];
      if (n == 0)
         return correct;

      // Build full DP matrix for traceback
      int[][] dp = new int[m + 1][n + 1];
      for (int j = 0; j <= n; j++)
         dp[0][j] = j;
      for (int i = 1; i <= m; i++) {
         dp[i][0] = i;
         for (int j = 1; j <= n; j++) {
            int cost = expected.charAt(i - 1) == typed.charAt(j - 1)
               ? 0 : 1;
            dp[i][j] = Math.min(Math.min(
               dp[i - 1][j] + 1,        // deletion from expected
               dp[i][j - 1] + 1),       // insertion in typed
               dp[i - 1][j - 1] + cost  // match or substitution
            );
         }
      }

      // Traceback: walk from bottom-right to top-left
      int i = m;
      int j = n;
      while (i > 0 && j > 0) {
         int cost = expected.charAt(i - 1) == typed.charAt(j - 1)
            ? 0 : 1;
         if (dp[i][j] == dp[i - 1][j - 1] + cost) {
            correct[j - 1] = (cost == 0);
            i--;
            j--;
         } else if (dp[i][j] == dp[i - 1][j] + 1) {
            i--; // deletion from expected — no typed char consumed
         } else {
            correct[j - 1] = false; // insertion in typed
            j--;
         }
      }
      // Remaining typed chars (j > 0) are all insertions
      while (j > 0) {
         correct[j - 1] = false;
         j--;
      }

      return correct;
   }
}
