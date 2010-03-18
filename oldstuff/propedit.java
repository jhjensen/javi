package javi;
/*
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.beans.PropertyDescriptor;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.Serializable;
import java.io.StreamTokenizer;
import java.io.StringReader;
//import java.lang.StringBuffer;
import java.util.Vector;

abstract class propedit {

static Object nullarray[] = {};
PropertyDescriptor prop;
int stringStart;
Object ob;

propedit(PropertyDescriptor propi,Object obi) {
   prop=propi;
   stringStart = prop.getDisplayName().length() +3;
   ob = obi;
}
public String toString() {
   try {
String temp =  prop.getDisplayName() + " - " ;
System.out.println(temp);
    temp +=  prop.getReadMethod().invoke(ob,nullarray) ;
    return temp;
   } catch( InvocationTargetException e) {
      throw new RuntimeException();
   } catch( IllegalAccessException e) {
      throw new RuntimeException();
   }
}

abstract String stringchange(String newval) throws Exception;
static String [] stringa = new String[0];
static extext getbeaninfo(Object obin) {
   try {
     BeanInfo bi = Introspector.getBeanInfo(obin.getClass());
     PropertyDescriptor pd[] = bi.getPropertyDescriptors();
     propedit pe[] = new propedit[pd.length];
     int i;
     for (i=0;i<pd.length;i++) {
System.out.println("class = " + pd[i].getPropertyType());
        if (pd[i].getPropertyType() == Integer.TYPE) {
           pe[i]=new intpropedit(pd[i],obin);
        } else if (pd[i].getPropertyType() == (stringa.getClass())) {
           pe[i]=new stringarraypropedit(pd[i],obin);
        } else
           pe[i]=new stringpropedit(pd[i],obin);
        System.out.println(pe[i]);
     }   
     extext plist =new extext((iocontroller)null);
     plist.insert(pe,1);
     return plist;
   } catch (IntrospectionException e) {
            throw new RuntimeException();
   }

}

}

class stringarraypropedit extends propedit {
stringarraypropedit(PropertyDescriptor propi,Object obi) {
     super(propi,obi);
}

String toString() {
   try {
      String[] sval = (String [])prop.getReadMethod().invoke(ob,nullarray) ;
      StringBuffer sb = new StringBuffer();
      int i,j;
      for (j=0;j<sval.length;j++) {
         for (i=0;i<sval[j].length();i++) {
            char c = sval[j].charAt(i);
            if (c == ',')
               sb.append('\\');
            sb.append(c);
         }
         sb.append(',');
      }
      if (sval.length!=0)
         sb.setLength(sb.length()-1);
      return prop.getDisplayName() + " - " + sb.toString();
   } catch( InvocationTargetException e) {
      throw new RuntimeException();
   } catch( IllegalAccessException e) {
      throw new RuntimeException();
   }
}

String stringchange(String newval) throws Exception {
   String retval = toString();
   StringBuffer sb = new StringBuffer();
   Vector sv=new Vector();
   
   int i;
   int scount;
   if (newval.length()==stringStart)
       scount=0;
   else {
      scount=1;
      for (i=stringStart;i<newval.length();i++) {
          char c = newval.charAt(i);
          switch (c) {
              case '\\':
                   i++;
                   c = newval.charAt(i);
                   // intentional fallthrough 
              default :
                sb.append(c);
                break;
              case ',':
                 String s = sb.toString();
                 sv.addElement(s);
System.out.println("adding s" + s);
                 sb.setLength(0);
                 scount++;
          }
      }
      sv.addElement(sb.toString());
   }
System.out.println("str ch stringStart = " + stringStart + " scount =  " + scount + "size = " + sv.size() 
     + " " + newval);
   String [] sarray =  new String[scount];
   sv.copyInto(sarray);
   Object argarray[] = {sarray};
   prop.getWriteMethod().invoke(ob,argarray);
   return retval;
}
}


class stringpropedit extends propedit {

stringpropedit(PropertyDescriptor propi,Object obi) {
     super(propi,obi);
}


String stringchange(String newval) throws Exception {
   String retval = toString();
   Object argarray[] = {newval.substring(stringStart,newval.length())};
   prop.getWriteMethod().invoke(ob,argarray);
   return retval;
}
}

class intpropedit extends propedit {

intpropedit(PropertyDescriptor propi,Object obi) {
     super(propi,obi);
}


String stringchange(String newval) throws Exception {
   String retval = toString();
   Object argarray[] = {new Integer(newval.substring(stringStart,newval.length()))};
   prop.getWriteMethod().invoke(ob,argarray);
   return retval;
}
}

*/
