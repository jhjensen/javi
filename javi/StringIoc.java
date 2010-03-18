package javi;
import java.io.Serializable;

class StringIoc extends IoConverter<String> {
private static class StringConverter extends ClassConverter<String> {

public String fromString(String S) {
  return S;
}

}
static  StringConverter stringConverter = new StringConverter();


String input;

StringIoc(String name,String value) {
   super(new FileProperties(FileDescriptor.InternalFd.make(name), stringConverter),true);
   input = value;
}

public String getnext() {
 String retval=null;
 if (input!=null) {
     int nindex = input.indexOf('\n');
     if (nindex <0) {
        retval = input;
        input = null;
     } else {
        retval = input.substring(0,nindex);
        input = input.substring(nindex+1);
     }
  }
 return retval;
}
static StringConverter converter = new StringConverter();

}
