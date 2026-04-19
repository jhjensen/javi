package javi.git;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GitProcess} command execution utilities.
 *
 * <p>These tests run real git commands against the javi repository
 * (which is a git worktree). Tests that require a git repo use
 * the test's own source directory as the working directory.</p>
 */
class GitProcessJUnitTest {

   // ── isGitRepo ───────────────────────────────────────────────

   @Test
   void isGitRepoReturnsTrueInGitDir() {
      // The test is running inside a git worktree
      assertTrue(GitProcess.isGitRepo(),
         "should detect git repo from CWD");
   }

   @Test
   void isGitRepoWithDirReturnsTrueForGitDir() {
      File dir = new File(System.getProperty("user.dir"));
      assertTrue(GitProcess.isGitRepo(dir),
         "should detect git repo from explicit directory");
   }

   @Test
   void isGitRepoWithDirReturnsFalseForNonGitDir() {
      File tmp = new File(System.getProperty("java.io.tmpdir"));
      assertFalse(GitProcess.isGitRepo(tmp),
         "temp dir should not be a git repo");
   }

   @Test
   void isGitRepoWithNullDirDelegatesToNoArgVersion() {
      // null dir should use the no-arg version
      boolean result = GitProcess.isGitRepo(null);
      // Just verify it doesn't throw; result depends on CWD
      assertDoesNotThrow(() -> GitProcess.isGitRepo(null));
   }

   // ── execute ─────────────────────────────────────────────────

   @Test
   void executeVersionReturnsOutput() throws IOException {
      List<String> output = GitProcess.execute("--version");
      assertNotNull(output);
      assertFalse(output.isEmpty(), "git --version should produce output");
      assertTrue(output.get(0).startsWith("git version"),
         "first line should start with 'git version'");
   }

   @Test
   void executeWithDirReturnsOutput() throws IOException {
      File dir = new File(System.getProperty("user.dir"));
      List<String> output = GitProcess.execute(dir, "--version");
      assertNotNull(output);
      assertFalse(output.isEmpty());
   }

   @Test
   void executeWithNullDirReturnsOutput() throws IOException {
      List<String> output = GitProcess.execute((File) null, "--version");
      assertNotNull(output);
      assertFalse(output.isEmpty());
   }

   // ── executeWithExitCode ─────────────────────────────────────

   @Test
   void executeWithExitCodeReturnsZeroForVersion() throws IOException {
      int rc = GitProcess.executeWithExitCode("--version");
      assertEquals(0, rc, "git --version should exit 0");
   }

   @Test
   void executeWithExitCodeReturnsNonZeroForBadCommand() throws IOException {
      int rc = GitProcess.executeWithExitCode(
         "nonexistent-subcommand-xyz");
      assertTrue(rc != 0, "invalid git subcommand should exit non-zero");
   }

   // ── executeWithResult ───────────────────────────────────────

   @Test
   void executeWithResultReturnsCodeAndOutput() throws IOException {
      GitProcess.Result result = GitProcess.executeWithResult("--version");
      assertEquals(0, result.exitCode);
      assertNotNull(result.output);
      assertFalse(result.output.isEmpty());
   }

   @Test
   void executeWithResultBadCommandReturnsNonZero() throws IOException {
      GitProcess.Result result = GitProcess.executeWithResult(
         "nonexistent-subcommand-xyz");
      assertTrue(result.exitCode != 0);
      assertNotNull(result.output);
   }

   // ── Result class ────────────────────────────────────────────

   @Test
   void resultFieldsAccessible() {
      GitProcess.Result r = new GitProcess.Result(42, List.of("a", "b"));
      assertEquals(42, r.exitCode);
      assertEquals(2, r.output.size());
      assertEquals("a", r.output.get(0));
   }

   // ── getBranchNames ──────────────────────────────────────────

   @Test
   void getBranchNamesReturnsNonEmptyList() throws IOException {
      List<String> branches = GitProcess.getBranchNames();
      assertNotNull(branches);
      assertFalse(branches.isEmpty(),
         "should have at least one branch");
      // master should always exist in the javi repo
      assertTrue(branches.contains("master")
         || branches.stream().anyMatch(b -> b.contains("T1")),
         "should contain known branch name");
   }

   // ── executeWithStdin ────────────────────────────────────────

   @Test
   void executeWithStdinReturnsResult() throws IOException {
      // Use hash-object to hash some content from stdin
      GitProcess.Result result = GitProcess.executeWithStdin(
         "hello world\n", "hash-object", "--stdin");
      assertEquals(0, result.exitCode,
         "hash-object --stdin should succeed");
      assertNotNull(result.output);
      assertFalse(result.output.isEmpty(),
         "hash-object should output a SHA");
      // SHA-1 hash is 40 hex chars
      assertTrue(result.output.get(0).matches("[0-9a-f]{40,}"),
         "output should be a hex hash");
   }
}
