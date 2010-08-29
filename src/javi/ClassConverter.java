package javi;

import java.io.Serializable;
import java.io.DataOutputStream;
import java.io.IOException;

abstract class ClassConverter<OType>  implements Serializable {

   public abstract OType fromString(String s);

   OType newExternal(history.ByteInput dis) {
      return fromString(dis.readUTF());
   }

   void saveExternal(Object ob, DataOutputStream dos) throws IOException {
      dos.writeUTF(ob.toString());
   }
   static void trace(String str) {
      Tools.trace(str, 1);
   }
}
