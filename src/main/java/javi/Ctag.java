package javi;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.regex.Pattern;
import static history.Tools.trace;

/*
   this could be improved by allowing other excmds.  It currently only
   allows line numbers
*/

final class Ctag {
   private static final class TagEntry {

      private final String name;
      private Position[] positions;
      private static final Position[] narray = new Position[0];
      private final long filestart;
      private final long fileend;

      TagEntry(String namei, long filestarti, long fileendi) {
         name = namei;
         filestart = filestarti;
         fileend = fileendi;
         //trace(this);
      }

      /**
       * Gets the positions for this tag entry, loading from file if needed.
       *
       * @param tagfile the path to the ctags file
       * @return array of Position objects for this tag
       * @throws IOException if an I/O error occurs reading the tag file
       */
      Position[] getPositions(String tagfile) throws IOException {
         if (null == positions) {
            try (RandomAccessFile ctfile = new RandomAccessFile(tagfile, "r")) {
               ctfile.seek(filestart);
               ArrayList<Position> tempvec = new ArrayList<>();
               do {
                  tempvec.add(getnextpos(ctfile));
               } while (fileend > ctfile.getFilePointer());

               positions = tempvec.toArray(narray);
            }
         }

         return positions;
      }

      public String toString() {
         return name + " "
            + (null == positions ? -1 : positions.length)
            + " " +  filestart  + " " + fileend + " ";
      }

      private static Position getnextpos(RandomAccessFile ctfile) throws
            IOException {
         //trace("getnextpos");
         String line;
         do {
            line = ctfile.readLine();
            if (null == line)
               return null;
            //trace("tagline:" + line);
         } while ('!' == line.charAt(0));
         String[] spl = tabpat.split(line, 3);
         String comment = "tag:" + spl[0];
         String file = new String(spl[1].toCharArray());
         line = spl[2];
         int pos = line.indexOf(';');
         int y = Integer.parseInt(line.substring(0, pos));
         comment += '\t' + line.substring(pos + 4, line.length());
         return new Position(0, y, file, comment);
      }
   }

   private String ctfilename;
   private ArrayList<TagEntry> carray = new ArrayList<>();

   /**
    * Creates a new Ctag instance by reading the tag file index.
    * Initializes the tag entry cache with boundary entries.
    *
    * @param tagfilename the path to the ctags file
    * @throws IOException if an I/O error occurs reading the tag file
    */
   Ctag(String tagfilename) throws IOException {
      ctfilename = tagfilename;
      try (RandomAccessFile ctfile = new RandomAccessFile(ctfilename, "r")) {
         carray.add(new TagEntry(String.valueOf(Character.MIN_VALUE), 0, 0));
         carray.add(new TagEntry(String.valueOf(Character.MAX_VALUE),
            ctfile.length(), ctfile.length()));
      }
   }

   /**
    * Look up a tag name in the ctags file using binary search.
    *
    * <p>Performs a binary search through the in-memory tag index, falling
    * back to file lookup when the exact entry isn't cached.</p>
    *
    * @param name the tag name to look up
    * @return array of Position objects for the tag, or null if not found
    * @throws IOException if there's an error reading the tags file
    */
   Position[] taglookup(String name) throws IOException {
      //trace("looking up " + name);
      int hirange = carray.size() - 1;
      int lowrange = 0;
      int guess = hirange >> 1;
      TagEntry te = carray.get(guess);
      // B8: Add iteration limit to prevent infinite loop in case of data corruption
      int maxIterations = carray.size() + 10;
      int iterations = 0;
      while (iterations++ < maxIterations) {
         if (lowrange == hirange - 1) {
            te = filelookup(name, lowrange);
            return null == te
                   ? null
                   : te.getPositions(ctfilename);
         }

         int compare = te.name.compareTo(name);
         if (0 == compare) {
            return te.getPositions(ctfilename);
         } else {
            if (compare < 0) {
               if (lowrange == guess)
                  throw new RuntimeException("ctag.taglookup lowrange");
               else {
                  lowrange = guess;
                  guess += (hirange - guess) >> 1;
                  te = carray.get(guess);
                  if (te.name.equals(name))
                     return te.getPositions(ctfilename);
               }
            } else {
               if (hirange == guess)
                  throw new RuntimeException("ctag.taglookup hirange");
               else {
                  hirange = guess;
                  guess -= (guess - lowrange) >> 1;
                  te = carray.get(guess);
                  if (te.name.equals(name))
                     return te.getPositions(ctfilename);
               }
            }
         }
      }
      // B8: Log warning if iteration limit was reached
      trace("Ctag.taglookup: iteration limit reached for name=" + name);
      return null;
   }

