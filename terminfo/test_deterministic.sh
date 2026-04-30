#!/bin/bash
# test_deterministic.sh — Non-interactive terminal test
#
# Produces deterministic escape sequences and their expected visual
# output on stdout.  Capture the output and compare between terminals.
# Does NOT use cursor position queries (DSR 6n), so works over
# non-interactive SSH.
#
# Usage:
#   TERM=xterm-256color bash test_deterministic.sh > ref_output.txt
#   # Then run same in javi and compare outputs
#
# The output includes both raw terminal screen state and labeled
# sections for easy visual diffing.

ESC=$'\033'
CSI="${ESC}["

# Use a small fixed screen (20 rows x 40 cols) for predictability
ROWS=20
COLS=40

##############################################################################
echo "=== Test 1: CUP absolute positioning ==="
##############################################################################
printf "${CSI}2J${CSI}H"  # clear & home
printf "${CSI}1;1HA"
printf "${CSI}1;10HB"
printf "${CSI}5;20HC"
printf "${CSI}10;1HD"
printf "${CSI}10;40HE"
# Expected: A at (1,1), B at (1,10), C at (5,20), D at (10,1), E at (10,40)

##############################################################################
echo ""
echo "=== Test 2: SGR Colors ==="
##############################################################################
printf "${CSI}2J${CSI}H"
# Standard ANSI colors
for i in $(seq 30 37); do
   printf "${CSI}${i}m*"
done
printf "${CSI}0m"
echo " <- standard fg"

# Bright colors
for i in $(seq 90 97); do
   printf "${CSI}${i}m*"
done
printf "${CSI}0m"
echo " <- bright fg"

# 256-color palette (first 16)
for i in $(seq 0 15); do
   printf "${CSI}38;5;${i}m#"
done
printf "${CSI}0m"
echo " <- 256col 0-15"

# Bold + underline + reverse
printf "${CSI}1mBOLD${CSI}0m "
printf "${CSI}4mUNDER${CSI}0m "
printf "${CSI}7mREVER${CSI}0m "
printf "${CSI}1;4;7mALL${CSI}0m"
echo " <- attributes"

##############################################################################
echo ""
echo "=== Test 3: Erase operations ==="
##############################################################################
printf "${CSI}2J${CSI}H"
# Fill line with X, then erase parts
printf "${CSI}1;1H"
printf "XXXXXXXXXXXXXXXXXXXX"

# EL 0 (erase to end from col 10)
printf "${CSI}1;10H${CSI}K"
echo ""

printf "YYYYYYYYYYYYYYYYYY"
# EL 1 (erase to beginning at col 10)
printf "${CSI}2;10H${CSI}1K"
echo ""

printf "ZZZZZZZZZZZZZZZZZZ"
# EL 2 (erase entire line)
printf "${CSI}3;10H${CSI}2K"
echo ""

##############################################################################
echo ""
echo "=== Test 4: Insert/Delete Characters ==="
##############################################################################
printf "${CSI}H${CSI}2J"
printf "${CSI}1;1HHello World"
# ICH: insert 3 spaces at col 6
printf "${CSI}1;6H${CSI}3@"
echo " <- ICH 3 at 'Hello   World'"

printf "${CSI}2;1HABCDEFGHIJ"
# DCH: delete 3 chars at col 4
printf "${CSI}2;4H${CSI}3P"
echo " <- DCH 3 at 'ABCGHIJ'"

##############################################################################
echo ""
echo "=== Test 5: Scroll region ==="
##############################################################################
printf "${CSI}H${CSI}2J"
# Set scroll region rows 2-5, fill it
printf "${CSI}2;5r"
for i in 2 3 4 5; do
   printf "${CSI}${i};1HLine ${i}"
done
# IND at bottom to scroll region
printf "${CSI}5;1H"
printf "${ESC}D"
printf "After IND"
# Reset scroll region
printf "${CSI}r"
echo ""
printf "${CSI}7;1H"
echo "Line 2 should have scrolled up"

##############################################################################
echo ""
echo "=== Test 6: DECSC/DECRC ==="
##############################################################################
printf "${CSI}H${CSI}2J"
printf "${CSI}3;10HSAVED"
printf "${ESC}7"       # save cursor at (3,15)
printf "${CSI}1;1HMOVED"
printf "${ESC}8"       # restore
printf "RESTORED"
echo ""
printf "${CSI}5;1H"
echo "Should see 'SAVEDRESTORED' at row 3"

##############################################################################
echo ""
echo "=== Test 7: DECALN ==="
##############################################################################
printf "${CSI}H${CSI}2J"
printf "${ESC}#8"      # fill screen with E's
# Carve out with EL
printf "${CSI}3;1H${CSI}2K"   # erase line 3
printf "${CSI}5;10H${CSI}K"   # erase from col 10 to end on line 5
printf "${CSI}7;1H"
echo ""
echo "Line 3 should be blank"
echo "Line 5 should have E's in cols 1-9 only"

##############################################################################
echo ""
echo "=== Test 8: Tab stops ==="
##############################################################################
printf "${CSI}H${CSI}2J"
# Default tabs at 8-col intervals
printf "${CSI}1;1H"
printf "1\t2\t3\t4"
echo " <- default tabs (every 8)"

# Custom: clear all, set at 5,15,25
printf "${CSI}3g"             # TBC clear all
printf "${CSI}1;5H${ESC}H"   # HTS at 5
printf "${CSI}1;15H${ESC}H"  # HTS at 15
printf "${CSI}1;25H${ESC}H"  # HTS at 25
printf "${CSI}2;1H"
printf "A\tB\tC\tD"
echo " <- custom tabs (5,15,25)"

##############################################################################
echo ""
echo "=== Test 9: Line drawing (ACS) ==="
##############################################################################
printf "${CSI}H${CSI}2J"
# Switch to G0 DEC Special Graphics
printf "${ESC}(0"
printf "lqqqqqk"  # upper-left, horizontal, upper-right
echo ""
printf "x     x"  # vertical bars
echo ""
printf "mqqqqqj"  # lower-left, horizontal, lower-right
printf "${ESC}(B"  # back to ASCII
echo " <- box drawing"

##############################################################################
echo ""
echo "=== Done ==="
