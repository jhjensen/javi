package javi.findbugs;

import static history.Tools.trace;
public final class FindBugsFactory implements javi.PluginFactory {
   @Override
   public String name() {
      return "findbugs";
   }

   @Override
   public javi.Plugin create(java.util.List<String> args) throws javi.InputException {
      if (args.size() != 0)
         throw new javi.InputException("unexpected arguments to findbugs plugin creation");
      trace("about to register");
      return new FindBugs();
   }
}
