package javi.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static history.Tools.trace;

/**
 * Execute git commands and capture output.
 *
 * <p>Uses ProcessBuilder to run git commands in the current working
 * directory, capturing stdout and stderr.</p>
 */
public final class GitProcess {

   /** Private constructor to prevent instantiation. */
   private GitProcess() {
   }

   /**
    * Execute a git command with the given arguments.
    *
    * @param args git subcommand and arguments (e.g. "status", "--porcelain")
    * @return list of output lines from the command
    * @throws IOException if the process cannot be started or read
    */
   public static List<String> execute(String... args) throws IOException {
      return execute((java.io.File) null, args);
   }

   /**
    * Execute a git command in the given directory.
    *
    * @param dir working directory for the git command, or null for default
    * @param args git subcommand and arguments
    * @return list of output lines from the command
    * @throws IOException if the process cannot be started or read
    */
   public static List<String> execute(java.io.File dir, String... args)
         throws IOException {
      String[] cmd = new String[args.length + 1];
      cmd[0] = "git";
      System.arraycopy(args, 0, cmd, 1, args.length);

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      if (dir != null)
         pb.directory(dir);
      Process proc = pb.start();

      ArrayList<String> output = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(proc.getInputStream(),
               StandardCharsets.UTF_8))) {
         for (String line; null != (line = reader.readLine());) {
            output.add(line);
         }
         proc.waitFor();
      } catch (InterruptedException e) {
         trace("interrupted executing git " + String.join(" ", args));
         Thread.currentThread().interrupt();
      }
      return output;
   }

   /**
    * Execute a git command and return the exit code.
    *
    * @param args git subcommand and arguments
    * @return the process exit code, or -1 on error
    * @throws IOException if the process cannot be started
    */
   public static int executeWithExitCode(String... args) throws IOException {
      String[] cmd = new String[args.length + 1];
      cmd[0] = "git";
      System.arraycopy(args, 0, cmd, 1, args.length);

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      Process proc = pb.start();

      // Drain output to prevent blocking
      try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(proc.getInputStream(),
               StandardCharsets.UTF_8))) {
         while (null != reader.readLine()) {
            continue; // discard output to prevent blocking
         }
         return proc.waitFor();
      } catch (InterruptedException e) {
         trace("interrupted executing git " + String.join(" ", args));
         Thread.currentThread().interrupt();
         return -1;
      }
   }

   /**
    * Result holder for commands that need both output and exit code.
    */
   public static final class Result {
      /** The process exit code. */
      public final int exitCode;
      /** Output lines (stdout + stderr). */
      public final List<String> output;

      Result(int code, List<String> lines) {
         exitCode = code;
         output = lines;
      }
   }

   /**
    * Execute a git command and return both exit code and output.
    *
    * @param args git subcommand and arguments
    * @return Result with exit code and output lines
    * @throws IOException if the process cannot be started
    */
   public static Result executeWithResult(String... args) throws IOException {
      String[] cmd = new String[args.length + 1];
      cmd[0] = "git";
      System.arraycopy(args, 0, cmd, 1, args.length);

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      Process proc = pb.start();

      ArrayList<String> output = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(proc.getInputStream(),
               StandardCharsets.UTF_8))) {
         for (String line; null != (line = reader.readLine());) {
            output.add(line);
         }
         int rc = proc.waitFor();
         return new Result(rc, output);
      } catch (InterruptedException e) {
         trace("interrupted executing git " + String.join(" ", args));
         Thread.currentThread().interrupt();
         return new Result(-1, output);
      }
   }

   /**
    * Execute a git command in a specific directory and return both
    * exit code and output.
    *
    * @param dir working directory (may be null for default)
    * @param args git subcommand and arguments
    * @return Result with exit code and output lines
    * @throws IOException if the process cannot be started
    */
   public static Result executeWithResult(java.io.File dir, String... args)
         throws IOException {
      String[] cmd = new String[args.length + 1];
      cmd[0] = "git";
      System.arraycopy(args, 0, cmd, 1, args.length);

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      if (dir != null)
         pb.directory(dir);
      Process proc = pb.start();

      ArrayList<String> output = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(proc.getInputStream(),
               StandardCharsets.UTF_8))) {
         for (String line; null != (line = reader.readLine());) {
            output.add(line);
         }
         int rc = proc.waitFor();
         return new Result(rc, output);
      } catch (InterruptedException e) {
         trace("interrupted executing git " + String.join(" ", args));
         Thread.currentThread().interrupt();
         return new Result(-1, output);
      }
   }

   /**
    * Get the list of local branch names.
    *
    * @return list of branch names (without leading markers)
    * @throws IOException if the git command fails
    */
   public static List<String> getBranchNames() throws IOException {
      List<String> raw = execute(
         "branch", "--format=%(refname:short)");
      return raw;
   }

   /**
    * Check if the current directory is inside a git repository.
    *
    * @return true if inside a git repo
    */
   public static boolean isGitRepo() {
      try {
         return 0 == executeWithExitCode("rev-parse", "--is-inside-work-tree");
      } catch (IOException e) {
         return false;
      }
   }

   /**
    * Check if the given directory is inside a git repository.
    *
    * @param dir directory to check, or null to use the JVM CWD
    * @return true if inside a git repo
    */
   public static boolean isGitRepo(java.io.File dir) {
      if (dir == null)
         return isGitRepo();
      try {
         String[] cmd = {"git", "-C", dir.getAbsolutePath(),
            "rev-parse", "--is-inside-work-tree"};
         ProcessBuilder pb = new ProcessBuilder(cmd);
         pb.redirectErrorStream(true);
         Process proc = pb.start();
         try (BufferedReader reader = new BufferedReader(
               new InputStreamReader(proc.getInputStream(),
                  StandardCharsets.UTF_8))) {
            while (null != reader.readLine()) {
               continue;
            }
            return 0 == proc.waitFor();
         }
      } catch (IOException | InterruptedException e) {
         return false;
      }
   }

   /**
    * Get the root directory of the git repository containing
    * the given directory.
    *
    * @param dir directory within a git repo, or null for JVM CWD
    * @return the repo root path, or null if not a git repo
    */
   public static String getRepoRoot(java.io.File dir) {
      try {
         List<String> output;
         if (dir != null) {
            output = execute(dir,
               "rev-parse", "--show-toplevel");
         } else {
            output = execute("rev-parse", "--show-toplevel");
         }
         if (!output.isEmpty())
            return output.get(0).trim();
      } catch (IOException e) {
         // not a repo
      }
      return null;
   }

   /**
    * Get the path to the actual .git directory for the repo
    * containing the given directory.  In a worktree this returns
    * the worktree-specific git dir (e.g. {@code .git/worktrees/X}),
    * not the shared toplevel.  Falls back to {@code <root>/.git}
    * if the command fails.
    *
    * @param dir directory within a git repo, or null for JVM CWD
    * @return the git directory path, or null if not a git repo
    */
   public static String getGitDir(java.io.File dir) {
      try {
         String[] cmd = {"git", "rev-parse", "--absolute-git-dir"};
         ProcessBuilder pb = new ProcessBuilder(cmd);
         pb.redirectErrorStream(true);
         if (dir != null)
            pb.directory(dir);
         Process proc = pb.start();
         java.util.ArrayList<String> output = new java.util.ArrayList<>();
         try (java.io.BufferedReader reader =
               new java.io.BufferedReader(
                  new java.io.InputStreamReader(
                     proc.getInputStream(),
                     StandardCharsets.UTF_8))) {
            for (String line; null != (line = reader.readLine());) {
               output.add(line);
            }
            int rc = proc.waitFor();
            if (rc == 0 && !output.isEmpty())
               return output.get(0).trim();
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      } catch (IOException e) {
         // not a repo or old git
      }
      return null;
   }

   /**
    * Execute a git command, writing the given string to its stdin.
    *
    * @param stdinContent content to write to the process stdin
    * @param args git subcommand and arguments
    * @return Result with exit code and output lines
    * @throws IOException if the process cannot be started
    */
   public static Result executeWithStdin(String stdinContent,
         String... args) throws IOException {
      return executeWithStdin(null, stdinContent, args);
   }

   /**
    * Execute a git command in a specific directory, writing the
    * given string to its stdin.
    *
    * @param dir working directory (may be null for default)
    * @param stdinContent content to write to the process stdin
    * @param args git subcommand and arguments
    * @return Result with exit code and output lines
    * @throws IOException if the process cannot be started
    */
   public static Result executeWithStdin(java.io.File dir,
         String stdinContent, String... args) throws IOException {
      String[] cmd = new String[args.length + 1];
      cmd[0] = "git";
      System.arraycopy(args, 0, cmd, 1, args.length);

      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.redirectErrorStream(true);
      if (dir != null)
         pb.directory(dir);
      Process proc = pb.start();

      // Write stdin content
      try (java.io.OutputStream os = proc.getOutputStream()) {
         os.write(stdinContent.getBytes(StandardCharsets.UTF_8));
         os.flush();
      }

      ArrayList<String> output = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(proc.getInputStream(),
               StandardCharsets.UTF_8))) {
         for (String line; null != (line = reader.readLine());) {
            output.add(line);
         }
         int rc = proc.waitFor();
         return new Result(rc, output);
      } catch (InterruptedException e) {
         trace("interrupted executing git " + String.join(" ", args));
         Thread.currentThread().interrupt();
         return new Result(-1, output);
      }
   }
}
