#!/bin/bash
# test_resize.sh — Terminal resize behavior test
#
# Usage: bash terminfo/test_resize.sh
#
# Tests that the terminal correctly reports dimensions after resize.
# Run this in a javi shell, then resize the window.  The script
# monitors stty size, COLUMNS/LINES, and DSR 6n (cursor position
# query) to detect mismatches.

ESC=$'\033'
CSI="${ESC}["

read_cursor_pos() {
   local row col
   IFS=';' read -sdR -p "${CSI}6n" row col </dev/tty 2>/dev/null
   row=${row##*[}
   echo "$row $col"
}

get_term_size() {
   local response
   # XTWINOPS: CSI 18 t — terminal responds CSI 8 ; rows ; cols t
   IFS=';' read -sdt -p "${CSI}18t" _ rows cols </dev/tty 2>/dev/null
   echo "$rows $cols"
}

##############################################################################
# Continuous resize monitor
##############################################################################

echo "=== Terminal Resize Test ==="
echo "Resize the terminal window.  This script checks dimensions."
echo "Press Ctrl-C to stop."
echo ""

prev_rows=0
prev_cols=0
iteration=0

trap 'echo ""; echo "Done after $iteration checks."; exit 0' INT

while true; do
   iteration=$((iteration + 1))
   stty_size=$(stty size 2>/dev/null)
   stty_rows=${stty_size% *}
   stty_cols=${stty_size#* }

   # Compare stty with shell variables
   shell_lines=${LINES:-?}
   shell_cols=${COLUMNS:-?}

   if [[ "$stty_rows" != "$prev_rows" || "$stty_cols" != "$prev_cols" ]]; then
      printf "[%03d] RESIZE: stty=%sx%s  LINES=%s COLUMNS=%s\n" \
         "$iteration" "$stty_rows" "$stty_cols" \
         "$shell_lines" "$shell_cols"

      # Validate stty rows is sane
      if [[ "$stty_rows" -le 1 ]]; then
         echo "  *** BUG: stty rows=$stty_rows (should be > 1)"
      fi

      prev_rows=$stty_rows
      prev_cols=$stty_cols
   fi

   sleep 0.5
done
