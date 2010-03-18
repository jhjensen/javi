package javi;
 
import java.io.IOException;

/** this is an abstract class which defines the necessary methods
    for an editvec to to IO
*/
abstract class bashio extends iocontroller {
int iodelay=0;
Object getnext() {
     //trace("entered");
     Object retval=null;
     String line = null;
     if (input==null )
        return null;
out: do try {
        String file;
        String comment;
        int x,y,pos;
        int count = 0;
        while (!input.ready()) { // total hack because java doesn't work
              Thread.sleep(100);
              if (input.ready())
                 break;
              //trace("getnext count = " +count);
              if (count++ >iodelay) {
                 trace("timing out");
                 break out;
              }
         }
              
         line = input.readLine();
         if (line==null)
                 break out;
         retval =parseline(line);
     } catch (InterruptedException e) {
       trace("getnexterror caught " + e + " line = " + line);
                 break out;
     } catch (IOException e) {
       trace("getnexterror caught " + e + " line = " + line);
     } 
        while  (retval==null);
  if (retval==null) {
     try { 
        input.close();
     } catch (IOException e) {
       trace("getnexterror caught " + e + " line = " + line);
     } 
     input=null;
  }
  //trace("get next returning " + retval);
  return retval;
} 
void init(editvec evi) {
  super.init(evi);
  threadstart=true;
}
}

