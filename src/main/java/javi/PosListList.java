package javi;

import java.io.File;
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

   @SuppressWarnings({"unchecked", "rawtypes"})
   PosListList(IoConverter ioc) {

      super(ioc, ioc.prop);
      //plist=this;
      finish();
      EditContainer.registerChangeListen(new FileChangeHandler());
   }

   private final class FileChangeHandler
         extends EditContainer.FileChangeListener  {

      void addedLines(FileDescriptor fd, int count, int index) {
         //trace("PLL got addedLines fd " + fd + " count " + count + " index " + index );
         // fix up the line numbers
         EditContainer<Position> errlist = at(1);
         for (int i = 1; i < errlist.readIn(); i++) {
            Position pos = errlist.at(i);
            if (pos.filename.equals(fd) && pos.y > index) {
               Position npos = new Position(pos.x,
                   (index > 0 || pos.y > index + count
                     ? pos.y + count
                     : index
                   ),
                   pos.filename, pos.comment);
               errlist.changeElementAt(npos, i);
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
      trace("lastlist " + lastlist + " lastlist2 " + lastlist2);
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
         for (int i = 1; i < finish(); i++)
            FvContext.dispose(at(i), this);
      } catch (Exception e) {
         UI.popError("attempting to dispose of list", e);
      }
      //trace("finish = " + plist.finish());
      //reload(true);
      //trace("finish = " + plist.finish());
      reload();
      finish();
   }

   private void gotoNextPos(FvContext<?> fvc, boolean[] reverse,
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

         FvContext listContext = fvc.switchContext(
            lastlist, reverse[0] ? -1 : 1);
         Object item = listContext.at();
         if (item instanceof Position) {
            Position pos = (Position) item;
            FileList.gotoposition(pos, true, fvc.vi);
         } else if (item instanceof TextEdit) {
            TextEdit ex = (TextEdit) item;
            if (ex.at(0) instanceof Position)
               gotoList(fvc, ex);
            else
               FvContext.connectFv(ex, fvc.vi);
         } else if (item instanceof FvExecute) {
            FvExecute fe = (FvExecute) item;
            fe.execute(fvc);
         } else
            throw new RuntimeException(
               "gotoNextPos unexpected object");
      }
   }

   public static final class Cmd extends Rgroup {

      private static ArrayList<Position> tagstack = new ArrayList<>();
      private static PosListList instance;

      @SuppressWarnings({"unchecked", "rawtypes"})
      private static PosListList createInstance() {
         IoConverter io = new IoConverter(new FileProperties(
            FileDescriptor.InternalFd.make("position list list"), converter),
            true);
         return new PosListList(io);
      }

      static {
         instance = createInstance();
      }

      private static final Matcher filePositionPattern = Pattern.compile(
            "(([a-zA-Z]:)?([^:\\s\\(\\)\"\']+)):([0-9]+)").matcher("");
      private static Ctag ctagFinder;
      private static HashMap<String, TextEdit> tagCache =
         new HashMap<>(100);

      public static void gotoList(FvContext fvc, TextEdit list) throws
            InputException  {
         instance.gotoList(fvc, list);
      }

      enum PCmd {
         UNUSED,              // 0
         TA,                  // 1: tag lookup (ta)
         GOTO_TAG,            // 2: go to tag
         POP_TAG,             // 3: pop tag stack
         FL,                  // 4: flush tag cache
         REP,                 // 5: global grep
         TE,                  // 6: (unused)
         GOTO_PL_LIST,        // 7: go to position list
         DUMMY1,              // 8: (unused)
         NEXT_POS,            // 9: next position
         GOTO_POSITION_LIST,  // 10: go to position list
         DUMMY_PLL,           // 11: (unused/error)
         GOTO_DIR_LIST,       // 12: go to dir list
         GOTO_ROOT,           // 13: go to root
         NEXT_POS_WAIT,       // 14: next position (wait)
         GOTO_DIR_LIST_DEFAULT, // 15: go to default dir list
         CN,                  // 16: :cn (cnext)
         CP,                  // 17: :cp (cprev)
      }

      private static final PCmd[] CMDS = PCmd.values();

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
            "gotosearchpath",
            "cn",
            "cp",
         };
         register(rnames);
         flush();
      }

      public Object doroutine(int rnum, Object arg, int count, int rcount,
            FvContext fvc, boolean dotmode) throws
            IOException, InputException {
         //trace("rnum = " + rnum );
         switch (CMDS[rnum]) {
            case TA:
               trace("ta command arg="
                  + (arg == null ? "null" : "'" + arg + "'"));
               if (null != arg)
                  gototag(arg.toString().trim(), fvc);
               return null;
            case GOTO_TAG:
               trace("gototag command (^])");
               gototag(null, fvc);
               return null;
            case POP_TAG:
               trace("poptag command (^T)");
               poptag(fvc.vi);
               return null;
            case FL:
               flush();
               return null;
            case REP:
               if (null != arg)
                  instance.addList(DirManager.getInstance().globalgrep(
                     arg.toString()));
               return null;
            case TE:
               return null; //markex(new extext(new testr()),fvc); return null;
            case GOTO_PL_LIST:
               instance.gotoList(fvc, instance);
               return null; // fallthrough
            case DUMMY1:
               return null; //jlintcommand();return null;
            case NEXT_POS:
               instance.gotoNextPos(fvc, (boolean[]) arg, false);
               return null;
            case GOTO_POSITION_LIST:
               instance.gotoList(fvc, null);
               return null;
            case DUMMY_PLL:
               throw new InputException("bad command");

            case GOTO_DIR_LIST:
               return openDirectoryForCurrentFile(fvc);
            case GOTO_ROOT:
               FvContext.connectFv(TextEdit.getRoot(), fvc.vi);
               return null;
            case NEXT_POS_WAIT:
               instance.gotoNextPos(fvc, (boolean[]) arg, true);
               return null;
            case GOTO_DIR_LIST_DEFAULT:
               DirManager.getInstance().showSearchPath();
               FvContext.connectFv(DirManager.getInstance(), fvc.vi);
               return null;
            case CN:
               instance.gotoNextPos(fvc, new boolean[]{false}, false);
               return null;
            case CP:
               instance.gotoNextPos(fvc, new boolean[]{true}, false);
               return null;
            default:
               throw new RuntimeException("vigroup:default");
         }
      }

      public static void setErrors(IoConverter<Position> newerrs) {
         //trace("setErrors" + newerrs);
         //trace("instance" + instance);
         instance.setFirst(newerrs);
      }

      public static TextEdit<Position> addPositionIoc(IoConverter<Position> ioc) {
         TextEdit<Position> newList =
            new TextEdit<>(ioc, instance, ioc.prop);
         instance.addList(newList);
         return newList;
      }

      /**
       * Replaces an existing named position list, or adds a new one.
       *
       * @param name the name to match (from IoConverter descriptor)
       * @param ioc the new IoConverter to use
       * @return the new TextEdit for the list
       */
      public static TextEdit<Position> replacePositionIoc(
            String name, IoConverter<Position> ioc) {
         removePositionIoc(name);
         return addPositionIoc(ioc);
      }

      /**
       * Removes a named position list entry if it exists.
       *
       * @param name the name to match
       */
      public static void removePositionIoc(String name) {
         for (int i = 1; i < instance.finish(); i++) {
            TextEdit<Position> entry = instance.at(i);
            if (name.equals(entry.getName())) {
               try {
                  FvContext.dispose(entry, instance);
               } catch (Exception e) {
                  trace("removePositionIoc: " + e);
               }
               instance.remove(i, 1);
               break;
            }
         }
      }

      static void flush() throws IOException {
         tagstack.clear();
         tagCache.clear();
         instance.flush();
         try {
            ctagFinder = new Ctag("tags");
         } catch (IOException e) {
            ctagFinder = null;
         }
      }

      private static void poptag(View vi) throws InputException {
         int size = tagstack.size();
         if (0 != size) {
            FileList.gotoposition(tagstack.get(size - 1), false, vi);
            tagstack.remove(size - 1);
         }
      }

      private static Object openDirectoryForCurrentFile(
            FvContext fvc) throws InputException, IOException {
         String dirPath = ".";
         String targetFile = null;
         if (fvc.edvec instanceof FileList) {
            Object item = fvc.at();
            if (item instanceof EditContainer) {
               FileDescriptor ifd =
                  ((EditContainer) item).fdes();
               if (ifd instanceof FileDescriptor.LocalFile) {
                  File f =
                     ((FileDescriptor.LocalFile) ifd).fh;
                  File p = f.getParentFile();
                  if (null != p && p.isDirectory()) {
                     dirPath = p.getPath();
                  }
                  targetFile = f.getName();
               }
            }
         } else {
            FileDescriptor fd = fvc.edvec.fdes();
            if (fd instanceof FileDescriptor.LocalFile) {
               File f = ((FileDescriptor.LocalFile) fd).fh;
               File parentDir = f.getParentFile();
               if (null != parentDir
                     && parentDir.isDirectory()) {
                  dirPath = parentDir.getPath();
               }
               targetFile = f.getName();
            }
         }
         return DirEdit.openDirectory(
            dirPath, fvc.vi, targetFile);
      }

      private static ArrayList<Position> findDirectories(String name) {
         ArrayList<Position> results = new ArrayList<>();
         if (name.isEmpty())
            return results;
         // Check if literal path is a directory
         File dirCheck = new File(name);
         if (dirCheck.isDirectory()) {
            FileDescriptor.LocalFile fd =
               FileDescriptor.LocalFile.make(dirCheck.getPath());
            results.add(new Position(0, 1, fd, "directory"));
         }
         // Search across the search path
         DirManager dm = DirManager.getInstance();
         for (FileDescriptor.LocalDir dir : dm.getSearchPath()) {
            File candidate = new File(dir.fh, name);
            if (candidate.isDirectory()) {
               FileDescriptor.LocalFile fd =
                  FileDescriptor.LocalFile.make(candidate.getPath());
               results.add(new Position(0, 1, fd, "directory"));
            }
         }
         return results;
      }

      private static String extractIdentifier(
            String text, int startIndex) {
         int endIndex = startIndex;
         for (; endIndex < text.length(); endIndex++) {
            char ch = text.charAt(endIndex);
            if (!Character.isJavaIdentifierPart(ch)
                  && '.' != ch)
               break;
         }
         return text.substring(startIndex, endIndex);
      }

      private void gototag(String tagName, FvContext fvc) throws
            InputException, IOException {
         Position originalPosition = fvc.getPosition(null);

         String searchText = null == tagName
                       ? fvc.at().toString()
                       : tagName;

         trace("gototag entry tagName="
            + (tagName == null ? "null" : "'" + tagName + "'")
            + " searchText='" + searchText + "'");

         if (filePositionPattern.reset(searchText).find()) {
            trace("gototag filePositionPattern matched file="
               + filePositionPattern.group(1) + " line="
               + filePositionPattern.group(4));
            FvContext fileContext = FileList.openFileName(
               filePositionPattern.group(1), fvc.vi);
            if (null != fileContext) {
               int ypos = Integer.parseInt(
                  filePositionPattern.group(4));
               fileContext.edvec.contains(ypos);
               fileContext.cursoryabs(ypos);
               tagstack.add(originalPosition);
               trace("gototag filePosition navigated, return");
               return;
            }
            trace("gototag filePosition openFileName returned null");
         }

         if (null == tagName) {
            tagName = fvc.at().toString();
            tagName = extractIdentifier(
               tagName, fvc.insertx());
            trace("gototag extracted sym='" + tagName + "'");
         }

         if (tagName.isEmpty()) {
            trace("gototag empty tagName, return");
            return;
         }

         // Ctags + mkid combined lookup
         TextEdit tagResults = taglookup(tagName, fvc.vi);
         if (null == tagResults) {
            trace("gototag taglookup returned null");
            throw new InputException("tag not found: " + tagName);
         }
         trace("gototag taglookup done tagResults="
            + tagResults.toString() + " finish="
            + tagResults.finish());

         // Check for any non-defpos entries in the result
         boolean hasResults = false;
         for (int i = 1; i < tagResults.readIn(); i++) {
            if (tagResults.at(i) != PositionIoc.defpos) {
               hasResults = true;
               break;
            }
         }
         trace("gototag hasResults=" + hasResults);
         if (tagResults.finish() <= 1 || !hasResults)
            throw new InputException("tag not found: " + tagName);

         tagstack.add(originalPosition);
         instance.setLastList(tagResults);
         trace("gototag done, pushed tagstack size="
            + tagstack.size());
      }

      /**
       * Check whether a TextEdit already contains a Position.
       * Used to prevent duplicate directory entries on repeated
       * lookups of the same symbol.
       */
      @SuppressWarnings("unchecked")
      private static boolean containsPosition(
            TextEdit<Position> list, Position pos) {
         int count = list.readIn();
         for (int i = 1; i < count; i++) {
            if (pos.equals(list.at(i)))
               return true;
         }
         return false;
      }

      private TextEdit<Position> createTagList(String sym)
            throws IOException {
         Position[] tagPositions = null;

         trace("createTagList ctagFinder=" + ctagFinder);
         if (null != ctagFinder) {
            tagPositions = ctagFinder.taglookup(sym);
            if ((tagPositions != null))
               if (tagPositions.length == 0)
                  tagPositions = null;
         }
         PositionIoc reader = new XrefReader(sym, tagPositions);

         TextEdit<Position> tagResults = new TextEdit<Position>(
            reader, tagPositions, instance, reader.prop);
         tagResults.contains(2);
         tagCache.put(sym, tagResults);
         if (tagResults.at(1) != PositionIoc.defpos)
            instance.insertOne(tagResults, instance.finish());
         return tagResults;
      }

      private static final Pattern DOT_PATTERN =
         Pattern.compile("\\.");
      private static final Matcher classTagMatcher = Pattern.compile(
         "\\bclass:(\\S*)\\b").matcher("");
      private static final Matcher fileTagMatcher = Pattern.compile(
         "\\bfile:(\\S*)\\b").matcher("");

      private static final class TagCacheDisposer
            implements EditContainer.FileDisposeListener {
         public void fileDisposed(EditContainer ev) {
            trace("file disposed " + ev);
            tagCache.remove(ev.getName());
         }
      }
      private TagCacheDisposer disposeListener =
         new TagCacheDisposer();

      private TextEdit taglookup(String qualifiedName, View vi)
            throws InputException {
         trace("taglookup qualifiedName='" + qualifiedName + "'");
         String[] nameSegments = DOT_PATTERN.split(qualifiedName);

         if (0 == nameSegments.length) {
            throw new InputException(
               "invalid tag target" + qualifiedName);
         }

         String simpleName = nameSegments[nameSegments.length - 1];
         @SuppressWarnings("unchecked") // raw TextEdit from HashMap
         TextEdit<Position> tagResults = tagCache.get(simpleName);
         trace("taglookup simpleName='" + simpleName
            + "' cached=" + (tagResults != null));
         try {
            if (null == tagResults) {
               tagResults = createTagList(simpleName);
               tagResults.addDisposeNotify(disposeListener);
            }

            int tagFinish = tagResults.finish();
            trace("taglookup tagResults.finish()" + tagFinish
               + " donereading=" + tagResults.donereading());

            int ctagCount = 1;
            for (; ctagCount < tagFinish; ctagCount++) {
               Position ctagpos = tagResults.at(ctagCount);
               if (!Ctag.getTagName(ctagpos).equals(simpleName))
                  break;
            }
            trace("taglookup ctagCount=" + ctagCount);

            if (ctagCount > 1) {
               navigateToBestScoredTag(tagResults,
                  ctagCount, nameSegments, qualifiedName, vi);
            } else if (tagResults.readIn() > 1) {
               navigateToFirstTag(tagResults,
                  qualifiedName, vi);
            }
         } catch (IOException e) {
            trace("PosListList taglookup caught " + e);
         }

         return tagResults;
      }

      private static void navigateToBestScoredTag(
            TextEdit<Position> tagResults, int ctagCount,
            String[] nameSegments, String qualifiedName,
            View vi) throws InputException {
         int[] scores = new int[ctagCount];
         for (int i = 1; i < ctagCount; i++)
            scores[i] = 0;
         int bestScore = 0;

         for (int symIndex = nameSegments.length - 2;
               symIndex >= 0;
               symIndex--) {
            String segment = nameSegments[symIndex];

            for (int i = 1; i < ctagCount; i++) {
               Position pos = tagResults.at(i);

               if (classTagMatcher.reset(
                     pos.comment).find()) {
                  String className =
                     classTagMatcher.group(1);
                  if (qualifiedName.indexOf(
                        className) != -1) {
                     scores[i] += className.length();
                  }
                  if (className.equals(segment)) {
                     scores[i] += 2;
                  }
               }

               if (fileTagMatcher.reset(
                        pos.filename.shortName).find()
                     && fileTagMatcher.group(1).equals(
                        segment)) {
                  scores[i] += 1;
               }
               if (scores[i] > bestScore)
                  bestScore = scores[i];
               else if (scores[i] < bestScore)
                  scores[i] = Integer.MIN_VALUE;
            }
         }
         for (int i = 1; i < ctagCount; i++) {
            if (scores[i] == bestScore) {
               if (FileList.gotoposition(
                     tagResults.at(i), false, vi)) {
                  FvContext tagfvc = FvContext.getcontext(
                     vi, tagResults);
                  if (tagfvc.edvec.contains(i))
                     tagfvc.cursoryabs(i);
                  break;
               }
            }
         }
      }

      @SuppressWarnings("unchecked")
      private static void navigateToFirstTag(
            TextEdit<Position> tagResults,
            String qualifiedName, View vi)
            throws InputException {
         // Merge any matching directories into the tag list
         ArrayList<Position> dirMatches =
            findDirectories(qualifiedName);
         trace("navigateToFirstTag dirMatches="
            + dirMatches.size());
         TextEdit<Position> tlist = tagResults;
         for (Position dp : dirMatches) {
            if (!containsPosition(tlist, dp))
               tlist.insertOne(dp, tlist.readIn());
         }

         // Navigate to first non-defpos entry
         for (int i = 1; i < tagResults.readIn(); i++) {
            Position p = tagResults.at(i);
            trace("navigateToFirstTag trying entry "
               + i + " p=" + p);
            if (p != PositionIoc.defpos
                  && FileList.gotoposition(
                     p, false, vi)) {
               FvContext tagfvc =
                  FvContext.getcontext(vi, tagResults);
               if (tagfvc.edvec.contains(i))
                  tagfvc.cursoryabs(i);
               break;
            }
         }
      }

      static void myassert(boolean flag, Object dump) {
         if (!flag)
            throw new RuntimeException("ASSERTION FAILURE " + dump.toString());
      }

      public static void main(String[] args) {

         try {
            filePositionPattern.reset("UI.java:1118 java.xxx.event.");
            myassert(filePositionPattern.find(), "UI");

            myassert(filePositionPattern.reset(
               "smtp_hfilter.c:254 ").find(), "");
            myassert(filePositionPattern.reset(
               "smtp_hfilter.c:254").find(), "");

            filePositionPattern.reset(
               "smtp_hfilter.c:254 hfilter_find SUBJECT"
               + "smtp_hfilter.c:266 hfilter_find SUBJECTsmtp_hfilter.c:"
               + "131 normalize_name_stbuf_ind 0 ,buffer[buf_ind]13");
            myassert(filePositionPattern.find(), "");
            myassert(filePositionPattern.group(4).equals("254"),
               filePositionPattern.group(4));
            myassert(filePositionPattern.group(1).equals(
               "smtp_hfilter.c"),
               filePositionPattern.group(1));
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
