package javi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static history.Tools.trace;

public final class PosListList extends TextList<Position> {

   private static final long serialVersionUID = 1;
   private transient TextEdit lastlist = null;
   private transient TextEdit lastlist2 = null;
   private static final PllConverter converter = new PllConverter();

   PosListList(IoConverter ioc) {

      super(ioc, ioc.prop);
      //plist=this;
      finish();
      EditContainer.registerChangeListen(new FCH());
   }

   private final class FCH extends EditContainer.FileChangeListener  {

      void addedLines(FileDescriptor fd, int count, int index) {
         //trace("PLL got addedLines fd " + fd + " count " + count + " index " + index );
         // fix up the line numbers
         EditContainer<Position> errlist = at(1);
         for (int ii = 1; ii < errlist.readIn(); ii++) {
            Position pos = errlist.at(ii);
            if (pos.filename.equals(fd) && pos.y > index) {
               Position npos = new Position(pos.x,
                   (index > 0 || pos.y > index + count
                     ? pos.y + count
                     : index
                   ),
                   pos.filename, pos.comment);
               errlist.changeElementAt(npos, ii);
            }
         }
      }
   }

   private void setLastList(TextEdit list) {
      //trace(((list==null) ? "null list " : list + " " + !list.contains(1)));
      if (null == list)
         return;
      lastlist2 = lastlist;
      lastlist = list;
      //trace("lastlist " + lastlist + " lastlist2 " + lastlist2);
   }

   private void gotoList(FvContext fvc, TextEdit list)throws InputException  {
      //trace(((list==null) ? "null list " : list + " " + !list.contains(1)) + ui.isGotoOk(fvc));
      if (null == list)
         list = lastlist;
      if ((null == list) || (!list.containsNow(1)) || !fvc.isGotoOk())
         return;
      lastlist2 = null;
      lastlist = list;
      //trace("lastlist " + lastlist + " lastlist2 " + lastlist2);
      FvContext.connectFv(list, fvc.vi); //??? exception safety
   }

   private void addList(TextEdit<Position> poslist) {
      insertOne(poslist, finish());
      poslist.readIn(); // force it to start reading
      setLastList(poslist);
   }

   private void setFirst(IoConverter<Position> newIo) {
      //trace("newIo " + newIo);
      TextEdit<Position> oldList = at(1);
      //trace("olderrlist " + oldList);
      //trace("oldList.readIn() = " + oldList.readIn());

      if (null != newIo) {
         //trace("add new ioc " + newIo);
         TextEdit<Position> newList =
            new TextEdit<Position>(newIo, this, newIo.prop);
         newList.readIn();  // make sure that process is started
         changeElementAt(newList, 1);
         setLastList(newList);
         if (oldList == lastlist2)
            lastlist2 = null;
         if (oldList == lastlist)
            lastlist = null;
         //trace("lastlist " + lastlist + " lastlist2 " + lastlist2);
         try {
            FvContext.dispose(oldList, newList);
         } catch (Exception e) {
            UI.popError("attempting to dispose of list", e);
         }
      } else {
         oldList.remove(1, oldList.readIn());
      }
   }

   void flush() throws IOException {
      //trace("reached flushI");
      setFirst(null);
      lastlist = null;
      lastlist2 = null;
      //trace("lastlist " + lastlist + " lastlist2 " + lastlist2);

      TextEdit base = at(1);
      base.reload();
      //trace("finish = " + plist.finish());
      try {
         for (int ii = 1; ii < finish(); ii++)
            FvContext.dispose(at(ii), this);
      } catch (Exception e) {
         UI.popError("attempting to dispose of list", e);
      }
      //trace("finish = " + plist.finish());
      //reload(true);
      //trace("finish = " + plist.finish());
      reload();
      finish();
   }

