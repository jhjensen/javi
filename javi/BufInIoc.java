
package javi;
 
import java.io.BufferedReader;
import java.io.IOException;

/** this is an abstract class which defines the necessary methods
    for an editvec to do IO
*/

abstract class BufInIoc<OType>  extends IoConverter<OType>  {
transient public BufferedReader  input;

final static String copyright = "Copyright 1996 James Jensen";

BufInIoc(FileProperties fp,boolean initThread) {
   super(fp,initThread);
}

public void dispose() throws IOException {
   super.dispose();
   if (input !=null)
      input.close();
}

EditCache<OType> convertStream(BufferedReader inputi)
      throws ReadOnlyException,IOException {
  //trace("index = " + index + " " + " input = " + inputi);
  input=inputi;
 
  return super.convertStream();
}

}
