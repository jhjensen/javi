package javt;


abstract class Regexp {
   abstract Result searchForward(String str,int start);
   abstract Result searchBackward(String str,int start);
   
   static int ind = 3;  // 0 looks up set to constant for force package
   static Regexp compile(String spec) throws inputexception {
switch (ind) {
     case 0: 
//         trace("looking for regexp package");
         break;
     case 1: 
//         trace("using stevesoft regex package " + spec);
         break;
     case 2: 
//         trace("using gnu regex package " + spec);
         break;
     case 3: 
//         trace("using starwave regex package " + spec);
         break;
     case 4: 
//         trace("using apache regexp package " + spec);
     case 5: 
//         trace("using java.util.regexp package " + spec);
         break;
}
     
         switch (ind) {
            case 0:
               try {
                  ind = 4;
                  trace("using apache regex package " + spec);
                  new apRegexp("a");
                  return new apRegexp(spec);
               } catch (NoClassDefFoundError e3) {
               try {
                  ind = 1;
                  trace("using stevesoft regex package " + spec);
                  new stRegexp("a");
                  return new stRegexp(spec);
/*
               } catch (NoClassDefFoundError e) {
               try {
                  trace("using gnu regex package " + spec);
                  ind = 2;
                  new gnuRegexp("a");
                  return new gnuRegexp(spec);
*/
               } catch (NoClassDefFoundError e1) {
               try {
                  ind = 3;
                  trace("using starwave regex package " + spec);
                  new starRegexp("a");
                  return new starRegexp(spec);
               } catch (NoClassDefFoundError e2) {
                  throw new inputexception("unable to find Regex class");
               }
               }
               }
//            }
            case 1: return new stRegexp(spec);
        //    case 2: return new gnuRegexp(spec);
            case 3: return new starRegexp(spec);
            case 4: return new apRegexp(spec);
           // case 5: return new utRegexp(spec);
            default:
                 throw new RuntimeException("Regexp.compile: impossible number");
         }
   }
   
   static Regexp compile(String spec,boolean caseinsensitive) throws inputexception{
switch (ind) {
     case 0: 
         //trace("looking for regexp package");
         break;
     case 1: 
         //trace("using stevesoft regex package " + spec);
         break;
     case 2: 
         //trace("using gnu regex package " + spec);
         break;
     case 3: 
         //trace("using starwave regex package " + spec);
         break;
}
         switch (ind) {
            case 0:
                 compile ("xx"); // set ind
                 return compile(spec,caseinsensitive);
            case 1: return new stRegexp(spec,caseinsensitive);
            //case 2: return new gnuRegexp(spec,caseinsensitive);
            case 3: return new starRegexp(spec,caseinsensitive);
            case 4: return new apRegexp(spec,caseinsensitive);
            case 5: //return new utRegexp(spec,caseinsensitive);
            default:
                 throw new RuntimeException("Regexp.compile: impossible number");
         }
   }

/*
   private static class utResult extends Result {
      java.util.regex.Matcher sres;
      int offset;
      public String toString() {
         return sres.toString();
      }
      utResult(java.util.regex.Matcher resi,int offseti) {
        sres = resi;
        offset=offseti;
        if (sres==null)
           throw new RuntimeException("null regex result");
      }
      String getMatch(int index) {
          return sres.group(index);
      }
      int getMatchStart(int index) {
          return sres.start(index)+offset;
      }
      int getMatchEnd(int index) {
          return sres.end(index)+offset;
      }
      int getMatchStart() {
        return sres.start(0)+offset;
      }
      int getMatchEnd() {
        return sres.end(0)+offset;
      }
   }
   private static class utRegexp extends Regexp{

      private java.util.regex.Pattern rex;
      private java.util.regex.Matcher matcher;
      int offset;
   
      public String toString() {
         return rex.toString();
      }
      utRegexp(String spec,boolean caseinsensitive) throws inputexception {
         
        try {
         rex = java.util.regex.Pattern.compile(spec, caseinsensitive ?
              java.util.regex.Pattern.CASE_INSENSITIVE
            : 0);
         matcher = rex.matcher("");
        } catch (java.util.regex.PatternSyntaxException e) {
           throw new inputexception("Illegal regular expression:" + spec);
        } catch (IllegalArgumentException e) {
           throw new inputexception("Illegal arguments for regular expression:" + spec);
        }

      }
 
      utRegexp(String spec) throws inputexception {
        try {
           rex = java.util.regex.Pattern.compile(spec);
           matcher = rex.matcher("");
        } catch (java.util.regex.PatternSyntaxException e) {
           throw new inputexception("Illegal regular expression:" + spec);
        }
      }
      
      Result searchForward(String str,int start) {
trace("searchForwared str = " + str + " start " + start);
         if (start>str.length())
            return null;
         offset=start;
         str=str.substring(start,str.length());
         matcher.reset(str);
         if (matcher.find())
         {
//          trace("searchForwared matched " + rex.matchedFrom(0));
//          trace("searchForwared matched " + rex.matchedFrom(1));
//          trace("searchForwared matched " + rex.matchedFrom());
            return new Regexp.utResult(matcher,offset);
         } else 
            return null;
      }
      Result searchBackward(String str,int start) {
    
trace("Regexp.utRegexp.searchBackward: start = " + start  + " " + str);
        str = str.substring(0,start);
        matcher.reset(str);
        int temp =-1;
      
        while (matcher.find())
           temp = matcher.start(0);

        if (temp==-1)
           return null;

        matcher.reset();
        while (matcher.find())
           if (temp == matcher.start(0))
              return new Regexp.utResult(matcher,0);
        throw new RuntimeException("Regexp.java reverse search logic error");

      }
   }
*/

