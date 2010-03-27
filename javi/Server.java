package javi;

import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

class Server implements Runnable,FileStatusListener {

   //vic serv;
   HashMap<EditContainer,BufferedOutputStream> shash=new HashMap<EditContainer,BufferedOutputStream>();
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
         BufferedOutputStream outstream=new BufferedOutputStream(sock.getOutputStream());

         BufferedReader instream=new BufferedReader(new InputStreamReader(sock.getInputStream(),"UTF-8"));
         
         if (1!= instream.read())  {
            throw new InputException("invalid byte from remote");
         }
/*
         StringBuilder sb = new StringBuilder();
         while(true) {
           String fname = DataInputStream.readUTF(instream);
           Tools.trace("Server read in name " + fname);
           if (fname.length()==0)
              break;
           sb.append(DataInputStream.readUTF(instream));
           sb.append("\n");
         }
         Tools.trace("editing line:" + sb + ":");
*/
         EditContainer ed = FileList.openFileList(instream,null);
         if (ed!=null) {
            shash.put(ed,outstream);
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
      BufferedOutputStream outstream = shash.get(ev);
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
