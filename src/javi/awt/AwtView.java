package javi.awt;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;

import javi.View;

//import static history.Tools.trace;

abstract class AwtView  extends View {

   abstract Shape updateCursorShape(Shape sh);

   protected static final transient int inset = 2;
   private transient Color cursorcolor =  AtView.cursorColor;
   private transient Shape cursorshape;

   protected AwtView(boolean traversei) {
      super(traversei);
      //trace("created view " + this);
   }

   final void bcursor(Graphics2D gr) {

      int bfla = needBlink();
      //trace("bcursor bfla " + Integer.toHexString(bfla));

      if (0 != (bfla & updateCursor))
         cursorshape = updateCursorShape(cursorshape);

      if (0 != (bfla & doBlink)) {
         if (0 != (bfla & onFlag))
            cursorcolor = 0 ==  (bfla & insertFlag)
               ? AtView.cursorColor
               : AtView.insertCursor;

         //trace("doCursor cursoron " + cursoron + " cursorColor " + cursorcolor);
         gr.setXORMode(cursorcolor);
         gr.setColor(AtView.background);
         gr.fill(cursorshape);
         gr.setPaintMode();
         //trace("doCursor cursoron " + cursoron);

      }
   }


}
