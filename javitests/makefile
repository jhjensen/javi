#JDKPATH=c:\JDK1.3.1
JDKPATH=/cygdrive/c/progra~1/Java/jdk1.6.0
JAVALIB= $(JDKPATH)/lib/jvm.lib
#CFLAGS = -c
CC=g++
LINK=ld

all : tagsx

tagsx: *.java
	ctags -n -R . ../history ../javi
	mkid . ../history ../javi
