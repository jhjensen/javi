package javi;
import junit.framework.TestCase;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;

class listTest extends TestCase {
   filelist flist;
   listTest(String name) {
      super(name);
   }
   public void setUp() throws IOException {
      makeFile("xx","a\nb\n");
      makeFile("yy","c\nd\n");
      flist = new filelist("xx yy");
   }

public static void main (String args[]) {
   try {
      listTest lt = new listTest("asdfsaf");
      lt.setUp();
      lt.testdelete();
   } catch (Exception e) {
      System.err.println("main caught exception " + e);
      e.printStackTrace();
      System.exit(0);
   }
   System.err.println("all tests passed");
}
   
   void testdelete() throws IOException {
     view vi = new oldview();
     fvcontext fvc = fvcontext.getcontext(vi,flist);
     assertTrue(flist.finish()==3);
     Object[] ev =  flist.remove( 1,1);
//     editvec ev0 = (editvec)ev[0];
     flist.checkpoint();
     assertTrue(" finish = " + flist.finish(),flist.finish()==2);
     flist.insert( ev,1);
     flist.checkpoint();
     assertTrue(" finish = " + flist.finish(),flist.finish()==3);
     flist.undo();
     assertTrue(" finish = " + flist.finish(),flist.finish()==2);
     flist.undo();
     assertTrue(" finish = " + flist.finish(),flist.finish()==3);
     ev = flist.getElementsAt(1,1);
     editvec ev0 = (editvec)ev[0];
     ev0.finish();
     assertTrue("a".equals(ev0.at(1)));
   }

static void copyFile(String from,String to) throws IOException {
      FileInputStream input = new FileInputStream(from);
      int length=(int)(new File(from)).length();
      byte[] iarray=new byte[length];
      int ilen = input.read(iarray,0,length);
      if (ilen!=length)
         throw new RuntimeException("copyFile: read in length doesnt match");
      input.close(); 
      FileOutputStream output = new FileOutputStream(to);
      output.write(iarray);
      output.close();
}
static void makeFile(String filename,String contents) throws IOException {
     FileWriter fs = new FileWriter(filename);
     fs.write(contents);
     fs.close();
}
static void checkFile(String filename,String contents) throws IOException {
     File f = new File(filename);
     char [] fchar = new char[(int)f.length() + 20];
     FileReader fs = new FileReader(filename);
     int len = fs.read(fchar,0,fchar.length);
     fs.close();
     if (len != contents.length())
        throw new IOException("file length unexpected:" + len);

     String str = new String(fchar,0,len);
     if (!str.equals(contents))
        throw new IOException("file contents not equal");
        
     
}
}
