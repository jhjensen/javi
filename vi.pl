#!/usr/bin/perl -w
# vi.pl — Open files in a running Javi instance via its Server plugin socket.
#
# Usage: vi.pl [options] file1 [file2 ...]
#
# Protocol:
#   1. Connect to Javi's Server plugin on localhost:6001 (TCP).
#   2. Send SOH (\001) as a handshake byte.
#   3. Send each filename (UTF-8 encoded, absolute path) followed by newline.
#   4. Wait for an acknowledgment line from the server.
#   5. Close the socket.
#
# If the connection fails (no running Javi instance), start a new Javi
# instance with the given files using the installed launcher script.
#
# The -p flag and its argument are skipped (legacy positioning option).

use strict;
use Socket;
use Encode qw(encode);
use File::Spec;

my $remote = 'localhost';
my $port   = 6001;

# Resolve hostname and build socket address
my $iaddr = inet_aton($remote) || die "no host: $remote";
my $paddr = sockaddr_in($port, $iaddr);
my $proto = getprotobyname('tcp');

socket(SOCK, PF_INET, SOCK_STREAM, $proto) || die "socket: $!";

if (connect(SOCK, $paddr)) {
   # Connected to a running Javi instance — send filenames via socket

   # Send SOH handshake byte
   print SOCK "\001";

   # Disable output buffering on the socket
   my $ofh = select SOCK;
   $| = 1;
   select $ofh;

   # Send each file argument as an absolute UTF-8 path
   while (my $filename = shift(@ARGV)) {
      # Skip the legacy -p (position) flag and its argument
      if ($filename eq "-p") {
         shift(@ARGV);
         next;
      }

      # Convert to absolute path
      my $abspath;
      if (File::Spec->file_name_is_absolute($filename)) {
         $abspath = $filename;
      } else {
         $abspath = File::Spec->rel2abs($filename);
      }

      my $encoded = encode("utf8", $abspath);
      print SOCK "$encoded\n";
      print SOCK "\n";
   }

   # Wait for server acknowledgment
   my $line = <SOCK>;
   close(SOCK) || die "close: $!";
} else {
   # No running Javi instance — start a new one with the installed launcher.
   # The launcher is expected at ~/.local/bin/javi (installed via 'make install').
   # Falls back to 'javi' on PATH if the local install doesn't exist.

   my $javi_bin = "$ENV{HOME}/.local/bin/javi";
   if (! -x $javi_bin) {
      # Try PATH
      $javi_bin = "javi";
   }

   exec($javi_bin, @ARGV);
   die "exec failed: $!";
}
