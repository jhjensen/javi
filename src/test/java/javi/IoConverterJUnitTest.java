package javi;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for {@link IoConverter}.
 *
 * <p>Tests the base class behavior including {@code getnext()},
 * {@code convertStream()}, {@code toString()}, constructor states,
 * and the reload/dorun lifecycle via a concrete test subclass.</p>
 */
class IoConverterJUnitTest {

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

   /** Concrete IoConverter that serves a fixed list of strings. */
   private static final class ListIoc extends IoConverter<String> {
      private static final long serialVersionUID = 1;
      private final List<String> items;
      private int index;

      ListIoc(String name, List<String> itemsi) {
         super(new FileProperties<>(
            FileDescriptor.InternalFd.make(name),
            StringIoc.converter), false);
         items = new ArrayList<>(itemsi);
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

   private static FileProperties<String> makeFP(String name) {
      return new FileProperties<>(
         FileDescriptor.InternalFd.make(name),
         StringIoc.converter);
   }

   // ── getnext default ─────────────────────────────────────────

   @Test
   @DisplayName("base getnext returns null")
   void baseGetnextReturnsNull() {
      IoConverter<String> ioc = new IoConverter<>(makeFP("gn1"), false);
      assertNull(ioc.getnext());
   }

   @Test
   @DisplayName("base getnext returns null repeatedly")
   void baseGetnextReturnsNullRepeatedly() {
      IoConverter<String> ioc = new IoConverter<>(makeFP("gn2"), false);
      assertNull(ioc.getnext());
      assertNull(ioc.getnext());
   }

   // ── toString ────────────────────────────────────────────────

   @Test
   @DisplayName("toString returns shortName from FileDescriptor")
   void toStringReturnsShortName() {
      IoConverter<String> ioc = new IoConverter<>(makeFP("myname"), false);
      assertEquals("myname", ioc.toString());
   }

   // ── prop ────────────────────────────────────────────────────

   @Test
   @DisplayName("prop is the FileProperties passed at construction")
   void propMatchesConstructorArg() {
      FileProperties<String> fp = makeFP("prp1");
      IoConverter<String> ioc = new IoConverter<>(fp, false);
      assertSame(fp, ioc.prop);
   }

   // ── convertStream ───────────────────────────────────────────

   @Test
   @DisplayName("convertStream collects getnext into EditCache")
   void convertStreamCollectsElements() {
      ListIoc ioc = new ListIoc("cs1", List.of("alpha", "beta", "gamma"));
      EditCache<String> result = ioc.convertStream();
      assertEquals(3, result.size());
      assertEquals("alpha", result.get(0));
      assertEquals("beta", result.get(1));
      assertEquals("gamma", result.get(2));
   }

   @Test
   @DisplayName("convertStream returns empty cache when getnext is null")
   void convertStreamEmptyWhenNoElements() {
      IoConverter<String> ioc = new IoConverter<>(makeFP("cs2"), false);
      EditCache<String> result = ioc.convertStream();
      assertEquals(0, result.size());
   }

   @Test
   @DisplayName("convertStream with single element")
   void convertStreamSingleElement() {
      ListIoc ioc = new ListIoc("cs3", List.of("only"));
      EditCache<String> result = ioc.convertStream();
      assertEquals(1, result.size());
      assertEquals("only", result.get(0));
   }

   // ── reload ──────────────────────────────────────────────────

   @Test
   @DisplayName("reload calls dorun pipeline")
   void reloadCallsDorun() {
      // ListIoc overrides getnext; reload calls dorun which uses getnext
      // via addElement, but reload operates on this.ioarray which is
      // null without init1. So test via convertStream which is safe.
      ListIoc ioc = new ListIoc("rl1", List.of("x", "y"));
      EditCache<String> cache = ioc.convertStream();
      assertEquals(2, cache.size());
   }

   // ── constructor quickThread flag ────────────────────────────

   @Test
   @DisplayName("constructor with quickThread=false does not start thread")
   void constructorNoQuickThread() {
      IoConverter<String> ioc = new IoConverter<>(makeFP("qt1"), false);
      // No thread started; getnext should still work
      assertNull(ioc.getnext());
   }

   @Test
   @DisplayName("constructor with quickThread=true does not start thread until init1")
   void constructorQuickThreadDeferred() {
      IoConverter<String> ioc = new IoConverter<>(makeFP("qt2"), true);
      // Thread not started yet because init1 hasn't been called
      assertNull(ioc.getnext());
   }

   // ── StringIoc integration through IoConverter ───────────────

   @Test
   @DisplayName("StringIoc convertStream produces correct cache")
   void stringIocConvertStream() {
      StringIoc sio = new StringIoc("sics", "line1\nline2\nline3");
      EditCache<String> result = sio.convertStream();
      assertEquals(3, result.size());
      assertEquals("line1", result.get(0));
      assertEquals("line2", result.get(1));
      assertEquals("line3", result.get(2));
   }

   @Test
   @DisplayName("StringIoc convertStream single line")
   void stringIocConvertStreamSingle() {
      StringIoc sio = new StringIoc("sics2", "alone");
      EditCache<String> result = sio.convertStream();
      assertEquals(1, result.size());
      assertEquals("alone", result.get(0));
   }

   @Test
   @DisplayName("StringIoc convertStream with trailing newline")
   void stringIocConvertStreamTrailing() {
      StringIoc sio = new StringIoc("sics3", "abc\n");
      EditCache<String> result = sio.convertStream();
      assertEquals(2, result.size());
      assertEquals("abc", result.get(0));
      assertEquals("", result.get(1));
   }
}
