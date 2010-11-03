package javi;

import history.PersistantStack;
import history.ByteInput;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

public final class UndoHistory<OType> extends PersistantStack {
   static final String copyright = "Copyright 1996 James Jensen";
   private EhMark currmark; // index of current position in undo
   private ChangeRecord current = null;
   private int savewrite = -1;
   final ClassConverter<OType> conv;
   final FileProperties<OType> prop;
   private final ChangeRecord[] prototypes;

   public String toString() {
      return currmark.toString();
   }

   UndoHistory(FileProperties propi, ChangeRecord [] prototypesi) {
      conv = propi.conv;
      prop = propi;
      prototypes = new ChangeRecord[4 + prototypesi.length];
      prototypes[0] = null;
      prototypes[1] = new ChangeRecord.BaseRecord();
      prototypes[2] = new ChangeRecord.CheckRecord();
      prototypes[3] = new WriteRecord();
      for (int i = 4; i < prototypesi.length + 4; i++)
         prototypes[i] = prototypesi[i - 4].newOne();
      currmark = new UndoHistory.EhMark();
   }

   public void deletecb(int index) {
      //trace("index);
      if (index <= savewrite)
         savewrite = -1;
   }

   public void usercb(ByteInput inp) {
      inp.readByte();
      savewrite = inp.readInt();
      inp.readUTF();
      //trace("savewrite = " + Integer.toHexString(savewrite));
   }

   void close() throws IOException {
      //trace("currmark = " + currmark);
      currmark.close();
   }

   void ereset() {
      //trace("should clear cache of " + prop);
      reset();
      currmark = new UndoHistory.EhMark();
      FvContext.invalidateBack(currmark); //circular reference ???
      current = null;
   }

   void idleSave() throws IOException {
      currmark.idleSave();
   }

   void addState(StringBuilder sb) {
      //trace("current " + current +  " + currmark " + currmark);
      //trace("current " + current +  " + curr = " + currmark.curr());
      //trace("savewrite " + savewrite);
      //trace("sb " + sb);
      if (currmark.isWritten())
         sb.append(" unchanged");
      else {
         String st = current.getDate();
         sb.append(st != null
              ?  " " + st
              : " modified");
      }
   }

   boolean isWritten() {
      //trace("isWritten " + currmark.isWritten() + " index " + currmark.getIndex());
      return currmark.isWritten();
   }
   void forceWritten() {
      savewrite = currmark.getIndex();
   }

   void push(ChangeRecord cr) {
      //trace("push record  type " + cr.getType()   +" :"+ cr);
      //Thread.dumpStack();
      currmark.push(current = cr);
   }

   void checkRecord() {

      //trace("current =  " + current);
      if (current instanceof ChangeRecord.CheckRecord)
         return;
      if (savewrite >= currmark.getIndex()) {
         savewrite = -1;
         currmark.push(current = new WriteRecord(-1));
      } else
         currmark.push(current = new ChangeRecord.CheckRecord());
   }

   void baseRecord() {
      //current = new ChangeRecord.BaseRecord(true);
      currmark.remove(current);
      //currmark.push(current) ;
      //trace("remove returned " +

      if (currmark.hasNext())
         pushend(current = new ChangeRecord.BaseRecord(currmark.getIndex()));
      else
         currmark.push(current =
                          new ChangeRecord.BaseRecord(currmark.getIndex() + 1));

      savewrite = currmark.getIndex();
      //trace("savewrite = " + savewrite + " + hasFile = " + hasFile());
   }

   void writeRecord() {

      //trace("currmark.index = " + currmark.getIndex()  );
      //trace("currmark.hasnext = " + currmark.hasNext());
      //trace("current = " + current );
      //trace("dump = " + dump() );

      if (currmark.hasNext())
         pushend(new WriteRecord(currmark.getIndex()));
      else
         currmark.push(new WriteRecord(currmark.getIndex() + 1));

      savewrite = currmark.getIndex();
      //trace("currmark.index = " + currmark.getIndex()  );
      //trace("currmark.hasnext = " + currmark.hasNext());
      //trace("current = " + current );
      //trace("dump = " + dump() );

      //trace("savewrite = " + savewrite + " + hasFile = " + hasFile());
   }