   private static class apResult extends Result {
      org.apache.regexp.RE sres;
      public String toString() {
         return sres.toString();
      }
      apResult(org.apache.regexp.RE resi) {
        sres = resi;
        if (sres==null)
           throw new RuntimeException("null regex result");
      }
      String getMatch(int index) {
          return sres.getParen(index);
      }
      int getMatchStart(int index) {
          return sres.getParenStart(index);
      }
      int getMatchEnd(int index) {
          return sres.getParenEnd(index);
      }
      int getMatchStart() {
        return sres.getParenStart(0);
      }
      int getMatchEnd() {
        return sres.getParenEnd(0);
      }
   }
   private static class apRegexp extends Regexp{

      private org.apache.regexp.RE rex;
      static org.apache.regexp.RECompiler comp=new org.apache.regexp.RECompiler();
   
      public String toString() {
         return rex.toString();
      }
      apRegexp(String spec,boolean caseinsensitive) throws inputexception {
         
        try {
         rex = new org.apache.regexp.RE(comp.compile(spec), caseinsensitive
           ? org.apache.regexp.RE.MATCH_CASEINDEPENDENT
           : 0);
        } catch (org.apache.regexp.RESyntaxException e) {
           throw new inputexception("Illegal regular expression:" + spec);
        }

      }
 
      apRegexp(String spec) throws inputexception {
        try {
           rex = new org.apache.regexp.RE(comp.compile(spec));
        } catch (org.apache.regexp.RESyntaxException e) {
           throw new inputexception("Illegal regular expression:" + spec);
        }
      }
      
      Result searchForward(String str,int start) {
         if (rex.match(str,start)) {
//          trace("searchForwared matched " + rex.matchedFrom(0));
//          trace("searchForwared matched " + rex.matchedFrom(1));
//          trace("searchForwared matched " + rex.matchedFrom());
            return new Regexp.apResult(rex);
         } else 
            return null;
      }
      Result searchBackward(String str,int start) {
trace("Regexp.apRegexp.searchBackward: start = " + start  + " " + str);
        str = str.substring(0,start);
        int temp =-1;
      
        while (rex.match(str,temp+1))
           temp = rex.getParenStart(0);

        if (temp!=-1)
           return new Regexp.apResult(rex);
        else
           return null;
      }
   }
   private static class stRegexp extends Regexp{
      com.stevesoft.pat.Regex rex;

      public String toString() {
         return rex.toString();
      }
      stRegexp(String spec,boolean caseinsensitive) throws inputexception {
         this(caseinsensitive ? "(?i)" : "" + spec);
      }
 
      stRegexp(String spec) throws inputexception {
        rex = new com.stevesoft.pat.Regex();
        try {
           rex.compile(spec);
        } catch (com.stevesoft.pat.RegSyntax e) {
           throw new inputexception("Illegal regular expression:" + spec);
        }
      }
      
