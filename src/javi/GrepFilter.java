package javi;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


final class GrepFilter implements FilenameFilter {

   private Matcher regex;
   private boolean invert;

   GrepFilter(String spec, boolean inverti) {
      regex =  Pattern.compile(spec).matcher("");
      invert = inverti;
      //trace("spec = " + spec + " invert = " + invert);
   }

   public boolean accept(File dir, String name) {
      //trace("filename = " + name);
      return  invert ^ regex.reset(name).find();
   }
}
