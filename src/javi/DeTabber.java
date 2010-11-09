package javi;

public final class DeTabber {
   private static int mytabs = 0;
   private static String[] tabstrings;

   private DeTabber() {
      throw new RuntimeException("attempt to new singleton");
   }

   static String mktabstr(int tab) {
      StringBuilder tst = new StringBuilder(tab);
      for (int i = 0; i < tab; i++)
         tst.append(' ');

      return tst.toString();
   }

   private static void initts(int tabstop) {
      tabstrings = new String[tabstop];
      tabstrings[0] =  mktabstr(tabstop);
      for (int i = 1; i < tabstop; i++)
         tabstrings[i] = tabstrings[0].substring(i);
   }

   public static String deTab(String text , int tabOffset,
         int tabstop, int[] tracking) {
      //trace("deTab " + tabstop + " tabOffset " + tabOffset);
      if (tabstop != mytabs)
         initts(tabstop);

      StringBuilder tbuf = new StringBuilder(text);

      do {
         tbuf.replace(tabOffset, tabOffset + 1,
            tabstrings[tabOffset % tabstop]);
         int addchars = tabstop + (-tabOffset) % tabstop;
         addchars = addchars == 0
                    ? tabstop - 1
                    : addchars - 1;
         //trace("addchars = " + addchars + " tabOffset = " + tabOffset);
         for (int i = 0; i < tracking.length; i++)
            if (tracking[i] != -1 && tracking[i] > tabOffset)
               tracking[i] = Integer.MAX_VALUE - addchars <= tracking[i]
                             ? Integer.MAX_VALUE
                             : tracking[i] + addchars;

         tabOffset = tbuf.indexOf("\t", (tabOffset + addchars));
      } while (tabOffset != -1);
      return tbuf.toString();
   }

   public static int tabFind(String line, int tabOffset,
      int tabstop, int charoffset) {
      //trace("tabFind tabOffset " + tabOffset + " tabstop " + tabstop + " charoffset " +  charoffset + " line " + line);
      if (tabstop != mytabs)
         initts(tabstop);

      if (charoffset <= tabOffset)
         return charoffset;

      int tadd = 0;
      int xoffset = tabOffset;
      charoffset -= tabOffset;
      //trace("charoffset " + charoffset);
      for (int it = tabOffset; it < line.length() && charoffset > 0; it++)
         if (line.charAt(it) == '\t') {
            int addchars = tabstop + (-xoffset - tadd) % tabstop;
            addchars = addchars == 0
                       ? tabstop - 1
                       : addchars - 1;
            charoffset -= addchars + 1;
            tadd += addchars;
            //trace("addchars " + addchars + " tadd " + tadd + " charoffset " + charoffset + " xoffset " + xoffset);
            if (charoffset >= 0)
               xoffset++;
         } else {
            xoffset++;
            charoffset--;
            //trace("it " + it + " tadd " + tadd + " charoffset " + charoffset + " xoffset " + xoffset + " line.charAt(it) " + line.charAt(it));
         }
      return xoffset;
   }
   public static void main(String[] args) {
      try {
         //final static int tabFind(String line,int tabOffset,int tabstop,int charoffset) {
         // tab test
         Tools.Assert(tabFind(
            "123456789012345678\t01234567\t901234567 "
            , 18, 8, 34) == 27, null);
         Tools.Assert(tabFind("\tat ", 0, 8, 8) == 1, null);
         Tools.Assert(tabFind("\tat ", 0, 8, 10) == 3, null);
         Tools.Assert(tabFind(
            "123456789012345678\t01234567\t901234567 "
            , 18, 8, 0) == 0, null);
         Tools.Assert(tabFind("1234\t1234", 4, 4, 5) == 4, null);
         //trace ("" + tabFind("123456789012345678\t01234567\t901234567 ",18,8,37) );
         Tools.Assert(tabFind(
            "123456789012345678\t01234567\t901234567 ",
            18, 8, 37) == 27, null);
         Tools.Assert(tabFind(
            "\tat history.PersistantStack.set\tFile(PersistantStack.java:518"
            , 0, 8, 0) == 0, null);
         Tools.Assert(tabFind("1234\t1234", 4, 4, 4) == 4, null);
      } catch (Throwable e) {
         Tools.trace("main caught exception " + e);
         e.printStackTrace();
      }
      System.exit(0);
   }
}