   EhMark copyCurr() {
      return (EhMark) currmark.clone();
   }

   // undo changes until a checked undo is found.  If the undo is to be used
   //    uncheckpoint() must be called
   int undo() throws IOException {
      //trace("undo current=" + current);
      int chindex = -1;

      do {
         if (current instanceof ChangeRecord.BaseRecord)
            return chindex;
         current = (ChangeRecord) currmark.previous();
         //trace(" " +currmark.getIndex() + " " + current );
      } while (current  instanceof ChangeRecord.CheckRecord);

      do {
         chindex = current.undocr();
         //trace(" " +currmark.getIndex() + " " + current);
      } while (!((current = (ChangeRecord) currmark.previous())
                 instanceof ChangeRecord.CheckRecord));
      //trace(" " +currmark.getIndex() + " " + current);
      return chindex;
   }

   // redoes undone changes up to the next undo check
   int redo() throws IOException {
      //trace(currmark.getIndex() + " " + current + dump());
      int chindex = -1;
      while (currmark.hasNext()) {
         //trace(currmark.getIndex() +" " + current );
         current = (ChangeRecord) currmark.next();
         //trace(currmark.getIndex() +" " +  current);
         if (current instanceof ChangeRecord.CheckRecord)
            break;
         chindex = current.redocr();
      }
      while (currmark.hasNext()) {
         current = (ChangeRecord) currmark.next();
         //trace(currmark.getIndex() +" "  + current);
         if (!(current instanceof ChangeRecord.CheckRecord)) {
            current = (ChangeRecord) currmark.previous();
            break;
         }
      }
      //trace( currmark.getIndex()  + " " + current);
      return chindex;
   }

   public static final class BackupStatus {
      public final boolean cleanQuit;
      public final boolean isQuitAtEnd;
      public final Throwable error;
      BackupStatus(boolean c, boolean i, Throwable e) {
         cleanQuit = c;
         isQuitAtEnd = i;
         error = e;
      }
      public String toString() {
         return "clean quit  = " + cleanQuit + "  isQuitAtEnd = "
                + isQuitAtEnd + " + error = " + error;
      }

      boolean clean() {
         return cleanQuit && isQuitAtEnd && error == null;
      }

   }

   BackupStatus readToQuit() throws IOException {
      //trace("currmark = " + currmark + " savewrite = " + savewrite);
      //trace("cleanQuit = " + cleanClose() + " isQuitAtEnd = " + isQuitAtEnd());

      if (cleanClose() && currmark.beforeQuit())  // if exited cleanly
         while (currmark.beforeQuit() && currmark.hasNext()) {
            current = (ChangeRecord) currmark.next();
            //trace("2 " +currmark.getIndex() + current);
            current.redocr();
         }
      else if (savewrite != -1)  // try for last saved version
         while (currmark.getIndex() < savewrite && currmark.hasNext()) {
            current = (ChangeRecord) currmark.next();
            //trace("1 " +currmark.getIndex() + current);
            current.redocr();
         }

      else
         while (currmark.hasNext()) {  //try for base version
            current = (ChangeRecord) currmark.next();
            //trace("3 " +currmark.getIndex() + current);
            current.redocr();
            if (current instanceof ChangeRecord.BaseRecord)
               break;
         }

      currmark.resetCache();

      //trace("currmark after process = " + currmark);

      //trace("dump= " + dump());
      BackupStatus retval = new BackupStatus(
            cleanClose(), isQuitAtEnd(), null);
      //trace("status = " + retval);
      return retval;
   }

