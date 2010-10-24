#JDK=jdk1.6.0_18
JDKPATH=/usr/lib/jvm/java-6-sun-1.6.0.15
JDKPATH=
JDKBIN=$(JDKPATH)/bin/
JDKBIN=
JAVALIB= $(JDKPATH)/lib/jvm.lib
#CFLAGS = -c
CC=g++
#CFLAGS += -mno-cygwin
#LDFLAGS += -mno-cygwin -lwsock32
LINK=ld


all: ID javi.jar

install : javi.jar
	cp javi.jar vi.pl /usr/share/java
	chmod +x javi.jar vi.pl /usr/share/java/vi.pl /usr/share/java/javi.jar

FORCE:

ID: FORCE
	ctags -n -R src
	mkid src

REM= Compute Task rtest rtest_Stub rtestClient
REM_CLASS = $(addsuffix .class,$(REM)) 
REM_JAR =$(addprefix javi/,$(REM_CLASS)) javi/rtestClient$$Example.class

remote.jar: $(REM_CLASS) makefile
	@echo REM_CLASS $(REM_CLASS)
	@echo REM_JAR $(REM_JAR)
	cd ..;$(JDKPATH)/bin/jar -0cf javi/remote.jar $(REM_JAR)

%.class : %.java 
	$(JDKPATH)/bin/javac -target 1.6 -source 1.6 $<

%_Stub.class : %.java 
	cd ..;$(JDKPATH)/bin/rmic javi.$*

clean:
	rm -f *.class ID javi.jar

javi.jar: *.class ../MANIFEST.MF 
	cd ..;$(JDKBIN)jar -cfm javi/javi.jar MANIFEST.MF javi/*.class history/*.class
