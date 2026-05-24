package javi;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import history.Tools;
import static history.Tools.trace;

/**
 * Searches GNU ID Utils databases using gid or lid.
 * Runs the command and parses grep-format output into Position objects.
 */
final class MkidSearch extends PositionIoc {

   private static final long serialVersionUID = 1;

   private static final Pattern GID_LINE = Pattern.compile(
      "^([^:]+):(\\d+):(.*)$");

   private MkidSearch(String label, String[] cmd) throws IOException {
      super(label, Tools.runcmd(cmd), pconverter);
   }

   @Override
   void dorun() {
      Matcher matcher = GID_LINE.matcher("");
      for (String line; null != (line = getLine());) {
         if (matcher.reset(line).matches()) {
            String file = matcher.group(1);
            int lineNum = Integer.parseInt(matcher.group(2));
            String text = matcher.group(3);
            addElement(new Position(0, lineNum, file, text));
            resultCount++;
         } else {
            trace("MkidSearch: unparseable line: " + line);
         }
      }
   }

   static TextEdit<Position> gidSearch(String pattern) throws
         IOException, InputException {
      checkIdFile();
      String[] cmd = {"gid", pattern};
      MkidSearch search = new MkidSearch("gid " + pattern, cmd);
      return PosListList.Cmd.replacePositionIoc("gid", search);
   }

   static TextEdit<Position> lidSearch(String pattern) throws
         IOException, InputException {
      checkIdFile();
      String[] cmd = {"lid", "--result=grep", pattern};
      MkidSearch search = new MkidSearch("lid " + pattern, cmd);
      return PosListList.Cmd.replacePositionIoc("lid", search);
   }

   private static void checkIdFile() throws InputException {
      if (!new File("ID").isFile())
         throw new InputException(
            "no ID database found — run mkid to create one");
   }
}
