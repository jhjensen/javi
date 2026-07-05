package javi.typingtutor;

import java.util.List;
import javi.Plugin;
import javi.PluginFactory;

public class TypingTutorFactory implements PluginFactory {
   @Override
   public String name() {
      return "typingtutor";
   }

    @Override
    public Plugin create(List<String> args) {
       return new TypingTutorPlugin();
    }
}
