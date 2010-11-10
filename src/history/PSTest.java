package history;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.NoSuchElementException;
import static history.Tools.trace;

final class PSTest extends Testutil {


   private static  final byte[] b1 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0};
   private static final  byte[] b2 = {11};
   private static  final byte[] b3 = new byte[500];
   private static  final byte[] r3 = {0, 0, 0, 1, 0, 0, 0, 2};

   static void testb1(byte[] bx) {
      myassert(bx.length == b1.length, Integer.valueOf(bx.length));
      myassert(arraycmp(bx, 0, bx.length, b1, 0), dumphex(bx));
   }

   static void testb2(byte[] bx) {
      myassert(bx.length == b2.length, b2);
      myassert(arraycmp(bx, 0, bx.length, b2, 0), b2);
   }
   static void    testr3(byte[] bx)  {
      myassert(bx.length == r3.length, r3);
      myassert(arraycmp(bx, 0, bx.length, r3, 0), r3);
   }
   static void testb3(ByteInput dis) {
      byte[] bx = new byte[dis.available()];
      myassert(bx.length == b3.length, dumphex(bx));
      dis.readFully(bx);
      myassert(arraycmp(bx, 0, bx.length, b3, 0), b3);
   }
   void deletetest() throws IOException {
      File hf = new File("delete.test");
      boolean exceptionFlag = false;
      TestPS ran = new TestPS();
      ran.newFile(hf);
      myassert(ran.cleanClose(), ran);
      PersistantStack.PSIterator iter = ran.createIterator();
      iter.push(b1);
      iter.push(b3);

      iter.decrement();

      iter.idleSave();
      hf.delete();
      try {
         iter.push(b2); // deletes record b3
         iter.idleSave();
         myassert(false, "should have noticed a deleted file");
      } catch (Throwable e) {
         trace("xxxPersistantStack.deletetest caught expected " + e);
         exceptionFlag = true;
         /*
                 iter.rebuildFile();
                 iter.push(b2); // deletes record b3

                 iter.idleSave();

                 ran.terminateWEP();

                 TestPS ran2 = new TestPS();
                 ran2.setFile(hf);
                 PersistantStack.PSIterator iter2= ran2.createIterator();
                 iter2.resetCache();

                 myassert(!ran2.cleanClose(),ran2);
                 testb1((byte [])iter2.next());
                 testb2((byte [])iter2.next());

                 testb2((byte [])iter2.curr());
                 testb1((byte [])iter2.previous());
                 iter2.close();
         */
      }
      if (!exceptionFlag)
         throw new RuntimeException(" didn't get expected exception ");

   }

   void killtest() throws IOException {
      File hf = new File("kill.test");
      TestPS ran = new TestPS();
      ran.newFile(hf);
      myassert(ran.cleanClose(), ran);
      PersistantStack.PSIterator iter = ran.createIterator();
      iter.push(b1);
      iter.push(b3);

      iter.push(b2); // deletes record b3

      iter.idleSave();

      myassert(hf.isFile(), hf);
      ran.kill();
      myassert(!hf.isFile(), hf);

   }
   void quittest() throws IOException {
      File hf = new File("quit.test");
      TestPS ran = new TestPS();
      ran.newFile(hf);
      myassert(ran.cleanClose(), ran);
      PersistantStack.PSIterator iter = ran.createIterator();
      iter.push(b1);

      iter.push(b3);

      iter.decrement();
      iter.push(b2); // deletes record b3

      iter.idleSave();

      ran.terminateWEP();

      TestPS ran2 = new TestPS();
      ran2.setFile(hf);
      PersistantStack.PSIterator iter2 = ran2.createIterator();
      iter2.resetCache();

      myassert(!ran2.cleanClose(), ran2);
      testb1((byte []) iter2.next());
      testb2((byte []) iter2.next());

      testb2((byte []) iter2.curr());
      testb1((byte []) iter2.previous());
      iter2.close();
   }

   void bigtest() throws IOException  {
      File hf = new File("random2.test");
      TestPS ran = new TestPS();
      ran.newFile(hf);
      myassert(ran.cleanClose(), ran);
      PersistantStack.PSIterator iter = ran.createIterator();
      iter.push(b1);

      iter.push(b3);

      iter.decrement();
      iter.push(b2); // deletes record b3

      iter.close();

      TestPS ran2 = new TestPS();
      ran2.setFile(hf);
      myassert(ran2.cleanClose(), ran);
      ran2.checkQuit(1);
      PersistantStack.PSIterator iter2 = ran2.createIterator();
      iter2.resetCache();

      testb1((byte []) iter2.next());
      testb2((byte []) iter2.next());

      testb2((byte []) iter2.curr());
      testb1((byte []) iter2.previous());
      iter2.close();
   }

   void callbacktest(File hf) throws IOException  {
      if (hf != null)
         hf.delete();

      TestPS ran =  new TestPS();
      if (hf != null)
         ran.newFile(hf);
      myassert(ran.cleanClose(), ran);
      PersistantStack.PSIterator iter = ran.createIterator();
      iter.push(b1);
      iter.push(new Oline((byte) 255));
      iter.idleSave();
      iter.decrement();
      iter.push(b1);
      iter.decrement();
      if (hf != null) {
         iter.close();
         ran = new TestPS();
         ran.index = (byte) 255;
         iter = ran.createIterator();
         ran.setFile(hf);
         iter.resetCache();
         myassert(ran.cleanClose(), ran);
         myassert(ran.cbOK, this);
         myassert(ran.dcb, this);
      }
      testb1((byte []) iter.next());
      ran.cbOK = false;
   }
   public static void main(String[] args) throws IOException {
      new PSTest();
   }

   PSTest() throws IOException {
//?? need a test were the length of record saved is >128 <256
      for (int i = 0; i < b3.length; i++)
         b3[i] = (byte) i;
      maintest(new File("random.test"));
      //trace("running maintest with null file");
      maintest(null);
      killtest();
      deletetest();
      quittest();

      callbacktest(new File("callback.test"));
      callbacktest(null);
      bigtest();
      trace("PersistantStack test passed");
   }
   void maintest(File hf) throws IOException {

      TestPS ran = new TestPS();
      if (hf != null)
         ran.newFile(hf);

      myassert(ran.cleanClose(), ran);


      PersistantStack.PSIterator iter = ran.createIterator();
      iter.push(b1);

      iter.push(b2);
      iter.push(r3);

      if (hf != null)
         iter.close();

      TestPS ran2 = (hf == null) ? ran : new TestPS();
      if (hf != null)
         ran2.setFile(hf);
      PersistantStack.PSIterator iter2 = ran2.createIterator();
      iter2.resetCache();
      myassert(ran2.cleanClose(), ran2);
      testb1((byte []) iter2.next());
      testb2((byte []) iter2.next());
      testr3((byte []) iter2.next());

      testr3((byte []) iter2.curr());
      testb2((byte []) iter2.previous());
      testb1((byte []) iter2.previous());
      iter2.decrement();
      myassert(ran2.size() == 3, ran2);

      iter2.push(b2);
      if (hf != null)
         iter2.close();

      TestPS ran3 = (hf == null) ? ran2 : new TestPS();
      if (hf != null)
         ran3.setFile(hf);
      myassert(ran3.cleanClose(), ran3);
      PersistantStack.PSIterator iter3 = ran3.createIterator();
      iter3.resetCache();
      testb2((byte []) iter3.next());
      try {
         iter3.next();
         myassert(false, ran3);
      } catch (NoSuchElementException e) {
         // we expect an exception from iter3.next
      }

   }
   /*
   Test cases to write:
      force crash situation no quit at end
   */
}

