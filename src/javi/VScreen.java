package javi;

abstract class VScreen {
   abstract void incX(int amount, StringBuffer sb);
   abstract void incY(int amount, StringBuffer sb);
   abstract void setX(int val, StringBuffer sb);
   abstract void setY(int val, StringBuffer sb);
   abstract void setXY(int xval, int yval, StringBuffer sb);
   abstract void eraseScreen(StringBuffer sb);
   abstract void eraseToEnd(StringBuffer sb);
   abstract void eraseLine(StringBuffer sb);
   abstract void eraseChars(int count, StringBuffer sb);
   abstract void insertLines(int count, StringBuffer sb);
   abstract void setInsertMode(boolean val, StringBuffer sb);
   abstract void updateScreen(StringBuffer sb);
   abstract void saveCursor(StringBuffer sb);
   abstract void restoreCursor(StringBuffer sb);
}
