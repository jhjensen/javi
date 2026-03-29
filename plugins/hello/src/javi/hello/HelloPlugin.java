package javi.hello;

import java.io.IOException;
import javi.FvContext;
import javi.InputException;
import javi.Plugin;
import javi.Rgroup;
import javi.UI;

/**
 * Template plugin demonstrating the javi plugin architecture.
 *
 * <p>A plugin must:</p>
 * <ol>
 *   <li>Extend {@link Rgroup} and implement {@link Plugin}</li>
 *   <li>Register commands via {@link #register(String[])}</li>
 *   <li>Optionally bind keys via {@link Plugin#bindKey}</li>
 *   <li>Implement {@link #doroutine} for command dispatch</li>
 * </ol>
 *
 * <p>The JAR manifest must declare {@code Plugin-Class}
 * pointing to this class.</p>
 */
public final class HelloPlugin extends Rgroup implements Plugin {

   enum Cmd {
      UNUSED,
      HELLO,
   }

   private static final Cmd[] CMDS = Cmd.values();

   public HelloPlugin() {
      final String[] rnames = {
         "",
         "hello",
      };
      register(rnames);
   }

   public Object doroutine(int rnum, Object arg, int count,
         int rcount, FvContext fvc, boolean dotmode)
         throws IOException, InputException {
      switch (CMDS[rnum]) {
         case HELLO:
            String name = (arg instanceof String) ? (String) arg : "world";
            UI.reportMessage("Hello, " + name + "!");
            return null;
         default:
            throw new RuntimeException("HelloPlugin: unknown command");
      }
   }
}