final class TestPS extends PersistantStack {

   byte index;
//   byte cbdata =0;
   boolean cbOK = false;
   boolean dcb = false;
   TestPS() { /* doesn't need to do anything*/ }
   TestPS(byte in) {
      index = in;
   }

   public TestIterator createIterator() {
      return new TestIterator();
   }

   public void usercb(ByteInput dis) throws EOFException {
      byte bi = dis.readByte();
      Testutil.myassert(!cbOK, this);
      Testutil.myassert(bi == -1, Byte.valueOf(bi));
//      cbdata=0;
      cbOK = true;
   }

//   public void owriteExternal(DataOutputStream dos,Object obj) throws IOException {
//      dos.writeByte(index);
//   }
//   public void oreadExternal(byteInput dis,Object obj)throws IOException  {
//      (index= dis.readByte();
//   }
//   public Object oreadExternal(byteInput dis) throws IOException {
//      return new Byte(dis.readByte());
//   }
   public String toString() {
      return "tcb " + index;
   }

   public void deletecb(int indexi) { //??? do we really need the index?
      dcb = true;
   }


   final class TestIterator extends PersistantStack.PSIterator {

      protected boolean isOutLine(Object ob) {
         return ob instanceof Oline;
      }

      public void writeExternal(DataOutputStream dos, Object obj) throws
            IOException {
         if (obj instanceof Oline) {
            Oline oo = (Oline) obj;
            dos.write(oo.index);
         } else {
            byte [] b = (byte []) obj;
            dos.writeInt(b.length);
            dos.write(b);
         }
      }

      public boolean matches(Object ob, Object ob2) {
         return false;
      }

      public Object readExternal(ByteInput dis) throws IOException {
         byte[] b = new byte[dis.readInt()];
         dis.readFully(b);
         return b;
      }
      public Object newExternal(ByteInput dis) throws IOException {
         return readExternal(dis);
      }

   }
}
class Oline {
   int index;
   Oline(byte indexi) {
      index = indexi;
   }

}

