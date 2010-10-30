package javi;

//import static history.Tools.trace;

public final class JeyEvent {

   private final int code;
   private final int modifiers;

   public JeyEvent(int modifiersi, int keyCode, char keyChar) {

      boolean isAct = keyChar == CHAR_UNDEFINED;

      if (!isAct) {
         switch (keyChar) { // for shifted characters the code is shifted, and
            case ' ':           // we don't want to distinguish with the
            case 8:           // modifers except for a few special cases
            case 26:
               break;
            default:
               modifiersi &= ~SHIFT_MASK;
         }
      }

      code = isAct
         ? keyCode
         : keyChar;

      modifiers =
          (modifiersi & (SHIFT_MASK | META_MASK | CTRL_MASK | ALT_MASK))
          | (isAct ? ACT_MASK : 0);
   }

   public int hashCode() {
      // put modifiers in upper bits leaving as much room for
      // chars as possible;
      return code | (modifiers << 27);
   }

   public boolean equals(Object ev) {
      //trace("equals ev " + ev  + " this " + this);

      if (ev instanceof JeyEvent) {
         JeyEvent jv = (JeyEvent) ev;
         return code == jv.code && modifiers == jv.modifiers;
      } else
         return false;
   }

   public String toString() {
      return "JeyEvent mod " + modifiers + " code " + code;
   }

   char getKeyChar() {
      return (modifiers & ACT_MASK) == 0
         ? (char) code
         : CHAR_UNDEFINED;
   }

   int getKeyCode() {
      return code;
   }

   private static final int ACT_MASK = 16;

   public static final int SHIFT_MASK = 1;
   public static final int META_MASK = 4;
   public static final int CTRL_MASK = 2;
   public static final int ALT_MASK = 8;

   public static final int VK_INSERT = 155;
   public static final int VK_PAGE_UP = 33;
   public static final int VK_PAGE_DOWN = 34;
   public static final int VK_END = 35;
   public static final int VK_HOME  = 36;
   public static final int VK_LEFT  = 37;
   public static final int VK_UP = 38;
   public static final int VK_RIGHT = 39;
   public static final int VK_DOWN = 40;
   public static final int VK_F1 = 112;
   public static final int VK_F2 = 113;
   public static final int VK_F3 = 114;
   public static final int VK_F4 = 115;
   public static final int VK_F5 = 116;
   public static final int VK_F6 = 117;
   public static final int VK_F7 = 118;
   public static final int VK_F8 = 119;
   public static final int VK_F9 = 120;
   public static final int VK_F10 = 121;
   public static final int VK_F11 = 122;
   public static final int VK_DELETE = 127;
   public static final char CHAR_UNDEFINED = 65535;

}
