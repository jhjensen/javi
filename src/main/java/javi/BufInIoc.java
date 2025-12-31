package javi;

import java.io.BufferedReader;
import java.io.IOException;
import static history.Tools.trace;

/** this is an abstract class which defines the necessary methods
    for an editvec to do IO
*/

public class BufInIoc<OType>  extends IoConverter<OType>  {
   private transient BufferedReader  input;
   private static final long serialVersionUID = 1;

   protected final String getLine() {
      if (null == input)
         return null;
      try {
         String retval = input.readLine();
         if (null != retval)
            return retval;
      } catch (IOException e) {
         trace("getLine caught " + e);
      }
      try {
         input.close();
         input = null;
      } catch (IOException e) {
         trace("getLine caught " + e);
      }
      return null;

   }

/*
   protected final String getLine2() {
      try {
         if (input == null)
            return null;
         int c;

         do
            c = input.read();
         while (c == '\n' || c == '\r');

         if (c != -1) {
            String retval = input.readLine();
            if (retval != null)
               return c + retval;
         }
      } catch (IOException e)  {
         trace("getLine2 caught " + e);
      }
      try {
         input.close();
      } catch (IOException e)  {
         trace("getLine2 caught " + e);
      }
      input = null;
      return null;
   }
*/

   @SuppressWarnings({"unchecked", "rawtypes"})
   public BufInIoc(FileProperties fp,
         boolean initThread, BufferedReader inputi) {
      super(fp, initThread);
      input = inputi;
   }

   public void dispose() throws IOException {
      super.dispose();
      if (null != input)
         input.close();
   }

   final EditCache<OType> convertStream(BufferedReader inputi) throws
         IOException {
      //trace("index = " + index + " " + " input = " + inputi);
      input = inputi;

      return super.convertStream();
   }

}
