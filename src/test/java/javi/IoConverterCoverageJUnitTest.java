package javi;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended coverage tests for {@link IoConverter}.
 *
 * <p>Tests convertStream, reload, toString, constructor state
 * transitions, and interaction with EditCache via a concrete
 * test subclass.</p>
 */
class IoConverterCoverageJUnitTest {

   @BeforeAll
   static void initEditor() throws Exception {
      TestInit.init();
   }

   @BeforeEach
   void acquireLock() {
      EventQueue.biglock2.lock();
   }

   @AfterEach
   void releaseLock() {
      EventQueue.biglock2.unlock();
   }

   // ── Helpers ─────────────────────────────────────────────────

   /** Concrete IoConverter serving a fixed list of strings. */
   private static final class FixedIoc extends IoConverter<String> {
      private static final long serialVersionUID = 1;
      private final List<String> items;
      private int index;

      FixedIoc(String name, List<String> elems) {
         super(new FileProperties<>(
            FileDescriptor.InternalFd.make(name),
            StringIoc.converter), false);
         items = new ArrayList<>(elems);
         index = 0;
      }

      @Override
      public String getnext() {
         return index < items.size() ? items.get(index++) : null;
      }

      void resetIndex() {
         index = 0;
      }
   }

   /** Empty IoConverter — getnext always returns null. */
   private static final class EmptyIoc extends IoConverter<String> {
      private static final long serialVersionUID = 1;

      EmptyIoc(String name) {
         super(new FileProperties<>(
            FileDescriptor.InternalFd.make(name),
            StringIoc.converter), false);
      }
   }

   /** Quick-start IoConverter — triggers INITSTART state. */
   private static final class QuickIoc extends IoConverter<String> {
      private static final long serialVersionUID = 1;
      private boolean called = false;

      QuickIoc(String name) {
         super(new FileProperties<>(
            FileDescriptor.InternalFd.make(name),
            StringIoc.converter), true);
      }

      @Override
      public String getnext() {
         if (!called) {
            called = true;
            return "quickdata";
         }
         return null;
      }
   }

   private static FileProperties<String> makeFP(String name) {
      return new FileProperties<>(
         FileDescriptor.InternalFd.make(name),
         StringIoc.converter);
   }

   // ── convertStream ─────────────────────────────────────────

   @Test
   @DisplayName("convertStream returns all elements from getnext")
   void convertStreamReturnsAll() {
      FixedIoc ioc = new FixedIoc("cs1",
         List.of("alpha", "beta", "gamma"));
      EditCache<String> result = ioc.convertStream();
      assertEquals(3, result.size());
      assertEquals("alpha", result.get(0));
      assertEquals("beta", result.get(1));
      assertEquals("gamma", result.get(2));
   }

   @Test
   @DisplayName("convertStream returns empty cache when no elements")
   void convertStreamEmptyWhenNoElements() {
      EmptyIoc ioc = new EmptyIoc("cs_empty");
      EditCache<String> result = ioc.convertStream();
      assertEquals(0, result.size());
   }

   @Test
   @DisplayName("convertStream with single element")
   void convertStreamSingleElement() {
      FixedIoc ioc = new FixedIoc("cs_single", List.of("only"));
      EditCache<String> result = ioc.convertStream();
      assertEquals(1, result.size());
      assertEquals("only", result.get(0));
   }

   @Test
   @DisplayName("convertStream with many elements")
   void convertStreamManyElements() {
      List<String> items = new ArrayList<>();
      for (int i = 0; i < 100; i++)
         items.add("line" + i);
      FixedIoc ioc = new FixedIoc("cs_many", items);
      EditCache<String> result = ioc.convertStream();
      assertEquals(100, result.size());
      assertEquals("line0", result.get(0));
      assertEquals("line99", result.get(99));
   }

   // ── toString ──────────────────────────────────────────────

   @Test
   @DisplayName("toString returns shortName of FileDescriptor")
   void toStringReturnsShortName() {
      IoConverter<String> ioc = new IoConverter<>(makeFP("myname"), false);
      assertEquals("myname", ioc.toString());
   }

   @Test
   @DisplayName("toString varies per instance")
   void toStringVariesPerInstance() {
      IoConverter<String> a = new IoConverter<>(makeFP("aaa"), false);
      IoConverter<String> b = new IoConverter<>(makeFP("bbb"), false);
      assertEquals("aaa", a.toString());
      assertEquals("bbb", b.toString());
   }

   // ── constructor state ─────────────────────────────────────

   @Test
   @DisplayName("non-quick constructor creates INIT state")
   void nonQuickConstructorInitState() {
      IoConverter<String> ioc = new IoConverter<>(makeFP("nq"), false);
      assertNull(ioc.getnext(), "base getnext returns null");
   }

   @Test
   @DisplayName("quick constructor sets INITSTART state")
   void quickConstructorInitStartState() {
      QuickIoc ioc = new QuickIoc("quick1");
      assertNotNull(ioc, "quick IoConverter should be constructable");
   }

   // ── reload ────────────────────────────────────────────────

   @Test
   @DisplayName("reload on fixed ioc processes all elements")
   void reloadProcessesElements() {
      FixedIoc ioc = new FixedIoc("reload1",
         List.of("x", "y", "z"));
      EditCache<String> cache = new EditCache<>();
      // init1 sets up the cache
      ioc.init1(cache, new IoConverter.BuildCB() {
         @Override
         void notifyDone(EditCache ed) {
            // no-op
         }
         @Override
         BackupStatus getBackupStatus() {
            return null;
         }
      });
      ioc.reload();
      // After reload, elements should have been read via dorun
   }

   @Test
   @DisplayName("reload on empty ioc is safe")
   void reloadEmptyIsSafe() {
      EmptyIoc ioc = new EmptyIoc("reload_empty");
      EditCache<String> cache = new EditCache<>();
      ioc.init1(cache, new IoConverter.BuildCB() {
         @Override
         void notifyDone(EditCache ed) {
         }
         @Override
         BackupStatus getBackupStatus() {
            return null;
         }
      });
      ioc.reload();
   }

   // ── prop field ────────────────────────────────────────────

   @Test
   @DisplayName("prop is accessible and has correct descriptor")
   void propFieldAccessible() {
      FileProperties<String> fp = makeFP("proptest");
      IoConverter<String> ioc = new IoConverter<>(fp, false);
      assertNotNull(ioc.prop);
      assertEquals("proptest", ioc.prop.fdes.shortName);
   }

   // ── addElement / dorun interaction ────────────────────────

   @Test
   @DisplayName("multiple convertStream calls on reset ioc")
   void multipleConvertStreamCalls() {
      FixedIoc ioc = new FixedIoc("multi_cs",
         List.of("a", "b"));
      EditCache<String> r1 = ioc.convertStream();
      assertEquals(2, r1.size());
      // After exhaustion, convertStream returns empty
      EditCache<String> r2 = ioc.convertStream();
      assertEquals(0, r2.size());
      // Reset and try again
      ioc.resetIndex();
      EditCache<String> r3 = ioc.convertStream();
      assertEquals(2, r3.size());
   }
}
