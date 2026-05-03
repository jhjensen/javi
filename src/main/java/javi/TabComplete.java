package javi;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javi.git.GitProcess;

/**
 * Tab-completion for the colon command line.
 *
 * <p>Completes branch names when the current command is a git command
 * that takes a branch argument (git_branch_switch, git_branch_delete,
 * git_merge).</p>
 */
final class TabComplete {

   private static final Set<String> BRANCH_COMMANDS = Set.of(
      "git_branch_switch",
      "git_branch_delete",
      "git_merge"
   );

   private TabComplete() {
   }

   /**
    * Attempt to complete the current command-line text.
    *
    * @param lineText the committed line text (including prompt char)
    * @param insertX  the cursor position in committed text
    * @param pending  unflushed buffer content
    * @return the completion suffix to append, or null if no completion
    */
   static String complete(String lineText, int insertX,
         String pending) {
      String full = lineText.substring(0,
         Math.min(insertX, lineText.length())) + pending;
      if (full.length() < 2 || full.charAt(0) != ':')
         return null;
      String cmdText = full.substring(1);
      int spaceIdx = cmdText.indexOf(' ');
      if (spaceIdx < 0) {
         // No space yet — complete the command name itself
         return completeCommandName(cmdText);
      }
      String cmd = cmdText.substring(0, spaceIdx);
      if (!BRANCH_COMMANDS.contains(cmd))
         return null;
      String partial = cmdText.substring(spaceIdx + 1);
      return completeBranch(partial);
   }

   /**
    * Complete a command name from the registered commands.
    * In a git context, git commands are prioritized.
    *
    * @param partial the partial command name typed so far
    * @return the completion suffix, or null if no match
    */
   private static String completeCommandName(String partial) {
      Set<String> cmds = Rgroup.getRegisteredCommands();
      if (cmds.isEmpty())
         return null;
      boolean isGit = false;
      try {
         FvContext<?> fvc = FvContext.getCurrFvc();
         String bufName = (fvc != null && fvc.edvec != null)
            ? fvc.edvec.fdes().getShortName() : "";
         isGit = bufName.startsWith("*git-");
      } catch (Throwable t) {
         // FvContext may not be initialized in test environments
      }

      String match = null;
      if (isGit) {
         for (String c : cmds) {
            if (c.startsWith("git") && c.startsWith(partial)) {
               match = (null == match) ? c : commonPrefix(match, c);
            }
         }
      }
      if (null == match) {
         for (String c : cmds) {
            if (!c.isEmpty() && c.startsWith(partial)) {
               match = (null == match) ? c : commonPrefix(match, c);
            }
         }
      }
      if (null == match || match.length() <= partial.length())
         return null;
      return match.substring(partial.length());
   }

   /**
    * Find the longest common-prefix completion among branches
    * matching the partial name.
    *
    * @param partial the partial branch name typed so far
    * @return the suffix to append, or null if no match
    */
   private static String completeBranch(String partial) {
      List<String> branches;
      try {
         branches = GitProcess.getBranchNames();
      } catch (IOException e) {
         return null;
      }
      String match = null;
      for (String b : branches) {
         if (b.startsWith(partial)) {
            if (null == match) {
               match = b;
            } else {
               match = commonPrefix(match, b);
            }
         }
      }
      if (null == match || match.length() <= partial.length())
         return null;
      return match.substring(partial.length());
   }

   /** Return the longest common prefix of two strings. */
   private static String commonPrefix(String a, String b) {
      int len = Math.min(a.length(), b.length());
      int i = 0;
      while (i < len && a.charAt(i) == b.charAt(i))
         i++;
      return a.substring(0, i);
   }
}
