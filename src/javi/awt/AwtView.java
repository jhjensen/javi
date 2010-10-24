package javi.awt;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;

import javi.View;
import static javi.View.Opcode.*;

//import static history.Tools.trace;

public abstract class AwtView  extends View {

//   abstract Position mousepos(MouseEvent event);
   abstract Shape updateCursorShape(Shape sh);
//   abstract void ssetFont(Font font);

   /* Copyright 1996 James Jensen all rights reserved */
   private static final String copyright = "Copyright 1996 James Jensen";

   abstract static class Inserter {
      abstract String getString();
      abstract boolean getOverwrite();
   }

   protected static final transient int inset = 2;
   private transient Color cursorcolor =  AtView.cursorColor;
   private transient Shape cursorshape;

   AwtView(boolean traversei) {
      super(traversei);
      //trace("created view " + this);
   }

   public final void bcursor(Graphics2D gr,
         boolean isInsert, boolean updateShape) {

      // never move cursor or change cursor color except when off
      //trace("bcursor cursoron " + cursoron + " checkCursor " + checkCursor);
      if (updateShape)
         cursorshape = updateCursorShape(cursorshape);

      cursorcolor =  isInsert
         ? AtView.cursorColor
         : AtView.insertCursor;

      //trace("doCursor cursoron " + cursoron + " cursorColor " + cursorcolor);
      gr.setXORMode(cursorcolor);
      gr.setColor(AtView.background);
      gr.fill(cursorshape);
      gr.setPaintMode();
      //trace("doCursor cursoron " + cursoron);

   }

   final int placeline(int lineno, float amount) {
      int row = getRows(amount);
      screenFirstLine();
      row =  lineno - screenFirstLine() - row;
      return screeny(row);
   }

}
