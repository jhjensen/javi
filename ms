#!/bin/bash
#  jroot="$(cd "$(dirname "$0")" && pwd)"
#  myprog=$jroot/lib
#  commoncp=$myprog:$myprog/juniversalchardet-1.0.3.jar:$myprog/rhino-1.7.14.jar:$myprog/junit3.8.2/junit.jar:$JAVA_HOME/lib/tools.jar:$myprog/RXTXcomm.jar
#
#  exportcp="$jroot/build:$commoncp"
#  BTCLASS="-Xbootclasspath/a:$JAVA_HOME/lib/tools.jar;$exportcp"
#
#
#  export CLASSPATH=$exportcp
#  echo $CLASSPATH
#  #jarf=$myprog/javi/javi.jar
#  jarf=build/libs/javi-dev.jar
#JAVA_HOME/bin/java $BTCLASS  -Xmx64m -jar $jarf 
./gradlew installDist && ./build/install/javi/bin/javi todo.txt src main history java javi history plugin awt

