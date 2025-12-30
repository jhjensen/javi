package javi;

import java.io.BufferedReader;
import static history.Tools.trace;

public class PositionIoc extends BufInIoc<Position> {

   static final PositionConverter pconverter = new PositionConverter();
   static final Position defpos = new Position(0, 0, "", null);
   private int errcount = 0;
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

   private Position parsefile() {
      //trace("parsefile this " + this.getClass());

      for (String line; null != (line = getLine());) {
         //trace("line = " + line);
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

   private PositionIoc(String name) {
      super(new FileProperties(
         FileDescriptor.InternalFd.make(name), pconverter), true, null);
      //trace(label);
   }

   public PositionIoc(String name, BufferedReader inputi,
      ClassConverter converteri) {

      super(new FileProperties(FileDescriptor.InternalFd.make(name),
         converteri), true, inputi);
   }

   public final Position getnext() {
      //trace("getnext input " + " this " + this);

      Position pos = parsefile();
      //trace("get next got pos " + pos);
      if (null == pos) {
         if (0 != toString().length())
            UI.reportMessage(this + "complete " + errcount + " results");
      } else {
         errcount++;
      }
      return pos;
   }
}
