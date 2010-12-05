#!/usr/bin/perl

use strict;
use integer;
use ops;
use warnings;

my @elist;
my @sorted;

while (<>) {
   #print;
   if (/(.*),([a-zA-Z0-9.]+),Line ([0-9]+).*/) {
     push @elist, [$2,$3,$1];
   }
}

#Ithere are 1 blank lines between the members.,Low Severity,AtView.java,Line 32

@sorted = sort { 
   if (@$a[0] lt @$b[0]) { return 1}
   elsif (@$a[0] gt @$b[0]) { return -1}
   else {return (@$b[1] <=> @$a[1]) }} (@elist);

foreach my $line (@sorted) {
   #print "@$line[0]:@$line[1]\n" ;
   print "@$line[0]:@$line[1] @$line[2]\n" ;
}
