package javi;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

class GState {
   
   private static Pattern searchRegex = Pattern.compile("");

   static Matcher getRegex() {
      return searchRegex.matcher("");
   }

   static void setRegex(String regex,int flags) {
      searchRegex = Pattern.compile(regex,flags);
   }
}
