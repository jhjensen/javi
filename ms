#!/bin/bash
if [ $OS == Windows_NT ] ; then
   listsep=\;
   JDK2=c:\\Progra~1\\Java\\$JDK
   jroot="C:\Docume~1\utftbwx\Desktop\cyghome\javt"
   myprog="$jroot\lib"

   commoncp="$myprog\juniversalchardet-1.0.3.jar;$myprog\rhino1_7R2\js.jar;$myprog\junit3.8.2\junit.jar;$JDK2\lib\tools.jar"

   mycp="$jroot\build;$commoncp"
   exportcp="$jroot\build;$commoncp"


   export CLASSPATH=$exportcp 
   #echo $mycp
   DEBUGFLAGS="-Xdebug -Xrunjdwp:transport=dt_shmem,address=currjdbconn,server=y,suspend=n"

   #jarf=$myprog\\dist\\javi.jar

   BTCLASS="-Xbootclasspath/a:$JDK2\lib\tools.jar;$mycp"
else
   $listsep=\:
   JDK=/usr/lib/jvm/java-6-sun-1.6.0.15
   insprog=/usr/share/java
   myprog=/home/dad/javt
   mycp=$myprog:$insprog/juniversalchardet-1.0.3.jar:$insprog/js.jar:/$insprog/junit3.8.2/junit.jar:$JDK/lib/tools.jar:$insprog/RXTXcomm.jar
   export CLASSPATH="$mycp" 
   #jarf=$myprog/javi/javi.jar
fi
   java $BTCLASS  -Xmx64m javi.Javi todo.txt src javi history javi/plugin awt
