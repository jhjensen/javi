package javi;

import java.io.Serializable;
import java.io.DataOutputStream;
import java.io.IOException;

public abstract class ClassConverter<OType>  implements Serializable {

   public abstract OType fromString(String s);

   final OType newExternal(history.ByteInput dis) {
      return fromString(dis.readUTF());
   }

   final void saveExternal(Object ob, DataOutputStream dos) throws IOException {
      dos.writeUTF(ob.toString());
   }
   static void trace(String str) {
      Tools.trace(str, 1);
   }
}
