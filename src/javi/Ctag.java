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

      private String name;
      private Position[] positions;
      private static final Position[] narray = new Position[0];
      private long filestart;
      private long fileend;

      TagEntry(String namei, long filestarti, long fileendi) {
         name = namei;
         filestart = filestarti;
         fileend = fileendi;
         //trace(toString());
      }
      Position [] getPositions(String tagfile) throws IOException {
         if (positions == null) {
            RandomAccessFile ctfile = new RandomAccessFile(tagfile , "r");
            ctfile.seek(filestart);
            ArrayList<Position> tempvec = new ArrayList<Position>();
            do {
               tempvec.add(getnextpos(ctfile));
            } while (fileend > ctfile.getFilePointer());

            positions = tempvec.toArray(narray);
         }

         return positions;
      }

      public String toString() {
         return name + " "
            + (positions == null ? -1 : positions.length)
            + " " +  filestart  + " " + fileend + " ";
      }

      private Position getnextpos(RandomAccessFile ctfile) throws IOException {
         //trace("getnextpos");
         String line;
         do {
            line = ctfile.readLine();
            if (null == line)
               return null;
            //trace("tagline:" + line);
         } while (line.charAt(0) == '!');
         String [] spl = tabpat.split(line, 3);
         String comment = "tag:" + spl[0];
         String file = new String(spl[1].toCharArray());
         line = spl[2];
         int pos = line.indexOf(';');
         int y = Integer.parseInt(line.substring(0, pos));
         comment += '\t' + line.substring(pos + 4, line.length());
         return new Position(0, y, file, comment);
      }
   }
   /* Copyright 1996 James Jensen all rights reserved */
   static final String copyright = "Copyright 1996 James Jensen";

   private String ctfilename;
   private ArrayList<TagEntry> carray = new ArrayList<TagEntry>();

   Ctag(String tagfilename) throws IOException {
      ctfilename = tagfilename;
      RandomAccessFile ctfile = new RandomAccessFile(ctfilename , "r");
      try {
         carray.add(new TagEntry(String.valueOf(Character.MIN_VALUE), 0, 0));
         carray.add(new TagEntry(String.valueOf(Character.MAX_VALUE),
            ctfile.length(), ctfile.length()));
      } finally {
         ctfile.close();
      }
   }

   Position[] taglookup(String name) throws IOException {
      int hirange = carray.size() - 1;
      int lowrange = 0;
      int guess = hirange / 2;
      TagEntry te;
      te = carray.get(guess);
      while (true) {
         if (lowrange == hirange - 1) {
            te = filelookup(name, lowrange);
            return te == null
                   ? null
                   : te.getPositions(ctfilename);
         }

         int compare = te.name.compareTo(name);
         if (compare == 0) {
            return te.getPositions(ctfilename);
         } else {
            if (compare < 0) {
               if (lowrange == guess)
                  throw new RuntimeException("ctag.taglookup lowrange");
               else {
                  lowrange = guess;
                  guess = guess + (hirange - guess) / 2;
                  te = carray.get(guess);
                  if (te.name.equals(name))
                     return te.getPositions(ctfilename);
               }
            } else {
               if (hirange == guess)
                  throw new RuntimeException("ctag.taglookup hirange");
               else {
                  hirange = guess;
                  guess = guess - (guess - lowrange) / 2;
                  te = carray.get(guess);
                  if (te.name.equals(name))
                     return te.getPositions(ctfilename);
               }
            }
         }
      }
   }

   private TagEntry filelookup(String name, int guess) throws IOException {
      //trace("filelookup guess = " + guess);
      RandomAccessFile ctfile = new RandomAccessFile(ctfilename, "r");
      TagEntry te1;
      while (true) {
         te1 = carray.get(guess);
         TagEntry te2 = carray.get(guess + 1);
         //trace("findfile te1 " + te1 + " te2 " + te2);
         te1 = filefind(ctfile, te1.fileend, te2.filestart);
         if (te1 == null)
            break;
         carray.add(guess + 1, te1);

         int compare = te1.name.compareTo(name);
         //trace ("compare = " + compare);
         if (compare < 0)
            guess++;
         else  if (compare == 0)
            break;
      }
      ctfile.close();

      return te1;
   }
   private static Pattern tabpat = Pattern.compile("\\t");

   static String getTagName(Position p) {
      if (p != null && p.comment != null && p.comment.startsWith("tag:")) {
         String[] spl = tabpat.split(p.comment, 2);
         return spl[0].substring(4, spl[0].length());
      } else
         return "";
   }

   private TagEntry filefind(RandomAccessFile ctfile,
      long start, long end) throws IOException {
      //trace("filefind start " + start + " end " + end);
      if (start == end)
         return null;

      ctfile.seek((start + end) / 2);
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
         if  (line == null || !line.startsWith(curtag))
            break;
         endmark = ctfile.getFilePointer();
      }

      //scan backwards to find non matching of label
      //trace("backmark " + backmark);
      for (long offset = backmark - 200;; offset -= 200) {
         ctfile.seek(offset);
         line = ctfile.readLine(); // line up to next new line
         if  (line == null || !line.startsWith(curtag))
            break;
         offset -= line.length();
      }

      do {
         backmark = ctfile.getFilePointer();
         line = ctfile.readLine(); // line up to next new line
      } while  (line != null && !line.startsWith(curtag));

      return backmark == endmark
             ? null
             : new TagEntry(curtag.substring(
                0, curtag.length() - 1), backmark, endmark);
   }

   /* Copyright 1996 James Jensen all rights reserved */
   public static void main(String[] args) {
      try {
         Ctag ct = new Ctag(args[0]);
//       ct.taglookup("stderr");
         ct.taglookup("ALButton");
         ct.taglookup("zprocess");
      } catch (Throwable t) {
         trace("main caught " + t);
         t.printStackTrace();
      }
   }
}
