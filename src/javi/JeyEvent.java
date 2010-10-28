package javi;

public class JeyEvent {

   private final int code;
   private final int modifiers;
   private final char ch;

   public JeyEvent(int modifiersi, int keyCode, char keyChar) {
      code = keyCode;
      ch = keyChar;
      modifiers = modifiersi;
   }

   public String toString() {
      return "JeyEvent mod " + modifiers + " code " + code + " char " + ch;
   }

   final char getKeyChar() {
      return ch;
   }

   final boolean isActionKey() {
      return ch == CHAR_UNDEFINED;
   }

   final int getKeyCode() {
      return code;
   }
   final int getModifiers() {
      return modifiers;
   }

   final String eventToString() {
      return KeyGroup.bindtostring(isActionKey(),
         isActionKey()
            ? getKeyCode()
            : getKeyChar(),
         getModifiers());
   }
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
   public static final int CHAR_UNDEFINED = 65535;

}
