/* this is code for dynamicly adding a class to javi.  it has been a while 
   since testing
private int tvar=0;
private void tcomp(fvcontext fvc) throws IOException,inputexception {
   int i;
   String tempfile = "temp" + tvar;
   String fullclassname= fvc.edvec.at(1).toString();
   if (fullclassname.startsWith("package "))
      fullclassname = fullclassname.substring(8,fullclassname.length()-1) + ".";
   else
      fullclassname =null;
   fullclassname += tempfile;
   String[] sa = {"-d","d:\\java","d:\\java\\" + tempfile + ".java"};
   String realfile= fvc.edvec.ioc.getCanonicalName();
   fvc.edvec.printout();
   try {
      String classname= realfile.substring(
                 realfile.lastIndexOf("\\")+1,
                 realfile.lastIndexOf(".jav"));
      extext cl = new extext((extext)fvc.edvec);
      if (g_pattern==null) {
         g_pattern = Regexp.compile("xyzzy");
      }   
      match_pattern = Regexp.compile(classname);
      cl.commandproc(1,cl.finish()-1,g_pattern, false ,
         match_pattern,true,'s', tempfile,
         0,null);
      cl.commandproc(1,cl.finish()-1,null, false ,
         null,false,'w', null,
         0,sa[2]);
      
      extext tempposlist=new extext(new mycomp(sa));
      tempposlist.finish();
      if (tempposlist.finish()==2 && ((position)tempposlist.at(1)).filename==null) {
         tvar++;
         loadgroup(realfile,fullclassname);
      } else {
//trace("real file = " + realfile);
         Regexp match_pattern2 = Regexp.compile("^.*" + tempfile + "\\.java");
         tempposlist.commandproc(1,tempposlist.finish()-1,null, false ,
           match_pattern2,true,'s', realfile.replace('\\','/'),
           0,null);
         setErrors(tempposlist);
      }
   
   } catch (inputexception e) {
      throw new RuntimeException("vigroup caught inputexception");
   } catch (StringIndexOutOfBoundsException e) {
      e.printStackTrace();
      throw new inputexception("invalid filename");
   }
}

private Regexp match_pattern;
private Regexp g_pattern;
*/

