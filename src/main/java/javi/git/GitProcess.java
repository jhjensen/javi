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
}
