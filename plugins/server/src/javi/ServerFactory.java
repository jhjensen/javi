package javi.server;

//import javi.Server;
import static history.Tools.trace;
public final class ServerFactory implements javi.PluginFactory {
   @Override
   public String name() {
      return "Server";
   }

   @Override
   public javi.Plugin create(java.util.List<String> args) throws javi.InputException {
      int port = 0;
      if (args.size() != 0)
         port = Integer.parseInt(args.get(0));
      else {

      // Start server on configured port
         String portProp = System.getProperty("javi.server.port");
         if (portProp != null) {
            try {
               port = Integer.parseInt(portProp);
            } catch (NumberFormatException e) {
               trace("invalid javi.server.port: " + portProp);
            }
         }
      }
      return Server.startServer(port);
   }
}
