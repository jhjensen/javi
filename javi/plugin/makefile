JDKPATH=/cygdrive/c/Progra~1/Java/jdk1.6.0_18
JAVALIB= $(JDKPATH)/lib/jvm.lib

all : plugin.jar 

clean:
	rm -f *.class plugin.jar

plugin.jar: *.class 
	$(JDKPATH)/bin/jar -0cf plugin.jar *.class
