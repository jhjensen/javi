echo off
title curr javt
set JDK=c:\j2sdk1.4.2_10
#set CLASSPATH=d:\download\javt.jar
#set CLASSPATH=c:\javt
#$set CLASSPATH=C:\Docume~1\jensen\MyDocu~1\myprog~1;$JDK\lib\tools.jar;$JDK\jre\lib\ext\RXTXcomm.jar
set DEBUGFLAGS= -Xdebug -Xrunjdwp:transport=dt_shmem,address=currjdbconn,server=y,suspend=n 
set path=%path%;c:\cygwin\usr\local\bin


set BTCLASS= -Xbootclasspath/a:%JDK%\lib\tools.jar
%JDK%\bin\java %BTCLASS%  -classpath=../javi1/javi.jar -mx64m javi.Javi todo.txt ..\history
