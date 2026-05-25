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

   /**
    * Search the ID database for file names matching a pattern.
    * Each match produces a position pointing to line 1 of the file.
    */
   static TextEdit<Position> fnidSearch(String pattern) throws
         IOException, InputException {
      checkIdFile();
      String[] cmd = {"fnid", pattern};
      FnidSearch search = new FnidSearch("fnid " + pattern, cmd);
      return PosListList.Cmd.replacePositionIoc("fnid", search);
   }

   private static void checkIdFile() throws InputException {
      if (!new File("ID").isFile())
         throw new InputException(
            "no ID database found — run mkid to create one");
   }
}

/**
 * Parses fnid output (one filename per line) into Position objects at line 1.
 */
final class FnidSearch extends PositionIoc {

   private static final long serialVersionUID = 1;

   FnidSearch(String label, String[] cmd) throws IOException {
      super(label, Tools.runcmd(cmd), pconverter);
   }

   @Override
   void dorun() {
      for (String line; null != (line = getLine());) {
         line = line.trim();
         if (!line.isEmpty()) {
            addElement(new Position(0, 1, line, "fnid:" + line));
            resultCount++;
         }
      }
   }
}
