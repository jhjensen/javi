package javi.ai;

import static history.Tools.trace;
public final class AiFactory implements javi.PluginFactory {
   @Override
   public String name() {
      return "typingtutor";
   }

   @Override
   public javi.Plugin create(java.util.List<String> args) throws javi.InputException {
      if (args.size() != 2)
         throw new javi.InputException("unexpected arguments to ai creation");
      trace("about to config");
      new AIConfig(args.get(0), args.get(1));
      trace("about to register");
      return new AICommands(args);
   }
}