   private void gotoNextPos(FvContext fvc, boolean[] reverse,
         boolean wait) throws InputException {
      //trace(" goto nextpos lastlist = " + lastlist);
      if (fvc.edvec instanceof FileList)
         FvContext.connectFv((TextEdit) fvc.at(), fvc.vi);
      else  {
         if ((null == lastlist)) {
            lastlist = lastlist2;
            lastlist2 = null;
            //trace("lastlist " + lastlist + " lastlist2 " + lastlist2);
         }
         if (null == lastlist)
            return;

         if ((wait && !lastlist.contains(1)) || (!lastlist.containsNow(1)))
            return;

         FvContext listfvc = fvc.switchContext(lastlist, reverse[0] ? -1 : 1);
         Object obj = listfvc.at();
         //trace("obj class = " + obj.getClass());
         if (obj instanceof Position) {
            Position pos = (Position) obj;
            FileList.gotoposition(pos, true, fvc.vi);
         } else if (obj instanceof TextEdit) {
            TextEdit ex = (TextEdit) obj;
            if (ex.at(0) instanceof Position)
               gotoList(fvc, ex);
            else
               FvContext.connectFv(ex, fvc.vi);
         } else if (obj instanceof FvExecute) {
            FvExecute fe = (FvExecute) obj;
            fe.execute(fvc);
         } else
            throw new RuntimeException("vic.gotonextpos unexpected object");
      }
   }

   public static final class Cmd extends Rgroup {

      private static ArrayList<Position> tagstack = new ArrayList<Position>();
      private static PosListList inst;

      static {
         IoConverter io = new IoConverter(new FileProperties(
            FileDescriptor.InternalFd.make("position list list"), converter),
            true);
         inst = new PosListList(io);
      }

      private static final Matcher filereg = Pattern.compile(
            "(([a-zA-Z]:)?([^:\\s\\(\\)\"\']+)):([0-9]+)").matcher("");
      private static Ctag ctags;
      private static HashMap<String, TextEdit> tahash =
         new HashMap<String, TextEdit>(100);

      public static void gotoList(FvContext fvc, TextEdit list) throws
            InputException  {
         inst.gotoList(fvc, list);
      }

      Cmd() throws IOException {
         final String[] rnames = {
            "",
            "ta",
            "gototag",
            "poptag",
            "fl",
            "rep",               //  5
            "te",
            "gotopllist",
            "dummy1",
            "nextpos",
            "gotopositionlist", //10
            "dummypll",
            "gotodirlist",
            "gotoroot",
            "nextposwait",
         };
         register(rnames);
         flush();
      }

      public Object doroutine(int rnum, Object arg, int count, int rcount,
            FvContext fvc, boolean dotmode) throws
            IOException, InputException {
         //trace("rnum = " + rnum );
         switch (rnum) {
            case 1:
               if (null != arg)
                  gototag(arg.toString().trim(), fvc);
               return null;
            case 2:
               gototag(null, fvc);
               return null;
            case 3:
               poptag(fvc.vi);
               return null;
            case 4:
               flush();
               return null;
            case 5:
               if (null != arg)
                  inst.addList(DirList.getDefault().globalgrep(
                     arg.toString()));
               return null;
            case 6:
               return null; //markex(new extext(new testr()),fvc); return null;
            case 7:
               inst.gotoList(fvc, inst);
               return null; // fallthrough
            case 8:
               return null; //jlintcommand();return null;
            case 9:
               inst.gotoNextPos(fvc, (boolean[]) arg, false);
               return null;
            case 10:
               inst.gotoList(fvc, null);
               return null;
            case 11:
               throw new InputException("bad command");

            case 12:
               FvContext.connectFv(DirList.getDefault(), fvc.vi);
               return null;
            case 13:
               FvContext.connectFv(TextEdit.getRoot(), fvc.vi);
               return null;
            case 14:
               inst.gotoNextPos(fvc, (boolean[]) arg, true);
               return null;
            default:
               throw new RuntimeException("vigroup:default");
         }
      }

      public static void setErrors(IoConverter<Position> newerrs) {
         //trace("setErrors" + newerrs);
         //trace("inst" + inst);
         inst.setFirst(newerrs);
      }

      static void flush() throws IOException {
         tagstack.clear();
         tahash.clear();
         inst.flush();
         try {
            ctags = new Ctag("tags");
         } catch (IOException e) {
            ctags = null;
         }
      }

