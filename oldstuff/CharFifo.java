package javi;
 
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

final class CharFifo {
    private char[]  store = new char[200];
    private int size = 200;
    private int head=0;
    private int tail=0;

    synchronized int getChar() {
       if (head==tail) {
          notifyAll();
          return -1;
       }
       int retval = store[tail++];
       if (tail>=size)
          tail =0;
       //ui.trace("getChar retval " + Integer.toHexString(retval) + " head " + head + " tail " +tail );
       return retval;
    }

    private void append(char ch) {
       store[head]=ch;

       if (++head>=size)
          head=0;

       if (head==tail) {
          int newsize = size*2;
          char [] newstore = new char[newsize];
          Tools.trace("Char Fifo reallocated " + newsize);
          System.arraycopy(store,head,newstore,0,size-head);
          System.arraycopy(store,0,newstore,size-head,head);
          head=size;
          tail=0;
          size=newsize;
          store=newstore;
      }
      //ui.trace("appended " + ch  + " head " + head);
    }

    synchronized void resize(int newsize) {
       char [] newstore = new char[newsize];
       Tools.trace("Char Fifo resized " + newsize);
       if (head>tail) {
           System.arraycopy(store,tail,newstore,0,head-tail);
           head = tail-head;
       } else if (head <tail){
          System.arraycopy(store,tail,newstore,0,size-tail);
          System.arraycopy(store,0,newstore,size-tail,head);
          head=size -tail + head;
       }
       tail=0;
       size=newsize;
       store=newstore;
   }
/*      
    synchronized void addStreamUni(InputStream ins,EventQueue.IEvent event) throws IOException,InterruptedException {
               append((char)(ins.read() + (ins.read() >>8)));
               while (ins.available()>1)
                  append((char)(ins.read() + (ins.read() >>8)));
               if (event!=null) {
                  EventQueue.eventq.insert(event);
                  wait();
              }
     }
*/

     synchronized void addStreamByte(InputStream ins,EventQueue.IEvent event) throws IOException ,InterruptedException {
         for (;;) {
               int inchar = ins.read();

               if (inchar ==-1) 
                  throw new IOException("end of input stream reached");

               append((char)inchar);
               while (ins.available()>0)
               //oportunity for optimization, read into array. ???
                  append((char)ins.read());
               if (event!=null) {
                  //UI.trace("addStreambyte waking up event");
                  EventQueue.insert(event);
                  wait();
              }
         }
     }
   // test routine
   private synchronized StringBuffer fillBuf(StringBuffer sb) {
      //ui.trace("getString head " + head + " tail " +tail + " sb " + sb);
      if (sb==null)
         sb = new StringBuffer(size);
      
       if (head>tail) {
           sb.insert(0,store,tail,head-tail);
       } else {
          sb.insert(0,store,tail,size-tail);
          sb.insert(size-tail,store,0,head);
       }
       head=0;
       tail=0;
       Tools.trace("sb:" +sb);
       return sb;
   }
      
   public static void main (String args[]) {
      try {

        // basic fillBuf operation
        CharFifo fifo = new CharFifo();
        fifo.resize(10);
        byte [] str1= {'a','b','c','d','e','f','g','h','i'};
        
        fifo.addStreamByte(new ByteArrayInputStream(str1),null);
        StringBuffer sb = fifo.fillBuf(null);
        Tools.Assert("abcdefghi".equals(sb.toString()),sb);
        
        // basic getChar operation

        fifo.resize(10);
        byte [] str2= {'a','b'};
        fifo.addStreamByte(new ByteArrayInputStream(str2),null);
        Tools.Assert(fifo.getChar() == 'a',fifo);
        Tools.Assert(fifo.getChar() == 'b',fifo);

        // resize head <tail
        byte [] str3= {'a','b','c','d','e','f','g','h','i','j','k','l','m','n'};
        
        fifo.addStreamByte(new ByteArrayInputStream(str3),null);
        sb = fifo.fillBuf(null);
        Tools.Assert("abcdefghijklmn".equals(sb.toString()),sb);

        // resize head > tail
        fifo.resize(10);
        fifo.addStreamByte(new ByteArrayInputStream(str1),null);
        Tools.Assert(fifo.getChar() == 'a',fifo);
        Tools.Assert(fifo.getChar() == 'b',fifo);
        Tools.Assert(fifo.getChar() == 'c',fifo);
        Tools.Assert(fifo.getChar() == 'd',fifo);
        Tools.Assert(fifo.getChar() == 'e',fifo);
        fifo.addStreamByte(new ByteArrayInputStream(str1),null);
        sb = fifo.fillBuf(null);
        Tools.Assert("fghiabcdefghi".equals(sb.toString()),sb);

        Tools.trace("test executed successfully");
      } catch (Throwable e) {
         Tools.trace("main caught exception " + e);
         e.printStackTrace();
      }
      System.exit(0);
     }
     
}
