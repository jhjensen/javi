#include <stdio.h>
#include <stdlib.h>
#include <string>
#include <new>
#ifdef WIN32
   //#include <winsock.h>
   //#include <direct.h>
   //#include <process.h>
   #define lasterr WSAGetLastError()

#else
   #include "sys/socket.h"
   #include "errno.h"
   #include "unistd.h"
   #include "netinet/in.h"
   #include "string.h"
   #define lasterr errno
   typedef int SOCKET;
   
#endif

void myexit(int i) {
  printf("exiting %d %d\n",i,lasterr); 
  exit(i);
}

void myconnect(int argc,char **argv) {

#ifdef WIN32
   { // init
      WORD wVersionRequested;
      WSADATA wsaData;
      int err;
       
      wVersionRequested = MAKEWORD( 2, 2 );
       
      err = WSAStartup( wVersionRequested, &wsaData );
      if ( err != 0 ) {
          // Tell the user that we could not find a usable 
          // WinSock DLL.                            
          return;
      }
    
      // Confirm that the WinSock DLL supports 2.2.
      // Note that if the DLL supports versions greater 
      // than 2.2 in addition to 2.2, it will still return 
      // 2.2 in wVersion since that is the version we 
      // requested.           
    
      if ( LOBYTE( wsaData.wVersion ) != 2 ||
              HIBYTE( wsaData.wVersion ) != 2 ) {
          // Tell the user that we could not find a usable 
          // WinSock DLL.                                  
          WSACleanup( );
          return; 
      }
    }
#endif
    {
       SOCKET sock = socket(PF_INET,SOCK_STREAM,0);
       struct sockaddr_in addr = {AF_INET,htons(6001)};
       char buf[] = {0,0,1};
       #ifdef WIN32
          addr.sin_addr.S_un.S_addr=htonl(INADDR_LOOPBACK);
       #else
          addr.sin_addr.s_addr=htonl(INADDR_LOOPBACK);
       #endif
       if (sock == 0)
          myexit(2);
       if (connect(sock,(struct sockaddr *)&addr,sizeof (addr)))
          return;
       if (send(sock,buf,sizeof(buf),0)!= sizeof(buf))
          myexit(4);
       unsigned short argcr = htons((short)(argc-1));
       send(sock,(char *)&argcr,2,0);
       for (int i=1;i<argc;i++) {
       #ifdef WIN32
          char pbuf[1000];
          _fullpath(pbuf,argv[i],sizeof pbuf);
       #else
          #ifdef PATH_MAX
             int path_max = PATH_MAX;
          #else
             int path_max = pathconf (argv[i], _PC_PATH_MAX);
             if (path_max <= 0)
               path_max = 4096;
          #endif
          char *pbuf = new char [path_max];
          realpath(argv[i],pbuf);
       #endif

         unsigned short slen = htons((short)strlen(pbuf));
         send(sock,(char *)&slen,2,0);
         send(sock,pbuf,strlen(pbuf),0);
         delete [] pbuf;
       }
       recv(sock,buf,1,0);
       myexit(5);
    }
}


main(int argc,char **argv) {
    myconnect(argc,argv);
    const char * prog = "java";
    const char **argv2 = new const char *[argc+3];
    argv2[0]=prog;
    argv2[1]="javt.javt";
    for (int i=1;i<argc;i++) 
       argv2[i+1] = argv[i];
    argv2[argc+1]=0;
    execvp(prog,(char * const *)argv2);
    perror("execv error");
    return 1;
}
#if 0
    addlibpath();
    
        JDK1_1InitArgs vm_args = {
   
           JNI_VERSION_1_4, //jint version

           0, //char **properties;
           0,//jint checkSource;
           1000000,//jint nativeStackSize;
           1000000,//jint javaStackSize;
           8000000,//jint minHeapSize;
           30000000,//jint maxHeapSize;
           2,//jint verifyMode;
           USER_CLASSPATH , //char *classpath;
       
           &vfp,//jint (JNICALL *vfprintf)(FILE *fp, const char *format, va_list args);
           &myexit,//void (JNICALL *exit)(jint code);
           myabort,//void (JNICALL *abort)(void);

           0,//jint enableClassGC;
           0,//jint enableVerboseGC;
           0,//jint disableAsyncGC;
           0,//jint verbose;
           0,//jboolean debugging;
           0,//jint debugPort;
        };
