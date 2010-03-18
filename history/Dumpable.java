package history;
import java.io.DataOutputStream;
public interface Dumpable { //??? make package access?
   void writeExternal(DataOutputStream dos) ;
   void readExternal(ByteInput dis) ;
}
