package javi;

public final class StringIoc extends IoConverter<String> {
   private static final class StringConverter extends ClassConverter<String> {

      public String fromString(String str) {
         return str;
      }

   }
   //private static  StringConverter stringConverter = new StringConverter();


   private String input;

   public StringIoc(String name, String value) {
      super(new FileProperties(FileDescriptor.InternalFd.make(name),
         converter), true);
      input = value;
   }

   public String getnext() {
      String retval = null;
      if (input != null) {
         int nindex = input.indexOf('\n');
         if (nindex < 0) {
            retval = input;
            input = null;
         } else {
            retval = input.substring(0, nindex);
            input = input.substring(nindex + 1);
         }
      }
      return retval;
   }
   static final StringConverter converter = new StringConverter();

}
