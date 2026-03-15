package javi;

/**
 * Shared test initialization utility.
 *
 * <p>
 * Manages the UI singleton ({@link StreamInterface}) for JUnit tests
 * that need editor infrastructure. Since all tests run in one JVM,
 * only the first call creates the instance.
 *
 * <p>
 * Also ensures {@link TextEdit} static initialization (root creation)
 * happens before any other {@code InternalFd} instances are created,
 * avoiding counter-dependent canonName mismatches.
 */
public final class TestInit {

   private static volatile boolean initialized;
   private static volatile boolean commandsInitialized;
   private static volatile boolean allCommandsInitialized;

   private TestInit() {
   }

   /**
    * Idempotent initialization: forces TextEdit root creation,
    * StreamInterface singleton, and Buffers (delete buffer).
    * Safe to call from multiple test classes' {@code @BeforeAll}.
    */
   public static synchronized void init() throws Exception {
      if (initialized)
         return;
      // TextEdit root creation requires biglock2 to be held
      EventQueue.biglock2.lock();
      try {
         // Force TextEdit class load so its static{} root creation
         // runs before anything else increments InternalFd.uniqCtr
         Class.forName("javi.TextEdit");
         // Initialize delete buffer for processCommand tests
         EditTester1.TestCircBuffer.initCmd();
      } finally {
         EventQueue.biglock2.unlock();
      }
      try {
         new StreamInterface();
      } catch (RuntimeException e) {
         // singleton already created by another test class — OK
         if (!e.getMessage().contains("two Awt singletons"))
            throw e;
      }
      initialized = true;
   }

   /**
    * Idempotent initialization of the command system.
    * Registers core {@link Rgroup} subclasses needed for
    * {@code bindingLookup} and command dispatch via
    * {@code processCommand}.
    *
    * <p>
    * Does <b>not</b> call {@code MapEvent.bindCommands()} because
    * that requires UI-specific commands registered by
    * {@code AwtInterface.Commands} (e.g. "togglestatus", "fullscreen")
    * which are unavailable in the {@link StreamInterface} test
    * environment.
    * </p>
    *
    * <p>
    * Groups with heavyweight side-effects ({@code PosListList.Cmd},
    * {@code JS.JSR}, {@code MakeCmd}, {@code JavaCompiler},
    * {@code CheckStyle}) are also omitted because they require
    * {@code FvContext} or external tool setup.
    * </p>
    */
   public static synchronized void initCommands() throws Exception {
      init(); // ensure base init first
      if (commandsInitialized)
         return;
      EventQueue.biglock2.lock();
      try {
         new Javi.Jcmds();
         new MiscCommands();
         new EditGroup();
         new FormatDispatch();
         Command.init();
         MoveGroup.init();
         FileList.initDescriptions();
         new javi.ai.AICommands();
      } finally {
         EventQueue.biglock2.unlock();
      }
      commandsInitialized = true;
   }

   /**
    * Idempotent initialization of ALL command groups needed for
    * full keymap binding ({@code MapEvent.bindCommands()}).
    * Registers FileList, DirEdit, PosListList, Git commands, and
    * stubs for AWT-only commands unavailable in headless tests.
    */
   public static synchronized void initAllCommands() throws Exception {
      initCommands(); // ensure core commands first
      if (allCommandsInitialized)
         return;
      EventQueue.biglock2.lock();
      try {
         // FileList singleton (registers vi, e, nextfile, Zprocess, etc.)
         if (FileList.TestAccess.getInstance() == null)
            FileList.make("");
         // DirEdit commands (diredit_open, diredit_sort, etc.)
         DirEdit.Commands.getInstance();
         // PosListList.Cmd (nextpos, cn, cp, gotodirlist, etc.)
         if (Rgroup.bindingLookup("nextpos") == null)
            new PosListList.Cmd();
         // Git commands (git_expand, git_log_diff, etc.) — plugin
         Class.forName("javi.git.GitCommands");
         // LSP commands (lsp.def, lsp.ref, etc.) — plugin
         Class.forName("javi.lsp.LspCommands");
         // MakeCmd (mk, cc) — needed by MapEvent.bindCommands F7 binding
         if (Rgroup.bindingLookup("mk") == null)
            new MakeCmd();
         // AWT-only stubs: commands bound by MapEvent.bindCommands()
         // that are normally provided by AwtInterface.Commands or
         // heavyweight classes (JS.JSR, etc.)
         String[] awtStubs = {"togglestatus", "fullscreen", "gotofontlist",
               "jsevalfile", "jseval", "jsclear"};
         for (String cmd : awtStubs) {
            if (Rgroup.bindingLookup(cmd) == null) {
               new Rgroup() {
                  { register(new String[]{"", cmd}); }
                  public Object doroutine(int rnum, Object arg,
                        int count, int rcount, FvContext fvc,
                        boolean dotmode) {
                     return null;
                  }
               };
            }
         }
      } finally {
         EventQueue.biglock2.unlock();
      }
      allCommandsInitialized = true;
   }
}
