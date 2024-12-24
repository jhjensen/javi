JAVALIB= $(JAVA_HOME)/lib/jvm.lib

all : plugin.jar 

clean:
	rm -f *.class plugin.jar

plugin.jar: *.class 
	$(JAVA_HOME)/bin/jar -0cf plugin.jar *.class