   void terminate() {
      terminateWEP();
   }
   // undo changes made to a particular element */
   /*
   void undoElement(editvec ev,int index) throws readOnlyException {
      if (ev.readonly)
         throw new readOnlyException(ev,ev.prop.getName());
      ChangeRecord cr;
      ChangeRecord crsave = null;
      for (int chind = ev.curr;;chind--) {
         cr = (ChangeRecord)ev.changes.elementAt(chind);
         if (cr instanceof BaseRecord)
                  break;
         else if (cr.index <= index)

            if (cr instanceof DeleteRecord)
               index++;

            else if (cr instanceof InsertRecord)
               if (cr.index==index)
                  break;
               else
                  index--;

            else if (cr instanceof ModRecord)
               if (cr.index==index) {
   //trace("undoElement " + cr);
                  ev.changes.insertElementAt(cr,ev.curr+1);
                  ev.changes.removeElementAt(chind);
                  cr.undocr(ev);
                  if (ev.curr > ev.writtenindex && chind < ev.writtenindex)
                    ev.writtenindex=-1; // force it to be modified
                  ev.curr--;
                  crsave = cr;
               }
               else;
            else;
         else;
      }
      if (crsave!=null) {
        crsave.checked = true;
        fvcontext.fixcursor(ev);
      }

   }

   */

   public final class EhMark extends
         PersistantStack.PSIterator implements Cloneable {

      EhMark() {
         super();
      }

      public Object clone() {
         try {
            return super.clone();
         } catch (CloneNotSupportedException e) {
            throw new RuntimeException("UndoHistory failed", e);
         }
      }

      public boolean sameBack(EhMark ehm) {
         //throw new RuntimeException ("fix this");
         return UndoHistory.this.prop.fdes == prop.fdes;
      }

      public boolean matches(Object ob, Object ob2) {
         ChangeRecord ch = (ChangeRecord) ob;
         ChangeRecord ch2 = (ChangeRecord) ob2;
         return (ch.getType() == ch2.getType())
                && ch.getType() == ChangeRecord.BASE;
      }


      public Object readExternal(ByteInput dis) {
         //      trace("type = " + breader.type);
         ChangeRecord rec = prototypes[dis.readByte()];
         rec.readExternal(dis, conv);
         return rec;
      }

      public ChangeRecord newExternal(ByteInput dis) {
         ChangeRecord rec = prototypes[dis.readByte()].newOne();
         rec.readExternal(dis, conv);
         return rec;
      }

      public void writeExternal(DataOutputStream dos, Object ext) {
         //trace("recordIndex = " + getIndex());
         ((ChangeRecord) ext).writeExternal(dos, conv);
         //trace("done recordIndex = " + getIndex() +  " " + this );
      }

      protected boolean isOutLine(Object obj) {
         //trace("isOutline " +obj + " retval =  " + (obj instanceof WriteRecord)) ;
         return (obj instanceof WriteRecord);
      }

      void terminate()  {
         //terminateWEP();
      }
      boolean isWritten() {
         //trace("index = " + getIndex() + " savewrite = " + savewrite);
         return getIndex() == savewrite;
      }

      public void getChanges(View.ChangeOpt vi) {
         //trace("changemark " + this + " currmark = " + currmark);
         //trace("current " + current );
         if (!isValid()) {
            setEqual(currmark);
            vi.redraw();
         }
         if (getIndex() < currmark.getIndex())
            while (getIndex() < currmark.getIndex()) {
               //trace("1 " + curr());
               if (((ChangeRecord) next()).redocr(vi)) {
                  setEqual(currmark);
                  break;
               }
            }
         else
            while (getIndex() > currmark.getIndex())
               if (((ChangeRecord) previous()).undocr(vi)) {
                  setEqual(currmark);
                  break;
               }
         //assert(getIndex() ==  currmark.getIndex());
      }

   }

   abstract static class ChangeRecord<OType> {

