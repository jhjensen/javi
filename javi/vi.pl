#!/usr/bin/perl -w
use strict;
use Socket;
use Encode qw(encode decode);
#use File::PathConvert qw(realpath);

   my ($remote,$port, $iaddr, $paddr, $proto, $line);

   #$remote  = shift || 'localhost';
   $remote  = 'localhost';
#   $remote  = 'dr1';
   #$port    = shift || 2345;  # random port
   $port    = 6001;
   #if ($port =~ /\D/) { $port = getservbyname($port, 'tcp') }
   #die "No port" unless $port;
   
   $iaddr   = inet_aton($remote)               || die "no host: $remote";

   $paddr   = sockaddr_in($port, $iaddr);

   $proto   = getprotobyname('tcp');
   socket(SOCK, PF_INET, SOCK_STREAM, $proto)  || die "socket: $!";
   if (connect(SOCK, $paddr) ) {

      print SOCK "\001";
     { 
          my $ofh = select SOCK;
	  $| = 1;
	  select $ofh;
     }
     
      while (my $filename = shift(@ARGV)) {
         my $tf= $filename;
         if ($tf eq "-p")  {
            shift(@ARGV);
            next;
         }
         my $rp;
         if ( $^O eq "Windows" ) {
            if  ($tf =~ /.*\\.*/) {
            $rp = encode("utf8",$filename);
            print "filea :$rp:\n";
            } else {
               $rp =  `cygpath -w -a -C UTF8 $filename`;
               chop $rp;
            }
         } else {
            $rp = encode("utf8",$filename);
         }
         print "filex :$rp:\n";
         print SOCK "$rp\n";
      }
      shutdown(SOCK,1);
      $line = <SOCK>;
      close (SOCK)  || die "exiting 5 $!";
      printf("exiting %d %d\n",5,0); 
   } else {
      if ( $^O eq "Windows" ) {

print("3\n");
         my $JDK="jdk1.6.0_19";
         my $JDK1="/cygdrive/c/Progra~1/Java/$JDK";
         my $JDK2="c:\\Progra~1\\Java\\$JDK";
         my $myprog="C:\\Users\\dad\\Desktop\\cyghome\\javt";
         my $mycp="$myprog;$myprog\\juniversalchardet-1.0.3.jar;$myprog\\rhino1_7R2\\js.jar;$myprog\\junit3.8.2\\junit.jar;$JDK2\\lib\\tools.jar";

         $ENV{'CLASSPATH'}=$mycp;
         my $DEBUGFLAGS="-Xdebug -Xrunjdwp:transport=dt_shmem,address=currjdbconn,server=y,suspend=n";

         $ENV{"PATH"}="$JDK1/bin:/usr/local/bin:$ENV{'PATH'}";

         #jarf=$myprog\\javi\\javi.jar
         my $jarf="";

         my $BTCLASS="-Xbootclasspath/a:$JDK2\\lib\\tools.jar";
         print "argv @ARGV";
#         my $paths = join(@ARGV);
         my $paths = $ARGV[0];
         if  ($paths =~ /.*\\.*/) {
            $paths = $ARGV[0];
         } else {
            $paths = `cygpath -w -a -C UTF8 @ARGV`;
            print "paths $paths\n";
            print $paths;
            chop $paths;
         }
         my @ex = ("$JDK1/bin/java","-cp","$jarf;$mycp",
              $BTCLASS ,"-Xmx64m","javi.Javi",$paths);
         print @ex;
         exec(@ex);
      } else  {
print("4 :@ARGV:\n");
         my $JDK="/usr/lib/jvm/java-6-sun-1.6.0.15";
         my $myprog="/home/dad/javt";
         my $insprog="/usr/share/java";
         my $mycp="$myprog:$insprog/juniversalchardet-1.0.3.jar:$insprog/js.jar:$insprog/junit3.8.2/junit.jar:$JDK/lib/tools.jar:/$insprog/RXTXcomm.jar";
         $ENV{"CLASSPATH"}=$mycp ;
         #my jarf="$myprog/javi/javi.jar";
         my $jarf="";
         my $BTCLASS="-Xbootclasspath/a:$JDK/lib/tools.jar";
         system("java  >~/.javiout 2>&1 -cp $jarf:$mycp $BTCLASS  -Xmx64m javi.Javi \"@ARGV\"");
      }
   }
