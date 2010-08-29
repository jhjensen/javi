#!/usr/bin/perl
# takes
# must be run from the build root, ie e:/build/embedded
# puts a log file into the directory where the make file is run from.
# prints only error messeges to stdout. in the format
# filename(line#): error messege.
# if filename is not known points to line in log file.
# prints done at the end.
#system("rm d:\\plog");

use strict;
use integer;
use ops;
#use warnings;

sub getline() {
    my $line = (<MAKE>);
    if ($line) {
          print TEEOUT $line;
          print TEEOUT "LINE:::$line";  # for debugging
    }
    return $line;
}

sub mylog {
#    open(OUT, ">>d:\\plog");
#    print OUT @_;
#    close OUT;
}  

my $nowarningFlag=0;
my $warningcount;
my %stor;

#targets are the same as bldemb targets.

my $leakx=0;
my $bld_type="PRODUCT";
$bld_type="DEBUG";
my $logfile;
my $gnuVer=
#      "gnu3.79.1";
#      "gnunew";
      "3.81";

sub parsemake  {
   #  force line buffering        
#   select STDERR;
#   $| = 1;
   my $old_fh = select(MAKE);
   $| = 1;
   select($old_fh);
#   select STDOUT; # rsh works better with STDERROR
   $| = 1;
   while($_= getline()) {
      tr /\\/\//;
      if  (/(^(\w:)?[ \w.\/\\]+)\(([0-9]+)\) : (.*)$/) { #  cl.exe 
         if (!/warning C4200: nonstandard extension used : zero-sized array in struct/) {
            mkerror($1,$3,0,$4);
         }

#C:\Docume~1\utftbwx\Desktop\cyghome\javt\src\javi\UI.java:1916: ';' expected
#         FontList.setDefaultFontSize(int width, int height) {
#                                              ^
      } elsif (/(^(\w:)?[~\w.\/\\]+):([0-9]+):(.*)/) { # gcc and others
         mkerror($1,$3,0,$4);
      } elsif (/: error LNK/) {   # recognizes MS link errors
         mkerror($logfile,$.,0,$_);
      } elsif (/^LINK : /) {   # recognizes MS CE link errors
         mkerror($logfile,$.,0,$_);
      } elsif (/^cc: unrecognized option/) {   # 
         mkerror($logfile,$.,0,$_);
      } elsif (/^cc: .*No such file or directory/) {   # 
         mkerror($logfile,$.,0,$_);
      } elsif (/^\*\*\* Signal/) {   # 
         mkerror($logfile,$.,0,$_);
      } elsif (/^TODO \S+ (\S+):([0-9]+)/) {   # 
         mkerror($1,$2,0,$_);
      } elsif (/^Traceback \(most recent call last\):/) {   # 
         mkerror($logfile,$.,0,$_);
      } elsif (/^\s*File "([a-zA-z.\/\\:]+)", line ([0-9]+)(, in .*)?$/) {   # 
          my $l1;
          my $file = $1;
          my $lineno = $2;
          while(1) {
             $l1 = getline() || die "unexpected EOF";
             chop $l1;
             $l1 = getline() || die "unexpected EOF";
             last if $l1 !~ /^\s*File "([a-zA-z.\/\\:]+)", line ([0-9]+)(, in .*)?$/;
             $file = $1;
             $lineno = $2;
             chop $l1;
          }
          my $charno = index($l1,"^");
          if ($charno ==-1) {
             mkerror ($file,$lineno,0," $l1");
          } else {

              my $l2 = getline() || die "unexpected EOF";
              chop $l2;
              mkerror ($file,$lineno,$charno,"asdf $l2");
          }
#  File "testscript", line 13, in doit
#    client = pcom.newclient("1.1.1.1","2.2.2.2")
#AttributeError: 'module' object has no attribute 'newclient'
#*** Error code 1

#  File "testscript", line 6, in trace
#    print "%s %s %s:%d"  % (msg,frm.function(),frm.file(),frm.lineno());
#NameError: global name 'msg' is not defined

#  File "testscript", line 6
#    print (%s %s %s:%d)  % (msg,frm.function(),frm.file(),frm.lineno());
#           ^
#SyntaxError: invalid syntax
#*** Error code 1
      } elsif (/^"((\w:)?[ \w.\/\\]+)", line ([0-9]+):(.*)/) {   # 
            mkerror($1,$3,0,$4);
      } elsif (/^.==. was unexpected at this time/) {   # recognizes MS CE link errors
         mkerror($logfile,$.,0,$_);
      } elsif (/is not recognized as an internal or external command/) {   # recognizes MS CE link errors
         mkerror($logfile,$.,0,$_);
      } elsif  (/(^(\w:)?[ \w.\/\\]+)\(([\w.+]+)\): (.*)$/) { # recongizes neutrino link errors
         mkerror($logfile,$.,0,$_);
      } elsif (/^BLDVOCAB_ERR/) {  # recognize BLDVOCAB error
         mkerror($logfile,$.,0,$_);
      } elsif (/^BLDROM_ERR/) {  # recognize BLDROM error
         mkerror($logfile,$.,0,$_);
      } elsif (/^BLDWORDS_ERR/) {  # recognize BLDWORDS error
         mkerror($logfile,$.,0,$_);
      } elsif (/^ERROR: rc =/) {  # recognize BLDWORDS error
         mkerror($logfile,$.,0,$_);
      } elsif (/^sed:/) {  # recognize BLDWORDS error
         mkerror($logfile,$.,0,$_);
      } elsif (/^Error creating vocabulary set/) {  # recognize BLDWORDS error
         mkerror($logfile,$.,0,$_);
      } elsif (/^Error:.*failed/) {  # recognize speakapp error
      mkerror($logfile,$.,0,$_);
      } elsif (/^SpeakApp:.*ERROR/) {  # recognize speakapp error
         mkerror($logfile,$.,0,$_);
      } elsif (/^ldmips(.exe)?: /) {  # recognize mips linker errors.
         mkerror($logfile,$.,0,$_);
      } elsif (/^[Mm][Aa][Kk][Ee](\.exe)?: \*\*\* /) {  # recognize Make errors
         mkerror($logfile,$.,0,$_);
      } elsif (/^make: .*Stop$/) {  # recognize bsd make errors
         mkerror($logfile,$.,0,$_);
      } elsif (/^\*\*\* Error code/) {  # recognize bsd make errors
         mkerror($logfile,$.,0,$_);
      } elsif (/^[Mm][Aa][Kk][Ee].*Error/) {  # recognize Make errors
         if (!/\(ignored\)/) {
            mkerror($logfile,$.,0,$_);
         }
      } elsif (/[Mm][Aa][Kk][Ee](.exe)*(\[[0-9+]\])*: \*\*\* /) {  # recognize Make errors
         if (!/\(ignored\)/) {
            mkerror($logfile,$.,0,$_);
         }
      } elsif (/^[Mm][Aa][Kk][Ee] \(e=.\): /) {  # recognize Make errors
         if (!/\(ignored\)/) {
             mkerror($logfile,$.,0,$_);
         }
      } elsif (/^Can't open /) {  # recognize Make errors
         if (!/\(ignored\)/) {
             mkerror($logfile,$.,0,$_);
         }
      } elsif (/^Access is denied/)  {  # recognize Make errors
          mkerror($logfile,$.,0,$_);
   } elsif (/^---- ERROR :/)  {  # recognize Make errors
       mkerror($logfile,$.,0,$_);
      } elsif (/^Access denied/)  {  # recognize Make errors
          mkerror($logfile,$.,0,$_);
      } elsif (/^cp(\.exe)?: cannot create/) {  # recognize copy errors
         if (!/\(ignored\)/) {
             mkerror($logfile,$.,0,$_);
         }
      } elsif (/^cp.*:  No such file or directory$/) {  # recognize copy errors
         if (!/\(ignored\)/) {
             mkerror($logfile,$.,0,$_);
         }
#      } elsif (/^cp(\.exe)?: cannot stat /) {  # recognize copy errors
#         if (!/\(ignored\)/) {
#             mkerror($logfile,$.,0,$_);
#         }
      } elsif (/^NMAKE : fatal error/) {
                mkerror($logfile,$.,0,$_);
      } elsif (/ \*\*\* extraneous `endif'.  Stop/) {
                mkerror($logfile,$.,0,$_);
      } elsif (/ \: \*\*\* /) {    # TTS stuff
             mkerror($logfile,$.,0,$_);
      } elsif (/^FAILED: copy/) {    # sea.pl error message
             mkerror("",$.,0,$_);
      } elsif (/at ([a-z]+\.pl) line ([0-9]+)\./) {    # perl syntax error message
             mkerror($1,$2,0,$_);
      } elsif (/at ([a-z]+\.pl) line ([0-9]+), near \"(.*)\"/) {    # perl syntax error message
             mkerror($1,$2,0,$_);


     } elsif (/^\#\#\# mwldeppc(\.exe)? Driver Warning/ ) { 
          my $l1 = getline() || die "unexpected EOF";
          chop $l1;
          $l1 = substr($l1,4,1000);
          my $l2 = getline() || die "unexpected EOF";
          $l2 = substr($l2,4,1000);
          chop $l2;
          mkerror ($logfile,$.,0,"$l1 $l2");
   
      } elsif (/is not recognized as an internal or external command/) {
             mkerror($logfile,$.,$_);
      } elsif (/System error [0-9]+ has occurred/) {
             mkerror($logfile,$.,0,$_);
   
      } elsif (/^\#\#\# mwldeppc(\.exe)? Linker ((Error)|(Warning))/) {
             my $l1 = getline() || die "unexpected EOF";
          chop $l1;
          $l1 = substr($l1,4);
          my $l2 = getline() || die "unexpected EOF";
          chop $l2;
             mkerror ($logfile,$.,0,"$l1 $l2");
   
      } elsif (/^\#\#\# mwldeppc(\.exe)? Usage Error/) { 
             my $l1 = getline() || die "unexpected EOF";
             mkerror ($logfile,$.,0,"$l1");

      } elsif (/^\#\#\# mwcceppc(\.exe)? Driver Error:/) {
          my $l1 = getline() || die "unexpected EOF";
          chop $l1;
          $l1 = substr($l1,4,1000);
          my $l2 = getline() || die "unexpected EOF";
          $l2 = substr($l2,4,1000);
          chop $l2;
          my $l3 = getline() || die "unexpected EOF";
          $l3 = substr($l3,4,1000);
          chop $l3;
          mkerror ($logfile,$.,0,"$l1 $l2 $l3 ");
      } elsif (/^### mwcceppc(\.exe)? Usage Warning:/) {
          my $err = getline() || die "unexpected EOF";
          chop $err;
          mkerror($logfile,$.,0,"$err ");
      } elsif (/^### mwldeppc(\.exe)? Usage Error:/) {
         my $err = getline() || die "unexpected EOF";
         my $err2= getline() || die "unexpected EOF";
         #print LOG $err2;  # for debugging
         chop $err;
         chop $err2;
         mkerror($logfile,$.,0,"$err $err2");
      } elsif (/^\#\#\# mwcceppc(\.exe)? Compiler Error/) { 
             my $err = getline() || die "unexpected EOF";
          chop $err;
             mkerror($logfile,$.,0,$err);
      } elsif (/^\#\#\# mwcceppc(\.exe)? Compiler Note/) {
          my $mess = getline();
          chop $mess;
          mkerror($logfile,$.,0,$mess);
      } elsif (/^\#\#\# mwcceppc(\.exe)? Compiler/) {
          my $msg = getline();
          our $fname;
          $_=$msg;
          if (/\#\s+((File)|(In)).*:\s(.*)$/) {
             $fname = $4;
             $_ = getline() || die "unexpected EOF";
             if (/\#    From:/) {
                   $_ = getline() || die "unexpected EOF";
             }
             $_  = getline() || die "unexpected EOF";
          }

          chop;
          /([0-9]+)/;
          my $lnum = $1;
          my $chnum = getline() || die "unexpected EOF";
          $chnum = index ($chnum,'^') - 12;
          $msg = getline() || die "unexpected EOF";
          $msg = substr($msg,1,1000);
          $lnum = "$chnum,$lnum";
          chop $msg;
          $_ = $msg;
          if (!/pad byte\(s\) inserted after data member/) {
             mkerror ($fname,$lnum,0,$msg);
          }
      }
      if ($warningcount) {
         mkerror($logfile,$.,0,"$warningcount warnings not shown");
      }
   }
}

my $cdir = $ARGV[0];
my $mkfile ="";
# find the closest directory with a makefile in it
#print "1x cdir = $cdir\n";
$_=$cdir;
$cdir =~ s/((\/|^|\\)([\w.]+))$//;  # chop off filename
$mkfile = $3;
my $mkfilet = $3;
print "xxmakefile:$mkfile\n";
if (!($mkfilet =~ (/(\.mak$)|(\.mk$)|(makefile)/i))) {
   xxx: while ($cdir) {
#print "4x cdir = $cdir\n";
      if (!$cdir) {
           last;
      }
      opendir(  DIR,$cdir) || die ("unable to open dir $cdir");
      while ($_ = readdir(DIR)) {
         #print "$_\n";
         if (/((\.mak$)|(\.mk$)|(makefile))/i) {
           $mkfile = $_;
           last xxx;
         }
      }
      $cdir =~ s/(\/|^|\\)[\w.]+$//;  # chop off filename
   }
}

use Cwd;
my $rootdir = lc(cwd);
if (!$mkfile ) { # we found a makefile
   die("make.pl:shouldn't come here");
} else {
   print "makefile:$mkfile\n";
   if ($cdir ne "") { # we found a makefile
      chdir $cdir || die "couldn't change to $cdir";
   }
   my @extras = ("");
  #      @extras = (" VER=1 "," VER=2" ," VER=3 " ," VER=4 " ," VER=5 " ," VER=6 "  ," VER=7" ," VER=8" ," VER=9" ," VER=100x " ," VER=voc " ," VER=160x " ," VER=006xx " ," VER=190x " ," VER=10 " ," VER=5000 " ," VER=6000 " ," VER=7000 " ," VER=8000 " ," VER=9000" ," VER=9400 ");
 
$|=1; # make unbuffered

my %origenv = %ENV; 
my @targets="build";
print "%origenv\n";
   foreach my $target (@targets) {
      $logfile ="$rootdir/$target.txt";
      unlink $logfile;
      open TEEOUT,">$logfile" || die("failed to open $logfile");
      %ENV=%origenv;
      mysetenv($target);
      foreach my $extra (@extras) {
         #my $mycommand = "smake -f $mkfile $extra all install 2>&1|";
         #my $mycommand = "make $extra all 2>&1|";
         my $mycommand = "ant -e $extra 2>&1|";
         #my $mycommand = "python2.3 setup.py build 2>&1|";
         print TEEOUT "results of  $mycommand for $target\n";
         print TEEOUT "cdir:$cdir \n";
         open(MAKE, $mycommand) || die ("$mycommand failed $!");
         parsemake();
      } 
   }
} 

exit(0);  # redundant exit

sub mkerror {
    my($filename,$linenumber,$charnumber,$messege) = @_;
    #$print "mkerr filename = $filename\n";
    $_=$filename;
    #my @cal = caller();
    #print  "xxxxx @cal\n";
    if ($nowarningFlag && /warning/ ) {
        $warningcount++;
        return;
    }
    $filename=lc($filename);
    $filename =~ s/^ *//;
#    $filename =~ s/^\.\.[\\\/]//g; # get rid of starting ../ 
    $_ = $filename;
    if (!/^((\w:)|[\\\/])/) { # matches absolute path
       if ($cdir ne "") {
          $filename = "$cdir/$filename";
       } else {
          $filename = findfile($filename);
          if ($filename eq "") {
             $filename=$logfile;
             $linenumber=$.;
          }
       }
    } else {
       print("CWD $rootdir \n");
       if (index($filename,$rootdir)==0) {
          $filename=substr($filename,1+length($rootdir));
       }
          
    }
    print "$filename($charnumber,$linenumber)-$messege\n";
    mylog ("$filename($linenumber)-$messege\n");
}

# given a partial path look for a file in the source tree
# this will potentially give the wrong filename if the partial path
# is ambiguous. This will return the first matching file it finds, which may be the wrong one.
# It also only looks in src, so source files in the object tree will get an unkown file.
# this could be improved by using the tgt variable to chose between duplicate files.

sub findfile { 
    my($filename) = @_;

    if (-e $filename) {
       return $filename;
    }

    if (keys(%stor)==0) {
        require "find.pl";
        &find('src');
    }

    $_=$filename;
    s/(\/|^|\\)([\w.]+$)//;  # get filename to $2

    if (exists $stor{$2}) {
       my @files= @{$stor{$2}};

       while ($_ = shift(@files)) {
           my $ind =  rindex $_,$filename;
           if (!(length($_) -length($filename)-$ind)) {
            return $_;
           }
        }
    }
        
  return "";
}

# builds an array keyed off of the pathless file name.  each element is a list
# of full filenames that match the pathless file
sub wanted {
   my $n=lc($File::Find::name);
   $n =~ s'(/|^|\\)([\w\-\$~.]+$)'';  # chop off filename
   die ("n = $n\nxxx $File::Find::name\n") unless  ($2);
   if (!(exists $stor{$2})) {
     $stor{$2} = [];
   }
   push @{$stor{$2}},$File::Find::name;
}


sub mysetenv($) {
   my($tgt) = @_;
   print "mysetenv $tgt\n";
   my $pathadd="";
   if ($tgt eq "build") {
      #$pathadd =  "$pathadd;";
   } else  {
        die "bad target $tgt"
   }
#   system("c:\\cygwin\\bin\\printenv.exe >$rootdir/oenv");
   $ENV{'PATH'}= "$pathadd:$ENV{'PATH'}";
#???   print  "\n\n$ENV{'PATH'}\n\n$ENV{'Path'}\n\n";

   system("printenv | sort >$rootdir/penv") && die;
}
