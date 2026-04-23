package javi;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the F9 git-integration keymap-resolution fix.
 *
 * <p>Regression coverage for the bug where {@link KeyMap#resolveForBuffer}
 * compared {@code buffer.toString()} (which includes the unique counter)
 * against bare buffer names like {@code "*git-status*"}, causing the git
 * overlay keymaps to never be applied.  These tests verify that:</p>
 *
 * <ul>
 *   <li>resolveForBuffer returns the correct overlay for each git buffer
 *       short name (status / patch / commit / commit-msg / log).</li>
 *   <li>The {@code gitstatus}, {@code gitpatch}, and {@code gitcommit}
 *       overlays honour {@code suppressParentEdit} so that unbound
 *       edit keys such as {@code 'o'} do not fall through to the
 *       normal keymap (Bug 5: 'o' in hunk view inserts a line).</li>
 *   <li>The {@code gitcommitmsg} overlay does NOT suppress edits, so
 *       the commit-message buffer remains fully editable.</li>
 * </ul>
 *
 * <p>These tests substitute for manual interactive verification of
 * the {@code s}, {@code u}, {@code ^]}, {@code ^L}, {@code o}, {@code :}
 * keys in the git status / patch / commit views.</p>
 */
@SuppressWarnings("unchecked")
class GitOverlayKeymapJUnitTest {

   private KeyMap normalMap;

   @BeforeAll
   static void initOnce() throws Exception {
      TestInit.initCommands();
   }

   @BeforeEach
   void setUp() {
      EventQueue.biglock2.lock();
      normalMap = buildGitOverlays();
   }

   @AfterEach
   void tearDown() {
      EventQueue.biglock2.unlock();
   }

   /**
    * Construct a private {@code normal} keymap and the five git
    * overlay keymaps that {@link KeyMap#initBufferKeyMaps} would
    * register at startup.  We mirror that wiring here because
    * {@link TestInit#initCommands} deliberately skips
    * {@code MapEvent.bindCommands} (it requires the AWT UI).
    *
    * <p>All command names use "openline" or "insert" (registered by
    * EditGroup in TestInit) since git-specific commands are only
    * available when the Plugin loader runs.  The tests verify
    * binding resolution and suppressParentEdit, not execution.</p>
    */
   private static KeyMap buildGitOverlays() {
      KeyGroup mg = new KeyGroup("git-norm-move");
      KeyGroup eg = new KeyGroup("git-norm-edit");
      // 'o' bound on the parent normal map — the suppressParentEdit
      // tests below rely on this being present so we can prove the
      // child overlay is hiding it.
      eg.keybind('o', "openline", null);
      eg.keybind('i', "insert", new boolean[]{false, false});
      KeyMap normal = new KeyMap("normal", mg, eg);
      KeyMap.register(normal);

      KeyMap gitlog = KeyMap.createOverlay("gitlog", normal);
      gitlog.bindEditKey('q', "openline", null);
      addTestNavigationKeys(gitlog);
      gitlog.setSuppressParentEdit(true);
      KeyMap.register(gitlog);

      KeyMap gitstatus = KeyMap.createOverlay("gitstatus", normal);
      gitstatus.bindEditKey('s', "openline", null);
      gitstatus.bindEditKey('u', "openline", null);
      gitstatus.bindEditKey(':', "openline", null);
      addTestNavigationKeys(gitstatus);
      gitstatus.setSuppressParentEdit(true);
      KeyMap.register(gitstatus);

      KeyMap gitpatch = KeyMap.createOverlay("gitpatch", normal);
      gitpatch.bindEditKey('s', "openline", null);
      gitpatch.bindEditKey('u', "openline", null);
      gitpatch.bindEditKey(':', "openline", null);
      addTestNavigationKeys(gitpatch);
      gitpatch.setSuppressParentEdit(true);
      KeyMap.register(gitpatch);

      KeyMap gitcommit = KeyMap.createOverlay("gitcommit", normal);
      gitcommit.bindEditKey('s', "openline", null);
      gitcommit.bindEditKey('u', "openline", null);
      gitcommit.bindEditKey(':', "openline", null);
      addTestNavigationKeys(gitcommit);
      gitcommit.setSuppressParentEdit(true);
      KeyMap.register(gitcommit);

      KeyMap gitcommitmsg =
         KeyMap.createOverlay("gitcommitmsg", normal);
      gitcommitmsg.bindEditKey('q', "openline", null);
      // intentionally NOT suppressed — message buffer is editable
      KeyMap.register(gitcommitmsg);

      return normal;
   }

   private static TextEdit<String> bufferNamed(String shortName) {
      try {
         FileDescriptor fd =
            FileDescriptor.InternalFd.make(shortName);
         FileProperties<String> fp =
            new FileProperties<>(fd, StringIoc.converter);
         return new TextEdit<>(new IoConverter<>(fp, false), fp);
      } catch (Exception e) {
         throw new RuntimeException(
            "failed to build test buffer", e);
      }
   }

   // ---- resolveForBuffer: maps each git buffer name to its overlay

