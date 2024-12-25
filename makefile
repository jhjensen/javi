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


jarf=build/libs/javi-all.jar

CLASSPATH=/Users/jjensen/javi/lib:/Users/jjensen/javi/build:/Users/jjensen/javi/lib:/Users/jjensen/javi/lib/juniversalchardet-1.0.3.jar:/Users/jjensen/javi/lib/rhino-1.7.14.jar:/Users/jjensen/javi/lib/junit3.8.2/junit.jar:/Library/Java/JavaVirtualMachines/jdk-23-macports.jdk/Contents/Home/lib/tools.jar:/Users/jjensen/javi/lib/RXTXcomm.jar
test: build 
	java -cp $(CLASSPATH) javi.EditTester1
	#java javi.ClangFormat

automake: test # runner

runner: build
	java javi.Javi

build: dist/javi.jar
FORCE: 

dist/javi.jar: FORCE
	ant -e

install : javi.jar
	cp javi.jar vi.pl /usr/share/java
	chmod +x javi.jar vi.pl /usr/share/java/vi.pl /usr/share/java/javi.jar

FORCE:

ID: FORCE
	ctags -n -R src
	mkid -m ~/cyghome/id-lang.map src

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
