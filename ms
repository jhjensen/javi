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
   jroot=/Users/jjensen/javt
   myprog=$jroot/lib
   commoncp=$myprog:$myprog/juniversalchardet-1.0.3.jar:$myprog/rhino1_7R3/js.jar:$myprog/junit3.8.2/junit.jar:$JAVA_HOME/lib/tools.jar:$myprog/RXTXcomm.jar
   exportcp="$jroot/build:$commoncp"
   BTCLASS="-Xbootclasspath/a:$JAVA_HOME/lib/tools.jar;$exportcp" 


   export CLASSPATH=$exportcp 
   echo $CLASSPATH
#   jarf=$myprog/javi/javi.jar
#fi
   $JAVA_HOME/bin/java $BTCLASS  -Xmx64m $jarf javi.Javi todo.txt src javi history javi/plugin awt