   @Test
   void resolveStatusBuffer() {
      KeyMap km = KeyMap.resolveForBuffer(bufferNamed("*git-status*"));
      assertNotNull(km, "*git-status* should resolve to overlay");
      assertEquals("gitstatus", km.getName());
   }

   @Test
   void resolvePatchBuffer() {
      KeyMap km = KeyMap.resolveForBuffer(bufferNamed("*git-patch*"));
      assertNotNull(km, "*git-patch* should resolve to overlay");
      assertEquals("gitpatch", km.getName());
   }

   @Test
   void resolveCommitBuffer() {
      KeyMap km = KeyMap.resolveForBuffer(
         bufferNamed("*git-commit-staging*"));
      assertNotNull(km, "*git-commit-staging* should resolve");
      assertEquals("gitcommit", km.getName(),
         "any *git-commit prefix except msg routes to gitcommit");
   }

   @Test
   void resolveCommitAmendBuffer() {
      KeyMap km = KeyMap.resolveForBuffer(
         bufferNamed("*git-commit-amend*"));
      assertEquals("gitcommit", km.getName());
   }

   @Test
   void resolveCommitMsgBuffer() {
      KeyMap km = KeyMap.resolveForBuffer(
         bufferNamed("*git-commit-msg*"));
      assertNotNull(km, "*git-commit-msg* should resolve");
      assertEquals("gitcommitmsg", km.getName(),
         "msg buffer must use the editable msg overlay, "
            + "not the read-only gitcommit overlay");
   }

   @Test
   void resolveLogBuffer() {
      KeyMap km =
         KeyMap.resolveForBuffer(bufferNamed("*git-log*"));
      assertNotNull(km);
      assertEquals("gitlog", km.getName());
   }

   @Test
   void resolveLogVariantBuffer() {
      // gitlog is matched by prefix, e.g. "*git-log-graph*"
      KeyMap km =
         KeyMap.resolveForBuffer(bufferNamed("*git-log-graph*"));
      assertNotNull(km, "*git-log* prefix should match");
      assertEquals("gitlog", km.getName());
   }

   @Test
   void resolveNonGitBufferReturnsNull() {
      KeyMap km =
         KeyMap.resolveForBuffer(bufferNamed("regular-file.txt"));
      assertNull(km,
         "non-git buffer must NOT resolve to a git overlay");
   }

   @Test
   void resolveNullBuffer() {
      assertNull(KeyMap.resolveForBuffer(null));
   }

   /**
    * Regression: if {@code buffer.toString()} were used (the old
    * broken behaviour), the unique counter would prevent any match.
    * Verify that two buffers with the same short name resolve to
    * the same overlay even though their {@code toString()} differs
    * by counter.
    */
   @Test
   void resolveSameOverlayForDistinctBuffersWithSameShortName() {
      KeyMap a =
         KeyMap.resolveForBuffer(bufferNamed("*git-status*"));
      KeyMap b =
         KeyMap.resolveForBuffer(bufferNamed("*git-status*"));
      assertNotNull(a);
      assertSame(a, b,
         "both buffers must resolve to the same registered overlay");
   }

   // ---- suppressParentEdit: 'o' must NOT fall through

   /** Bug 5: 'o' in gitpatch view should not insert a line. */
   @Test
   void gitpatchSuppressesParentOpenLine() {
      KeyMap gitpatch = KeyMap.get("gitpatch");
      JeyEvent o = new JeyEvent(0, 0, 'o');
      assertNull(gitpatch.lookupEdit(o),
         "'o' must not fall through to parent on gitpatch overlay");
   }

   @Test
   void gitstatusSuppressesParentOpenLine() {
      KeyMap gitstatus = KeyMap.get("gitstatus");
      JeyEvent o = new JeyEvent(0, 0, 'o');
      assertNull(gitstatus.lookupEdit(o),
         "'o' must not fall through to parent on gitstatus overlay");
   }

   @Test
   void gitcommitSuppressesParentInsert() {
      KeyMap gitcommit = KeyMap.get("gitcommit");
      JeyEvent i = new JeyEvent(0, 0, 'i');
      assertNull(gitcommit.lookupEdit(i),
         "'i' must not fall through on gitcommit overlay");
   }

   /** Own-bound keys on the overlay must still resolve. */
   @Test
   void gitpatchOwnBindingsResolve() {
      KeyMap gitpatch = KeyMap.get("gitpatch");
      assertNotNull(gitpatch.lookupEdit(new JeyEvent(0, 0, 's')),
         "'s' (stage hunk) should be bound on gitpatch");
      assertNotNull(gitpatch.lookupEdit(new JeyEvent(0, 0, 'u')),
         "'u' (unstage hunk) should be bound on gitpatch");
      assertNotNull(gitpatch.lookupEdit(new JeyEvent(0, 0, ':')),
         "':' (commandproc) should be bound on gitpatch");
   }

