package javi;

public final class StringIoc extends IoConverter<String> {
   private static final class StringConverter extends ClassConverter<String> {

      public String fromString(String str) {
         return str;
      }

   }

   private String input;
   static final StringConverter converter = new StringConverter();

   public StringIoc(String name, String value) {
      super(new FileProperties(FileDescriptor.InternalFd.make(name),
         converter), true);
      input = value;
   }

   public String getnext() {
      String retval = null;
      if (input != null) {
         int nindex = input.indexOf('\n');
         if (0 >= nindex) {
            retval = input;
            input = null;
         } else {
            retval = input.substring(0, nindex);
            input = input.substring(nindex + 1);
         }
      }
      return retval;
   }

}
