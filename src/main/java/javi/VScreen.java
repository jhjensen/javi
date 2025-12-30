package javi;

abstract class VScreen {
   abstract void incX(int amount, StringBuilder sb);
   abstract void incY(int amount, StringBuilder sb);
   abstract void setX(int val, StringBuilder sb);
   abstract void setY(int val, StringBuilder sb);
   abstract void setXY(int xval, int yval, StringBuilder sb);
   abstract void eraseScreen(StringBuilder sb);
   abstract void eraseToEnd(StringBuilder sb);
   abstract void eraseLine(StringBuilder sb);
   abstract void eraseChars(int count, StringBuilder sb);
   abstract void insertLines(int count, StringBuilder sb);
   abstract void setInsertMode(boolean val, StringBuilder sb);
   abstract void updateScreen(StringBuilder sb);
   abstract void saveCursor(StringBuilder sb);
   abstract void restoreCursor(StringBuilder sb);
}
