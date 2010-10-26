
import java.awt.CheckboxMenuItem;
   private static class MyCheckboxMenuItem extends CheckboxMenuItem {

      private static final long serialVersionUID = 1;

      MyCheckboxMenuItem(String label, String command, Menu men,
                         ItemListener listen) {

         super(label);
         addItemListener(listen);
         setActionCommand(command);
         men.add(this);
      }
   }

         new MyCheckboxMenuItem("enableclip", null, popmenu, this);