   /**
    * Look up a tag by reading directly from the file.
    *
    * Looks up a tag name by searching through the tag file.
    * Uses binary search through cached tag entries and reads more
    * entries from the file as needed.
    *
    * @param name the tag name to look up
    * @param guess the initial position in the cache to start searching
    * @return the TagEntry if found, null otherwise
    * @throws IOException if there's an error reading the file
    */
   private TagEntry filelookup(String name, int guess) throws IOException {
      //trace("filelookup guess = " + guess);
      try (RandomAccessFile ctfile = new RandomAccessFile(ctfilename, "r")) {
         // B8: Add iteration limit to prevent infinite loop
         int maxIterations = 10000;
         int iterations = 0;
         while (iterations++ < maxIterations) {
            TagEntry te1 = carray.get(guess);
            TagEntry te2 = carray.get(guess + 1);
            //trace("findfile te1 " + te1 + " te2 " + te2);
            te1 = filefind(ctfile, te1.fileend, te2.filestart);
            if (null == te1)
               return null;
            carray.add(guess + 1, te1);

            int compare = te1.name.compareTo(name);
            //trace ("compare = " + compare);
            if (0 > compare)
               guess++;
            else  if (compare == 0)
               return te1;
         }
         // B8: Log warning if iteration limit was reached
         trace("Ctag.filelookup: iteration limit reached for name=" + name);
         return null;
      }
   }

   private static Pattern tabpat = Pattern.compile("\\t");

   static String getTagName(Position p) {
      if (null != p && null != p.comment && p.comment.startsWith("tag:")) {
         String[] spl = tabpat.split(p.comment, 2);
         return spl[0].substring(4, spl[0].length());
      } else
         return "";
   }

   private static TagEntry filefind(RandomAccessFile ctfile,
      long start, long end) throws IOException {
      //trace("filefind start " + start + " end " + end);
      if (start == end)
         return null;

      ctfile.seek((start + end) >> 1);
      ctfile.readLine(); // skip to next line
      long backmark = ctfile.getFilePointer(); // where to start again backwards
      String line = ctfile.readLine();
      //trace("backmark = " + backmark);
      if ((ctfile.getFilePointer() > end) || (line == null)) {
         ctfile.seek(start);
         backmark = ctfile.getFilePointer(); // where to start again backwards
         line = ctfile.readLine();
         if (line == null) // end of file
            return null;
      }

      String curtag = line.substring(0, line.indexOf('\t') + 1);
      //trace("new curtag " + curtag);

      long endmark = ctfile.getFilePointer();

      // look for all possible tag entries with same tag
      while (ctfile.getFilePointer() < end) {
         line = ctfile.readLine();
         if  (null == line || !line.startsWith(curtag))
            break;
         endmark = ctfile.getFilePointer();
      }

      //scan backwards to find non matching of label
      //trace("backmark " + backmark);
      for (long offset = backmark - 300;; offset -= 300) {
         //trace("back tracking offset " + offset);
         ctfile.seek(offset);
         line = ctfile.readLine(); // find beginning of line
         line = ctfile.readLine(); // line up to next new line
         if  (null == line || !line.startsWith(curtag))
            break;
         offset -= line.length();
      }

      do {
         backmark = ctfile.getFilePointer();
         line = ctfile.readLine(); // line up to next new line
      } while  (null != line && !line.startsWith(curtag));

      return backmark == endmark
             ? null
             : new TagEntry(curtag.substring(
                0, curtag.length() - 1), backmark, endmark);
   }

   public static void main(String[] args) {
      trace("");
      try {
         Ctag ct = new Ctag(args[0]);
         Position[] parr = ct.taglookup("main");

         for (Position p : parr)
            trace("pos " + p);
         ct.taglookup("zprocess");
      } catch (Throwable t) {
         trace("main caught " + t);
         t.printStackTrace();
      }
   }
}
