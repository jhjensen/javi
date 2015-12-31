#!/bin/bash
#if [ $OS == Windows_NT ] ; then
#   JDK2="c:\\Progra~1\\Java\\$JDK"
#   jroot="C:\Users\dad\Desktop\cyghome\javt"
#   myprog="$jroot\lib"
#
#   commoncp="$myprog\juniversalchardet-1.0.3.jar;$myprog\rhino1_7R3\js.jar;$myprog\junit3.8.2\junit.jar;$JDK2\lib\tools.jar"
#
#   mycp="$jroot\buildworks;$commoncp"
#   mycp="$jroot\build;$commoncp"
#   exportcp="$jroot\build;$commoncp"
#
#
#   export CLASSPATH=$exportcp 
#   echo $CLASSPATH
#   DEBUGFLAGS="-Xdebug -Xrunjdwp:transport=dt_shmem,address=currjdbconn,server=y,suspend=n"
#
#   #jarf="-jar $myprog\dist\javi.jar"
#
#   BTCLASS="-Xbootclasspath/a:$JDK2\lib\tools.jar;$mycp" 
#   echo BTCLASS $BTCLASS
#else
#   JDK=/usr/lib/jvm/java-6-sun-1.6.0.15
#echo $JDK
   BTCLASS="-Xbootclasspath/a:$JDK/lib/tools.jar;$mycp" 
#echo $JDK
#BTCLASS="-bootclasspath $JDK/lib/tools.jar"
   jroot=/Users/jjensen/javt
   insprog=/usr/share/java
   myprog=$jroot/lib
   commoncp=$myprog:$myprog/juniversalchardet-1.0.3.jar:$myprog/rhino1_7R3/js.jar:$myprog/junit3.8.2/junit.jar:$JAVA_HOME/lib/tools.jar:$myprog/RXTXcomm.jar
   mycp="$jroot/buildworks:$commoncp"
   #mycp="$jroot/buildx:$commoncp"
   exportcp="$jroot/build:$commoncp;$JAVA_HOME/lib/tools.jar"


   export CLASSPATH=$exportcp 
   echo $CLASSPATH
#   jarf=$myprog/javi/javi.jar
#fi
   $JAVA_HOME/bin/java $BTCLASS  -Xmx64m $jarf javi.Javi todo.txt src javi history javi/plugin awt
