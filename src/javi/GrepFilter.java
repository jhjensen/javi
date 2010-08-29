package javi;

import java.io.FilenameFilter;
import java.io.File;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


class GrepFilter implements FilenameFilter {
   private Matcher regex;
   private boolean invert;

   GrepFilter(String spec, boolean inverti) {
      regex =  Pattern.compile(spec).matcher("");
      invert = inverti;
      //trace("spec = " + spec + " invert = " + invert);
   }

   public boolean accept(File dir, String name) {

      //trace("filename = " + name);
      regex.reset(name);
      return  invert ^ regex.find();
   }
   static void trace(String str) {
      Tools.trace(str, 1);
   }
}