//        /* Get the default initialization arguments and set the class 
//         * path */
//        JNI_GetDefaultJavaVMInitArgs(&vm_args);
//   vm_args.version = 0x00010001;
   JNI_GetDefaultJavaVMInitArgs(&vm_args);
   vm_args.properties=properties;

   int len = strlen(USER_CLASSPATH) + strlen(vm_args.classpath) + 1;
   char * newcp = (char*) malloc(len * sizeof(char));
   strcpy(newcp, USER_CLASSPATH);
   strcat(newcp, vm_args.classpath);
   vm_args.classpath = newcp;

    
//int x = *(int *)0;
        /* load and initialize a Java VM, return a JNI interface 
         * pointer in env */
        JavaVM *jvm;       /* denotes a Java VM */
        JNIEnv *env;       /* pointer to native method interface */
        jint res = JNI_CreateJavaVM(&jvm, (void **)&env, &vm_args);
        if (res < 0) {
           fprintf(stderr, "Can't create Java VM %d\n",res);
           exit(res);
        }
    
        /* invoke the Main.test method using the JNI */
        jclass cls = env->FindClass("javt/javt");
        if (cls == 0) {
            fprintf(stderr, "Can't find javt/javt class\n");
            exit(1);
        }
        jmethodID mid = env->GetStaticMethodID(cls, "main", "(I)V");
        if (mid == 0) {
            fprintf(stderr, "Can't find Prog.main\n");
            exit(1);
        }

    jobjectArray args = env->NewObjectArray(argc-1, 
            env->FindClass("java/lang/String"), NULL);
    if (args == 0) {
        fprintf(stderr, "Out of memory\n");
        exit(1);
    }
    for (int i=1;i<argc;i++) {
       jstring jstr = env->NewStringUTF(argv[i]);
       if (jstr == 0) {
           fprintf(stderr, "Out of memory\n");
           exit(1);
       }

       env->SetObjectArrayElement(args, i-1,jstr);
    }
        env->CallStaticVoidMethod(cls, mid, args);
    
        /* We are done. */
        jvm->DestroyJavaVM();
  return(0);
}

void addlibpath() {
    #define JDK "D:\\j2sdk1.4.1"
    const char jre[] = "PATH=" JDK "/bin;" JDK "\\jre\\bin;" ;
    char *oldpath = getenv("PATH");
printf("oldpath = %s\n",oldpath);
    if (!oldpath)
       oldpath="";
    char *newpath = new char[strlen(oldpath)  + 1 + sizeof(jre) ];
    *newpath=0;
    strcat(newpath,jre);
    strcat(newpath,oldpath);

    putenv(newpath);
    delete newpath;
#include "jni.h"
//#define USER_CLASSPATH "e:\\" /* where Prog.class is */
//#define USER_CLASSPATH "e:/" /* where Prog.class is */
#define USER_CLASSPATH "e:\\javt\\javt.jar" /* where Prog.class is */

//void myconnect(int argc,char **argv);
jint JNICALL vfp(FILE *fp, const char *format, va_list args) {
   return vfprintf(fp,format,args);
}
void JNICALL myexit(jint code) {
  exit(code);
}
void JNICALL myabort(void) { 
  exit(1);
}
char* properties[] = {
	NULL
	};

void addlibpath();
main(int argc,char **argv) {
printf("1\n");
    //JavaVMOption options[4];

//    options[1].optionString = "-Djava.class.path=e:\"; // user classes 

   char *prop[] = {
0,0
};
}
#endif