      private static void poptag(View vi) throws InputException {
         //trace("entered popstack size = " +tagstack.size() );
         int size = tagstack.size();
         if (0 != size) {
            //trace("pop to " + tagstack.get(size-1));
            FileList.gotoposition(tagstack.get(size - 1), false, vi);
            tagstack.remove(size - 1);
         }
      }

      private static String getLastSym(String str, int startid) {
         //trace(":" + str + " startid = " + startid);
         int endid = startid;
         for (; endid < str.length(); endid++) {
            char ch =  str.charAt(endid);
            if (!Character.isJavaIdentifierPart(ch) && '.' != ch)
               break;
         }
         //trace("endid" + endid + " startid = " + startid);
         return str.substring(startid, endid);
      }

      private void gototag(String str, FvContext fvc) throws
            InputException, IOException {
         Position porig = fvc.getPosition(null);

         String fstr = null == str
                       ? fvc.at().toString()
                       : str;

         //trace("gototag " + fstr);

         if (filereg.reset(fstr).find()) {
            //trace("filreg matched " + filereg.group(0) + "filename = " + filereg.group(1));
            //Position pos = new Position(0,Integer.parseInt(filereg.group(4)),filereg.group(1),"taglookup");
            //if (FileList.gotoposition(pos,false,fvc.vi))
            FvContext nfvc = FileList.openFileName(filereg.group(1), fvc.vi);
            if (null != nfvc) {
               int ypos = Integer.parseInt(filereg.group(4));
               nfvc.edvec.contains(ypos);
               nfvc.cursoryabs(ypos);
               //trace("add stack porig = "  + porig);
               tagstack.add(porig);
               return;
            }
         }

         if (null == str) {
            str = fvc.at().toString();
            str = getLastSym(str, fvc.insertx());
         }
         TextEdit templist = taglookup(str, fvc.vi);
         if (null != templist) {
            inst.setLastList(templist);
            //trace("add stack porig = "  + porig);
            tagstack.add(porig);
         }
      }

      private TextEdit<Position> createtags(String sym) throws IOException {
         Position[] parray = null;

         PositionIoc xf;
         if (true) {
            trace("create tags ctags" + ctags);
            if (null != ctags) {
               //trace("do lookup");
               parray = ctags.taglookup(sym);
               if ((parray != null))
                  if (parray.length == 0)
                     parray = null;
            }
            xf = new XrefReader(sym);
         } else {
            xf = new GlobalReader(sym);
         }

         TextEdit<Position> taglist = new TextEdit<Position>(
            xf, parray, inst, xf.prop);
         taglist.contains(2);
         tahash.put(sym, taglist);
         inst.insertOne(taglist, inst.finish());
         return taglist;
      }
      private static final Pattern spl = Pattern.compile("\\.");
      private static final Matcher classmatcher = Pattern.compile(
         "\\bclass:(\\S*)\\b").matcher("");
      private static final Matcher filematcher = Pattern.compile(
         "\\bfile:(\\S*)\\b").matcher("");

      private static final class FDL
            implements EditContainer.FileDisposeListener {
         public void fileDisposed(EditContainer ev) {
            trace("file disposed " + ev);
            tahash.remove(ev.getName());
         }
      }
      private FDL fdl = new FDL();