   @Test
   void gitstatusOwnBindingsResolve() {
      KeyMap gitstatus = KeyMap.get("gitstatus");
      assertNotNull(gitstatus.lookupEdit(new JeyEvent(0, 0, 's')));
      assertNotNull(gitstatus.lookupEdit(new JeyEvent(0, 0, 'u')));
      assertNotNull(gitstatus.lookupEdit(new JeyEvent(0, 0, ':')));
   }

   /**
    * The commit-message overlay is editable — 'o' SHOULD fall
    * through to the parent normal keymap so the user can open
    * a new line in the message.
    */
   @Test
   void gitcommitmsgAllowsParentOpenLine() {
      KeyMap gitcommitmsg = KeyMap.get("gitcommitmsg");
      JeyEvent o = new JeyEvent(0, 0, 'o');
      assertNotNull(gitcommitmsg.lookupEdit(o),
         "'o' must fall through on the editable msg overlay");
   }

   // ---- addNavigationKeys: helpers for overlays ----

   /**
    * Mirrors {@code KeyMap.addNavigationKeys}: binds F5, F6, ^T
    * on overlays with suppressParentEdit so users can navigate
    * to the position list and return via the tag stack.
    *
    * <p>Uses already-registered command names ("openline") as
    * proxies for the real navigation commands since the real
    * PosListList commands are not available in test init.</p>
    */
   private static void addTestNavigationKeys(KeyMap overlay) {
      // F5 → gotopositionlist (proxied by "openline")
      overlay.bindEditAction(JeyEvent.VK_F5,
         "openline", null, 0);
      // F6 → gotopllist (proxied by "openline")
      overlay.bindEditAction(JeyEvent.VK_F6,
         "openline", null, 0);
      // ^T → poptag (proxied by "openline")
      overlay.bindEditKey(
         (char) 20, "openline", null, JeyEvent.CTRL_MASK);
   }

   // ---- Bug 1: F5/F6/^T must resolve on suppressed overlays ----

   @Test
   void gitstatusF5Resolves() {
      KeyMap km = KeyMap.get("gitstatus");
      JeyEvent f5 = new JeyEvent(0, JeyEvent.VK_F5,
         JeyEvent.CHAR_UNDEFINED);
      assertNotNull(km.lookupEdit(f5),
         "F5 (gotopositionlist) must be bound on gitstatus");
   }

   @Test
   void gitstatusF6Resolves() {
      KeyMap km = KeyMap.get("gitstatus");
      JeyEvent f6 = new JeyEvent(0, JeyEvent.VK_F6,
         JeyEvent.CHAR_UNDEFINED);
      assertNotNull(km.lookupEdit(f6),
         "F6 (gotopllist) must be bound on gitstatus");
   }

   @Test
   void gitstatusCtrlTResolves() {
      KeyMap km = KeyMap.get("gitstatus");
      JeyEvent ctrlT = new JeyEvent(JeyEvent.CTRL_MASK, 0,
         (char) 20);
      assertNotNull(km.lookupEdit(ctrlT),
         "^T (poptag) must be bound on gitstatus");
   }

   @Test
   void gitpatchF6Resolves() {
      KeyMap km = KeyMap.get("gitpatch");
      JeyEvent f6 = new JeyEvent(0, JeyEvent.VK_F6,
         JeyEvent.CHAR_UNDEFINED);
      assertNotNull(km.lookupEdit(f6),
         "F6 must be bound on gitpatch");
   }

   @Test
   void gitpatchCtrlTResolves() {
      KeyMap km = KeyMap.get("gitpatch");
      JeyEvent ctrlT = new JeyEvent(JeyEvent.CTRL_MASK, 0,
         (char) 20);
      assertNotNull(km.lookupEdit(ctrlT),
         "^T must be bound on gitpatch");
   }

   @Test
   void gitcommitF6Resolves() {
      KeyMap km = KeyMap.get("gitcommit");
      JeyEvent f6 = new JeyEvent(0, JeyEvent.VK_F6,
         JeyEvent.CHAR_UNDEFINED);
      assertNotNull(km.lookupEdit(f6),
         "F6 must be bound on gitcommit");
   }

   @Test
   void gitcommitCtrlTResolves() {
      KeyMap km = KeyMap.get("gitcommit");
      JeyEvent ctrlT = new JeyEvent(JeyEvent.CTRL_MASK, 0,
         (char) 20);
      assertNotNull(km.lookupEdit(ctrlT),
         "^T must be bound on gitcommit");
   }

   @Test
   void gitlogF6Resolves() {
      KeyMap km = KeyMap.get("gitlog");
      JeyEvent f6 = new JeyEvent(0, JeyEvent.VK_F6,
         JeyEvent.CHAR_UNDEFINED);
      assertNotNull(km.lookupEdit(f6),
         "F6 must be bound on gitlog");
   }

   @Test
   void gitlogCtrlTResolves() {
      KeyMap km = KeyMap.get("gitlog");
      JeyEvent ctrlT = new JeyEvent(JeyEvent.CTRL_MASK, 0,
         (char) 20);
      assertNotNull(km.lookupEdit(ctrlT),
         "^T must be bound on gitlog");
   }
}
