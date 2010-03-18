package javi;

import java.io.IOException;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

class Server implements Runnable,FileStatusListener {

   //vic serv;
   HashMap<EditContainer,DataOutputStream> shash=new HashMap<EditContainer,DataOutputStream>();
   ServerSocket lsock;

   Server(int port) throws IOException {
      lsock=new ServerSocket(port);
      new Thread(this,"VI Server Thread").start();
      EditContainer.registerListener(this);
   }

   public void run() {
      Socket sock=null;
      while(true) try {
         sock=lsock.accept();
         DataOutputStream outstream=new DataOutputStream(sock.getOutputStream());

         DataInputStream instream=new DataInputStream(sock.getInputStream());
         short len;
         instream.readShort(); // read in dummy length
         if (1!= instream.readByte()) {
            throw new InputException("invalid byte from remote");
         }
         len = instream.readShort(); // read number of arguments

         StringBuilder sb = new StringBuilder();
         for(int i = 0;i<len;i++)  {
           sb.append(DataInputStream.readUTF(instream));
           sb.append("\n");
         }
         Tools.trace("editing line:" + sb + ":");
         FvContext fv = (FvContext)Rgroup.doroutine("vi",sb.toString(),1,1,FvContext.getCurrFvc(), false);
         if (fv!=null) {
            shash.put(fv.edvec,outstream);
            UI.toFront();
         }
      } catch (Throwable e) {
         try {
           if (sock !=null)
              sock.close();
         } catch (IOException e1) {
            Tools.trace("unexpected exception " + e1);
         }
         Tools.trace("server.run caught exception " + e);
         e.printStackTrace();
      }
   }


   void donefile(EditContainer ev) {
      //ui.trace("server.donefile entered " + ev);
      DataOutputStream outstream = shash.get(ev);
      if (outstream==null)
         return;
      try {
         outstream.write(1);
         outstream.close();
         shash.remove(ev);
         UI.hide();
      } catch (IOException e) {
       Tools.trace("server.donefile caught exception " + e);
      }

   }
   public boolean fileDisposed(EditContainer ev) {// ??? should undo positionlist fixes ??
      donefile(ev);
      return false;
   }
   public void fileWritten(EditContainer ev) {
      donefile(ev);
   }
   public void fileAdded(EditContainer ev) {/* don't care */}

}