      Result searchForward(String str,int start) {
         if (rex.searchFrom(str,start)) {
//          trace("searchForwared matched " + rex.matchedFrom(0));
//          trace("searchForwared matched " + rex.matchedFrom(1));
//          trace("searchForwared matched " + rex.matchedFrom());
            return new Regexp.stResult(rex);
         } else 
            return null;
      }
      Result searchBackward(String str,int start) {
        int temp =-1;
      
        if (rex.searchRegion(str,0,start)) {
           temp = 1+ rex.matchedFrom();
      
           while(rex.searchRegion(str,temp,start))
              temp = 1+ rex.matchedFrom();
           rex.searchRegion(str,temp-1,start);
           return new Regexp.stResult(rex);
        } else
           return null;
      }
   }
   /*
   private static class gnuRegexp extends Regexp{

      gnu.regexp.RE rex;
      
      public String toString() {
         return rex.toString();
      }
      gnuRegexp(String spec) throws inputexception {
        try {
           rex = new gnu.regexp.RE(spec);
        } catch (gnu.regexp.REException e) {
           throw new inputexception("Illegal regular expression:" + spec);
        }
      }
      
      gnuRegexp(String spec,boolean caseinsensitive) throws inputexception{
         try {
            rex = new gnu.regexp.RE(spec,gnu.regexp.RE.REG_ICASE);
         } catch (gnu.regexp.REException e) {
            throw new inputexception("Illegal regular expression:" + spec);
         }
      }
      Result searchForward(String str,int start) {
        gnu.regexp.REMatch temp = rex.getMatch(str,start);
        if (temp==null)
          return null;
        return new gnuResult(temp);
      }
      Result searchBackward(String str,int start) {
        gnu.regexp.REMatch[] temp = rex.getAllMatches(str.substring(0,start));
        if (temp.length==0)
          return null;
        return new gnuResult(temp[temp.length-1]);
      }
      }
   */
   private static class starRegexp extends Regexp{

      starwave.util.regexp.Regexp rex;

      public String toString() {
         return rex.toString();
      }
      starRegexp (String spec) throws inputexception {
         try {
         rex = starwave.util.regexp.Regexp.compile(spec);
         } catch (starwave.util.regexp.MalformedRegexpException e) {
            throw new inputexception("Illegal regular expression:" + spec);
         }
      }
      
      starRegexp (String spec,boolean caseinsensitive) throws inputexception{
         try {
            rex = starwave.util.regexp.Regexp.compile(spec,caseinsensitive);
         } catch (starwave.util.regexp.MalformedRegexpException e) {
            throw new inputexception("Illegal regular expression:" + spec);
         }
      }
      Result searchForward(String str,int start) {
        starwave.util.regexp.Result temp = rex.searchForward(str,start);
        if (temp==null)
          return null;
        return new Regexp.starResult(temp);
      }
      Result searchBackward(String str,int start) {
        starwave.util.regexp.Result temp = rex.searchBackward(str,start);
        if (temp==null)
          return null;
        return new Regexp.starResult(temp);
      } 
   }
      
  private static class stResult extends Result {
     com.stevesoft.pat.Regex sres;

      public String toString() {
         return sres.toString();
      }
     stResult(com.stevesoft.pat.Regex resi) {
       sres = resi;
       if (sres==null)
          throw new RuntimeException("null regex result");
     }
     String getMatch(int index) {
       if (index==0)
          return sres.stringMatched();
       else 
          return sres.stringMatched(index);
     }
     int getMatchStart(int index) {
//   trace("getMatchSTart  index = " + index + " returning " + sres.matchedFrom(index));
       if (index==0)
         return sres.matchedFrom();
       else
         return sres.matchedFrom(index);
     }
     int getMatchEnd(int index) {
       if (index==0)
          return sres.matchedTo();
       else
          return sres.matchedTo(index);
     }
     int getMatchStart() {
       return sres.matchedFrom();
     }
     int getMatchEnd() {
       return sres.matchedTo();
     }
   }
 /*
 private static class gnuResult extends Result{

    gnu.regexp.REMatch sres;

      public String toString() {
         return sres.toString();
      }
    gnuResult(gnu.regexp.REMatch resi) {
       sres = resi;
       if (sres==null)
           throw new RuntimeException("null regex result");
    }
    String getMatch(int index) {
      return sres.toString(index);
    }
    int getMatchStart(int index) {
      return sres.getSubStartIndex(index);
    }
    int getMatchEnd(int index) {
      return sres.getSubEndIndex(index);
    }
    int getMatchStart() {
      return sres.getStartIndex();
    }
    int getMatchEnd() {
      return sres.getEndIndex();
    }
  }
  */

  private static class starResult extends Result{

  starwave.util.regexp.Result sres;
  
      public String toString() {
         return sres.toString();
      }
    starResult(starwave.util.regexp.Result resi) {
       sres = resi;
       if (sres==null)
          throw new RuntimeException("null regex result");
    }
    String getMatch(int index) {
      try {
        return sres.getMatch(index);
      } catch (starwave.util.regexp.NoSuchMatchException e) {
        return null;
      }
    }
    int getMatchStart(int index) {
      return sres.getMatchStart(index);
    }
    int getMatchEnd(int index) {
      return sres.getMatchEnd(index);
    }
    int getMatchStart() {
      return sres.getMatchStart();
    }
    int getMatchEnd() {
      return sres.getMatchEnd();
    }
  }
  static void trace(String str) {
     ui.trace(str,1);
  }
}