      private TextEdit taglookup(String str, View vi) throws InputException {
         //trace("taglookup " + str);
         String[]symlist = spl.split(str);

         if (0 == symlist.length)
            return null;

         String sym = symlist[symlist.length - 1];
         TextEdit<Position> taglist = tahash.get(sym);
         try {
            if (null == taglist) {
               taglist = createtags(sym);
               taglist.addDisposeNotify(fdl);
            }

            int tagcount = 1;
            for (; taglist.readIn() > tagcount; tagcount++) {
               Position  ctagpos = taglist.at(tagcount);
               if (!Ctag.getTagName(ctagpos).equals(sym))
                  break;
            }

            if (tagcount > 1) {
               int[] scores = new int[tagcount];
               for (int ii = 1; ii < tagcount; ii++)
                  scores[ii] = 0;
               int maxscore = 0;

               for (int symIndex = symlist.length - 2;
                     symIndex >= 0;
                     symIndex--) {
                  String currsym = symlist[symIndex];

                  for (int ii = 1; ii < tagcount; ii++) {
                     Position pos = taglist.at(ii);

                     if (classmatcher.reset(pos.comment).find()) {
                        String clss =  classmatcher.group(1);
                        //trace("classmatcher class = " + clss + " currsym = " + currsym);
                        if (str.indexOf(clss) != -1) {
                           scores[ii] += clss.length();
                        }

                        if (clss.equals(currsym)) {
                           scores[ii] += 2;
                        }
                     }

                     if (filematcher.reset(pos.filename.shortName).find()
                           && filematcher.group(1).equals(currsym)) {
                        scores[ii] += 1;
                     }
                     if (scores[ii] > maxscore)
                        maxscore = scores[ii];
                     else if (scores[ii] < maxscore)
                        scores[ii] = Integer.MIN_VALUE;
                  }
               }
               //for (int i=1;i<tagcount;i++)
               //   trace("symlist[i] " + symlist[i] + " tagscore[" + i + "] = " + scores[i]);
               for (int ii = 1; ii < tagcount; ii++) {

                  if (scores[ii] == maxscore) {
                     if (FileList.gotoposition(taglist.at(ii), false, vi)) {
                        FvContext tagfvc =  FvContext.getcontext(vi, taglist);
                        if (tagfvc.edvec.contains(ii))
                           tagfvc.cursoryabs(ii);
                        break;
                     }
                  }
               }
            } else {
               if (FileList.gotoposition(taglist.at(1), false, vi)) {
                  FvContext tagfvc =  FvContext.getcontext(vi, taglist);
                  if (tagfvc.edvec.contains(1))
                     tagfvc.cursoryabs(1);
               }
            }
         } catch (IOException e) {
            trace("PosListList taglookup caught " + e);
         }

         return taglist;
      }

      static void myassert(boolean flag, Object dump) {
         if (!flag)
            throw new RuntimeException("ASSERTION FAILURE " + dump.toString());
      }

      public static void main(String[] args) {

         try {
            //final Matcher filereg = Pattern.compile(
            //"(\b([a-zA-Z]:)?([^:\\s\\(\\)\"\']+)):([0-9]+)").matcher("");
            // "(([a-zA-Z]:)?([^:\\s\\(\\)\"\']+)):([0-9]+)").matcher("");
            filereg.reset("UI.java:1118 java.xxx.event.");
            myassert(filereg.find(), "UI");

            myassert(filereg.reset("smtp_hfilter.c:254 ").find(), "");
            myassert(filereg.reset("smtp_hfilter.c:254").find(), "");

            filereg.reset("smtp_hfilter.c:254 hfilter_find SUBJECT"
               + "smtp_hfilter.c:266 hfilter_find SUBJECTsmtp_hfilter.c:"
               + "131 normalize_name_stbuf_ind 0 ,buffer[buf_ind]13");
            myassert(filereg.find(), "");
            myassert(filereg.group(4).equals("254"), filereg.group(4));
            myassert(filereg.group(1).equals("smtp_hfilter.c"),
               filereg.group(1));
            trace("test executed successfully");
         } catch (Throwable e) {
            trace("main caught exception " + e);
            e.printStackTrace();
         }
      }


   }

   private static final class PllConverter extends
         ClassConverter<TextEdit<Position>> {

      private static final long serialVersionUID = 1;
      public TextEdit<Position> fromString(String str) {
         PositionIoc ioc = new PositionIoc(
            str, null, PositionIoc.pconverter); // an unusable editvec
         return new TextEdit<Position>(ioc, ioc.prop); // an unusable editvec
      }
   }
}

class TextList<TOType> extends TextEdit<TextEdit<TOType>> {

   private static final long serialVersionUID = 1;
   TextList(IoConverter<TextEdit<TOType>> e,
         FileProperties<TextEdit<TOType>> fp) {
      super(e, fp);
   }
}
