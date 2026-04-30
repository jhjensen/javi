#!/bin/bash
# test_screen_capture.sh — Capture terminal screen state for comparison
#
# Usage: bash terminfo/test_screen_capture.sh [output_dir]
#
# Sends escape sequences then uses DSR 6n to verify cursor position.
# Also captures "screen content" by reading back character positions
# where possible.  Output is deterministic so it can be compared
# between javi and a reference terminal (xterm, iTerm2).
#
# For Docker testing:
#   docker run --rm -it -e TERM=xterm-256color ubuntu:24.04 bash
#   # then paste or run this script inside the container

set -e

ESC=$'\033'
CSI="${ESC}["
outdir="${1:-.}"

pass=0
fail=0
total=0

read_cursor_pos() {
   local row col
   IFS=';' read -sdR -p "${CSI}6n" row col </dev/tty 2>/dev/null
   row=${row##*[}
   echo "$row $col"
}

check_pos() {
   local label="$1" er="$2" ec="$3"
   total=$((total + 1))
   local pos row col
   pos=$(read_cursor_pos)
   row=${pos% *}
   col=${pos#* }
   if [[ "$row" == "$er" && "$col" == "$ec" ]]; then
      pass=$((pass + 1))
   else
      fail=$((fail + 1))
      printf "FAIL: %-40s expected=(%s,%s) got=(%s,%s)\n" \
         "$label" "$er" "$ec" "$row" "$col"
   fi
}

printf "${CSI}2J${CSI}H"  # clear screen, home cursor

##############################################################################
echo "=== 1. Cursor Movement ==="
##############################################################################

# CUP to (5,10), verify
printf "${CSI}5;10H"
check_pos "CUP 5;10" 5 10

# CUU 2 (cursor up 2)
printf "${CSI}2A"
check_pos "CUU 2 from (5,10)" 3 10

# CUD 4 (cursor down 4)
printf "${CSI}4B"
check_pos "CUD 4 from (3,10)" 7 10

# CUF 5 (cursor forward 5)
printf "${CSI}5C"
check_pos "CUF 5 from (7,10)" 7 15

# CUB 3 (cursor back 3)
printf "${CSI}3D"
check_pos "CUB 3 from (7,15)" 7 12

# CHA 1 (cursor to column 1)
printf "${CSI}1G"
check_pos "CHA 1 from (7,12)" 7 1

# VPA 3 (line position absolute, row 3)
printf "${CSI}3d"
check_pos "VPA 3 from (7,1)" 3 1

##############################################################################
echo "=== 2. DECALN + Erase ==="
##############################################################################

# Fill screen with E's (DECALN), then carve out with EL/ED
printf "${ESC}#8"              # DECALN fill
printf "${CSI}2;1H${CSI}K"    # EL 0 (erase to end) on row 2
printf "${CSI}3;1H${CSI}1K"   # EL 1 (erase to beginning) on row 3
printf "${CSI}4;5H${CSI}2K"   # EL 2 (erase entire line) on row 4
check_pos "After EL sequences" 4 5

# ED 1 (erase from beginning of screen to cursor)
printf "${CSI}10;40H${CSI}1J"
check_pos "After ED 1 at (10,40)" 10 40

##############################################################################
echo "=== 3. Scroll Region ==="
##############################################################################

printf "${CSI}2J${CSI}H"  # clear

# Set scroll region rows 5-15
printf "${CSI}5;15r"
check_pos "DECSTBM homes cursor" 1 1

# Write text at each row of the region
for i in $(seq 5 15); do
   printf "${CSI}${i};1H"
   printf "Line %02d of scroll region" "$i"
done
check_pos "After filling scroll region" 15 25

# IND (index) at bottom of region should scroll region up
printf "${CSI}15;1H"
printf "${ESC}D"  # IND
check_pos "After IND at bottom" 15 1

# Reset scroll region
printf "${CSI}r"

##############################################################################
echo "=== 4. Tab Stops ==="
##############################################################################

printf "${CSI}2J${CSI}H"  # clear

# Clear all tab stops, set custom ones
printf "${CSI}3g"           # TBC 3 — clear all
printf "${CSI}1;5H${ESC}H" # HTS at col 5
printf "${CSI}1;15H${ESC}H" # HTS at col 15
printf "${CSI}1;30H${ESC}H" # HTS at col 30

# Tab from col 1
printf "${CSI}1;1H"
printf "\t"
check_pos "Tab to stop at 5" 1 5
printf "\t"
check_pos "Tab to stop at 15" 1 15
printf "\t"
check_pos "Tab to stop at 30" 1 30

##############################################################################
echo "=== 5. Save/Restore Cursor (DECSC/DECRC) ==="
##############################################################################

printf "${CSI}2J${CSI}H"
printf "${CSI}7;20H"  # position cursor
printf "${ESC}7"       # DECSC save
printf "${CSI}1;1H"   # move away
printf "${ESC}8"       # DECRC restore
check_pos "DECRC restores (7,20)" 7 20

##############################################################################
echo "=== 6. Insert/Delete Lines (IL/DL) ==="
##############################################################################

printf "${CSI}2J${CSI}H"
printf "${CSI}5;15r"   # scroll region 5-15

# Fill region
for i in $(seq 5 15); do
   printf "${CSI}${i};1Hrow-%02d" "$i"
done

# Insert 2 lines at row 8
printf "${CSI}8;1H${CSI}2L"
check_pos "After IL 2 at row 8" 8 1

# Delete 1 line at row 10
printf "${CSI}10;1H${CSI}1M"
check_pos "After DL 1 at row 10" 10 1

printf "${CSI}r"  # reset scroll region

##############################################################################
echo "=== 7. Insert/Delete Characters (ICH/DCH) ==="
##############################################################################

printf "${CSI}2J${CSI}H"
printf "${CSI}3;1HHello World"
printf "${CSI}3;6H${CSI}3@"   # ICH 3 at col 6 (insert 3 spaces)
check_pos "After ICH 3" 3 6

printf "${CSI}3;9H${CSI}2P"   # DCH 2 at col 9 (delete 2 chars)
check_pos "After DCH 2" 3 9

##############################################################################
echo "=== 8. Autowrap ==="
##############################################################################

printf "${CSI}2J${CSI}H"
# Get terminal width
stty_size=$(stty size 2>/dev/null)
cols=${stty_size#* }
cols=${cols:-80}

# Write exactly $cols characters — cursor should be at end of line
# (pending wrap, not yet wrapped)
printf "${CSI}1;1H"
for ((i=1; i<=cols; i++)); do printf "X"; done
check_pos "After $cols chars (pending wrap)" 1 "$cols"

# Write one more character — should wrap to row 2, col 1
# (cursor advances to col 2 after the char is placed at 2,1)
printf "Y"
check_pos "After wrap character" 2 2

##############################################################################
echo ""
echo "=== Summary: $pass/$total passed, $fail failed ==="
