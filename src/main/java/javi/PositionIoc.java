package javi;

import java.io.BufferedReader;
import static history.Tools.trace;

/**
 * I/O converter that parses position information from build tool output.
 *
 * <p>PositionIoc reads lines from a BufferedReader (typically from a build
 * command like make or gcc) and parses them into {@link Position} objects
 * representing error/warning locations. Lines that cannot be parsed as
 * positions are tracked but skipped.
 *
 * <p>When processing completes, a status message is displayed showing the
 * number of parsed positions and the last line of output (which often
 * contains useful summary info like "BUILD SUCCESSFUL").
 *
 * @see Position
 * @see MakeCmd
 */
public class PositionIoc extends BufInIoc<Position> {

   static final PositionConverter pconverter = new PositionConverter();
   static final Position defpos = new Position(0, 0, "", null);
   /** Count of results found, accessible to subclasses for custom reporting. */
   protected int resultCount = 0;
   /** The last non-empty output line read, for display in completion message. */
   private String lastOutputLine = null;
   /** Maximum length for the last output line in the status message. */
   private static final int MAX_OUTPUT_LINE_LENGTH = 200;
   private static final long serialVersionUID = 1;

   private static final class PositionConverter
           extends ClassConverter<Position> {
      private static final long serialVersionUID = 1;
      public Position fromString(String s) {
         //trace(s);
         if (0 == s.length()) {
            return defpos;
         }
         int posx = s.indexOf('(');
         String filename = s.substring(0, posx++);
         int poscom = s.indexOf('-', posx + 1);
         int posy = s.indexOf(',', posx + 1);
         int x, y;
         if ((posy > poscom) || (posy == -1)) {
            y = Integer.parseInt(s.substring(posx, poscom - 1));
            x = 0;
         } else {
            //trace(s.substring(posx,posy));
            x = Integer.parseInt(s.substring(posx, posy));
            //trace(s.substring(posy+1,poscom));
            y = Integer.parseInt(s.substring(posy + 1, poscom - 1));
         }
         String comment = s.substring(poscom + 1, s.length());
         return new Position(x, y, filename, comment);
      }
   }

   /**
    * Parses lines from the input stream looking for position information.
    *
    * <p>Each line read is stored as the last output line for the completion
    * message. Lines that can be parsed as positions are returned; unparseable
    * lines are skipped.
    *
    * @return the next Position found, or null if no more input
    */
   private Position parsefile() {
      //trace("parsefile this " + this.getClass());

      for (String line; null != (line = getLine());) {
         //trace("line = " + line);
         // Track last non-empty line for completion message
         if (line.length() != 0) {
            lastOutputLine = line;
         }
         if ("done".equals(line)) {
            trace("should exit immediatly");
         } else if (line.length() != 0)
            try {
               return prop.conv.fromString(line);
            } catch (final Exception e) {
               //trace("line len " + line.length());
               //trace("positionioc.parsefile line =:" + line + ": exception = " + e + this);
            }
      }
      return null;
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   private PositionIoc(String name) {
      super(new FileProperties(
         FileDescriptor.InternalFd.make(name), pconverter), true, null);
      //trace(label);
   }

   @SuppressWarnings({"unchecked", "rawtypes"})
   public PositionIoc(String name, BufferedReader inputi,
      ClassConverter converteri) {

      super(new FileProperties(FileDescriptor.InternalFd.make(name),
         converteri), true, inputi);
   }

   /**
    * Adds a position result and increments the result count.
    *
    * <p>Subclasses that build positions outside of {@link #getnext()} (e.g.,
    * via compiler API callbacks or direct file scanning) should call this
    * method instead of {@link #addElement} to ensure {@link #resultCount}
    * is accurately tracked.
    *
    * @param pos the position to add
    */
   protected void addResult(Position pos) {
      addElement(pos);
      resultCount++;
   }

   /**
    * Formats the completion message including the last output line.
    *
    * <p>The message format is: "{name} complete {count} results: {lastLine}"
    * The last line is truncated if it exceeds {@link #MAX_OUTPUT_LINE_LENGTH}
    * characters. If there are no results and no output, reports "no output".
    *
    * <p>Subclasses may override this to provide a custom completion message.
    *
    * @return the formatted completion message
    */
   protected String formatCompletionMessage() {
      StringBuilder msg = new StringBuilder();
      msg.append(this).append(" complete ").append(resultCount).append(" results");

      if (null != lastOutputLine && lastOutputLine.length() > 0) {
         msg.append(": ");
         if (lastOutputLine.length() > MAX_OUTPUT_LINE_LENGTH) {
            msg.append(lastOutputLine.substring(0, MAX_OUTPUT_LINE_LENGTH - 3));
            msg.append("...");
         } else {
            msg.append(lastOutputLine);
         }
      } else if (0 == resultCount) {
         msg.append(": no output");
      }
      return msg.toString();
   }

   /**
    * Reports the completion message to the status bar.
    *
    * <p>Subclasses may override this to change how or whether the
    * completion message is reported.
    */
   protected void reportCompletion() {
      if (0 != toString().length())
         UI.reportMessage(formatCompletionMessage());
   }

   @Override
   protected void postRun() {
      reportCompletion();
   }

   /**
    * Returns the next parsed position from the input stream.
    *
    * <p>When no more positions are available the stream is exhausted
    * and null is returned. Completion reporting is handled by
    * {@link #postRun()} after the run loop finishes.
    *
    * @return the next Position, or null if no more input
    */
   public final Position getnext() {
      //trace("getnext input " + " this " + this);

      Position pos = parsefile();
      //trace("get next got pos " + pos);
      if (null != pos) {
         resultCount++;
      }
      return pos;
   }
}
