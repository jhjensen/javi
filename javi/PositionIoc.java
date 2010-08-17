package javi;

import java.io.BufferedReader;


public class PositionIoc extends BufInIoc<Position> {

   static class PositionConverter extends ClassConverter<Position> {
      public Position fromString(String s) {
         //trace(s);
         if ("".equals(s)) {
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
      private static final Position defpos = new Position(0, 0, "", null);
   }

   private static final PositionConverter converter = new PositionConverter();

   public Position parsefile() {
      //trace("parsefile this " + this.getClass());
      String line;
      while (null != (line = getLine())) {
         //trace("line = " + line);
         if ("done".equals(line)) {
            trace("should exit immediatly");
         } else if (!"".equals(line))
            try {
               return converter.fromString(line);
            } catch (Exception e) {
               //trace("line len " + line.length());
               //trace("positionioc.parsefile line =:" + line + ": exception = " + e + this);
            }
      }
      return null;
   }

   PositionIoc(String name) { //??? should make private
      super(new FileProperties(
         FileDescriptor.InternalFd.make(name), converter), true, null);
      //trace(label);
   }

   public PositionIoc(String name, BufferedReader inputi) {

      super(new FileProperties(FileDescriptor.InternalFd.make(name),
         converter), true, inputi);
   }

   private int errcount = 0;
   public final Position getnext() {
      //trace("getnext input " + input + " this "+ this );

      Position pos = parsefile();
      trace("get next got pos " + pos);
      if (pos == null) {
         UI.reportMessage(this + "complete " + errcount + " results");
      } else {
         if (pos.filename != null) {
            EventQueue.biglock2.lock();
            EditContainer ev =  EditContainer.findfile(pos.filename);
            if (ev != null)
               ev.fixposition(pos);
            EventQueue.biglock2.unlock();
         }
         errcount++;
      }
      return pos;
   }
}
