package javi;

import java.io.IOException;

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
final class TestInit {

   private static volatile boolean initialized;
   private static volatile boolean commandsInitialized;

   private TestInit() {
   }

   /**
    * Idempotent initialization: forces TextEdit root creation,
    * StreamInterface singleton, and Buffers (delete buffer).
    * Safe to call from multiple test classes' {@code @BeforeAll}.
    */
   static synchronized void init() throws Exception {
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
   static synchronized void initCommands() throws Exception {
      init(); // ensure base init first
      if (commandsInitialized)
         return;
      EventQueue.biglock2.lock();
      try {
         new Javi.Jcmds();
         new MiscCommands();
         new EditGroup();
         Command.init();
         MoveGroup.init();
      } finally {
         EventQueue.biglock2.unlock();
      }
      commandsInitialized = true;
   }
}
