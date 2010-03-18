package javi;
 
import java.io.IOException;


/** this is an abstract class which defines the necessary methods
    for an editvec to to IO
*/
public abstract class HackIo<OType> extends BufInIoc<OType> {
//int iodelay=0;
public HackIo(FileDescriptor fdes){
   super(fdes);
}

public OType getnext() {
     //trace("iodelay " + iodelay );
     OType retval=null;
     String line = null;
     if (input==null )
        return null;
out: do try {
/*
 *         int count = 0;

        while (!input.ready()) { // total hack because java doesn't work
              Thread.sleep(100);
              if (input.ready())
                 break;
              trace("count = " +count);
              if (count++ >iodelay) {
                 trace("timing out count "  + count + " iodelay " + iodelay);
                 break out;
              }
         }
*/
              
         line = input.readLine();
//         trace("reading line " + line);
         if (line==null)
                 break out;
         retval =fromString(line);
//trace("retval =  " + retval);
/*
     } catch (InterruptedException e) {
       trace("getnexterror caught " + e + " line = " + line);
                 break out;
*/
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
//trace("returning " + retval);
  return retval;
} 

void init1(EditContainer<OType> evi) {
  super.init1(evi);
  threadstart=true;
}

}