      private static final int BASE = 1;
      private static final int CHECK = 2;
      static final int WRITE = 3;
      static final int DELETE = 4;
      static final int INSERT = 5;
      static final int MOD = 6;
      static final int INSERTSTRING = 7;
      protected int cindex;

      String getDate() {
         return null;
      }

      ChangeRecord() {
      }

      ChangeRecord(int indexi) {
         cindex = indexi;
      }

      abstract byte getType();
      abstract ChangeRecord newOne();
      abstract int redocr();
      abstract int undocr();
      abstract boolean redocr(View.ChangeOpt vi);
      abstract boolean undocr(View.ChangeOpt vi);


      void readExternal(ByteInput dis, ClassConverter<OType> conv) {
         cindex = dis.readInt();
         //trace("ChangeRecord.readExternal type = " + getType() + " cindex = " + cindex);
      }
      void writeExternal(DataOutputStream dos, ClassConverter conv) {
         //trace("ChangeRecord.writeExternal type = " + getType() + " cindex = " + cindex);
         try {
            dos.write(getType());
            dos.writeInt(cindex);
         } catch (IOException e) {
            throw new RuntimeException(
               "ChangeRecord.writeExternal unexpected " , e);
         }
      }

      OType[] readObjs(ByteInput dis, ClassConverter<OType> conv) {
         int ocount = dis.readInt();
         OType []obj = (OType []) new Object[ocount];
         while (--ocount >= 0)
            obj[ocount] = conv.newExternal(dis);
         return obj;
      }
      void writeObjs(DataOutputStream dos, ClassConverter conv, OType [] obj) {
         try {
            int ocount = obj.length;
            dos.writeInt(ocount);
            while (--ocount >= 0)
               conv.saveExternal(obj[ocount], dos);
         } catch (IOException e) {
            throw new RuntimeException(
               "InsertRecord.writeExternal unexpected " , e);
         }
      }

      private static class BaseRecord extends WriteRecord {

         public String toString() {
            return  "base "  + super.toString();
         }
         byte getType() {
            return BASE;
         }

         BaseRecord newOne() {
            return new BaseRecord();
         }

         private BaseRecord() { }
         //private BaseRecord(boolean unused) {
         //    super( 0);
         //}
         BaseRecord(int index) {
            super(index);
         }
      }

      static class CheckRecord extends ChangeRecord {

         public String toString() {
            return "c" + cindex  + ":";
         }

         byte getType() {
            return CHECK;
         }

         CheckRecord newOne() {
            return new CheckRecord();
         }

         int redocr() {
            return cindex;
         }

         int undocr() {
            return cindex;
         }

         boolean redocr(View.ChangeOpt vi) {
            return false;
         }

         boolean undocr(View.ChangeOpt vi) {
            return false;
         }

         CheckRecord() {
         }
         CheckRecord(boolean unused) {
            super(0);
         }
      }

   }
   private static class WriteRecord extends
      UndoHistory.ChangeRecord.CheckRecord {
      private String writeDate;
      public String toString() {
         return "w" + cindex  + ":" + writeDate;
      }
      byte getType() {
         return WRITE;
      }

      WriteRecord newOne() {
         return new WriteRecord();
      }

      void readExternal(ByteInput dis, ClassConverter conv) {
         //trace("WriteRecord.readExternal");
         super.readExternal(dis, conv);
         writeDate = dis.readUTF();
      }

      void writeExternal(DataOutputStream dos, ClassConverter conv) {
         super.writeExternal(dos, conv);
         try {
            dos.writeUTF(writeDate);
         } catch (IOException e) {
            throw new RuntimeException(
               "ehsistory.WriteRecord.writeExternal failed", e);
         }
      }
      WriteRecord() { }

      WriteRecord(int index) {
         super(true);
         //trace("WriteRecord index = " + index);
         writeDate = new Date().toString();
         cindex = index;
      }
      String getDate() {
         return writeDate;
      }
   }
   static void trace(String str) {
      Tools.trace(str, 1);
   }
}
