package javi.git;

import static history.Tools.trace;
public final class GitFactory implements javi.PluginFactory {
   @Override
   public String name() {
      return "git";
   }

   @Override
   public javi.Plugin create(java.util.List<String> args) throws javi.InputException {
      if (args.size() != 2)
         throw new javi.InputException("unexpected arguments to git plugin creation");
      trace("about to register");
      return new GitCommands(args);
   }
}
