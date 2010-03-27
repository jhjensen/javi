#JDKPATH=c:\JDK1.3.1
#JDK=jdk1.6.0_18
JDKPATH=/cygdrive/c/progra~1/Java/jdk1.6.0_07
JDKPATH=/usr/lib/jvm/java-6-sun-1.6.0.15
JAVALIB= $(JDKPATH)/lib/jvm.lib
#CFLAGS = -c
CC=g++
#CFLAGS += -mno-cygwin
#LDFLAGS += -mno-cygwin -lwsock32
LINK=ld


all : vi
all1: ID javi.jar remote vi

install : javi.jar
	cp javi.jar vi.pl /usr/share/java
	chmod +x javi.jar vi.pl /usr/share/java/vi.pl /usr/share/java/javi.jar

ID: *.java
	ctags -n -R . ../history plugin
	mkid . ../history plugin

REM= Compute Task rtest rtest_Stub rtestClient
REM_CLASS = $(addsuffix .class,$(REM)) 
REM_JAR =$(addprefix javi/,$(REM_CLASS)) javi/rtestClient$$Example.class

remote.jar: $(REM_CLASS) makefile
	@echo REM_CLASS $(REM_CLASS)
	@echo REM_JAR $(REM_JAR)
	cd ..;$(JDKPATH)/bin/jar -0cf javi/remote.jar $(REM_JAR)

remote: remote.jar
	scp  $< speedy:

%.class : %.java 
	$(JDKPATH)/bin/javac -target 1.6 -source 1.6 $<

%_Stub.class : %.java 
	cd ..;$(JDKPATH)/bin/rmic javi.$*

vi: vi.cpp
	$(CC) $(CFLAGS)  vi.cpp  $(LDFLAGS) -o vi

clean:
	rm -f vi.exe vi.obj

javi.jar: *.class ../MANIFEST.MF 
	cd ..;$(JDKPATH)/bin/jar -0cfm javi/javi.jar MANIFEST.MF javi/*.class history/*.class
