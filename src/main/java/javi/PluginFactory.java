package javi;

import java.io.IOException;
import java.util.List;

public interface PluginFactory {
    String name();
    Plugin create(List<String> args) throws IOException;
}
