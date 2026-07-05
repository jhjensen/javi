package javi.rxtx;

import static history.Tools.trace;
public final class RxtxFactory implements javi.PluginFactory {
   @Override
   public String name() {
      return "rxtx";
   }

   @Override
   public javi.Plugin create(java.util.List<String> args) throws javi.InputException {
      if (args.size() != 0)
         throw new javi.InputException("unexpected arguments to rxtx creation");
      trace("about to register rxtx");
      return new RxtxPlugin();
   }
}
