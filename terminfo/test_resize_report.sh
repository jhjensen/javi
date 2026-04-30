#!/bin/bash
# test_resize_report.sh — Verify terminal size reporting
#
# Usage: bash terminfo/test_resize_report.sh
#
# Sends CSI 18t (XTWINOPS report terminal size) and verifies the
# response matches stty values. This tests that the terminal's
# internal size tracking agrees with the PTY dimensions.
#
# Useful for diagnosing htop/vim resize failures: if CSI 18t
# returns the wrong size, ncurses apps will use stale dimensions
# even after receiving SIGWINCH.

ESC=$'\033'
CSI="${ESC}["
pass=0
fail=0

report() {
   local label="$1" expected="$2" actual="$3"
   if [ "$expected" = "$actual" ]; then
      echo "  PASS: $label — expected=$expected actual=$actual"
      ((pass++))
   else
      echo "  FAIL: $label — expected=$expected actual=$actual"
      ((fail++))
   fi
}

echo "=== Terminal Size Report Verification ==="
echo ""

# Method 1: stty size
stty_size=$(stty size 2>/dev/null)
stty_rows=${stty_size%% *}
stty_cols=${stty_size##* }
echo "stty size: rows=$stty_rows cols=$stty_cols"

# Method 2: TIOCGWINSZ via tput (queries terminfo)
tput_rows=$(tput lines 2>/dev/null)
tput_cols=$(tput cols 2>/dev/null)
echo "tput:      rows=$tput_rows cols=$tput_cols"

# Method 3: Shell variables
echo "LINES=$LINES COLUMNS=$COLUMNS"

# Method 4: CSI 18t (terminal size report)
# Response format: ESC[8;rows;colst
read_xtwinops() {
   local response
   # Send CSI 18t and read response
   printf "${CSI}18t" > /dev/tty
   # Read response: ESC[8;R;Ct
   local buf=""
   while IFS= read -rsn1 -t 2 ch; do
      buf+="$ch"
      if [[ "$ch" == "t" ]]; then
         break
      fi
   done < /dev/tty
   # Parse: strip ESC[8; prefix and t suffix
   buf="${buf#*8;}"
   buf="${buf%t}"
   local r="${buf%;*}"
   local c="${buf#*;}"
   echo "$r $c"
}

winops_result=$(read_xtwinops)
winops_rows=${winops_result%% *}
winops_cols=${winops_result##* }
echo "CSI 18t:   rows=$winops_rows cols=$winops_cols"

# Method 5: DSR 6n — move to bottom-right corner, query position
printf "${CSI}999;999H" > /dev/tty  # Move cursor to max position
printf "${CSI}6n" > /dev/tty       # Request position
dsr_response=""
while IFS= read -rsn1 -t 2 ch; do
   dsr_response+="$ch"
   if [[ "$ch" == "R" ]]; then
      break
   fi
done < /dev/tty
dsr_response="${dsr_response#*[}"
dsr_response="${dsr_response%R}"
dsr_rows="${dsr_response%;*}"
dsr_cols="${dsr_response#*;}"
echo "DSR 6n:    max_row=$dsr_rows max_col=$dsr_cols"
printf "${CSI}H" > /dev/tty  # Return cursor to home

echo ""
echo "=== Comparing Methods ==="

# All methods should agree
report "stty vs tput rows" "$stty_rows" "$tput_rows"
report "stty vs tput cols" "$stty_cols" "$tput_cols"
if [ -n "$winops_rows" ]; then
   report "stty vs CSI18t rows" "$stty_rows" "$winops_rows"
   report "stty vs CSI18t cols" "$stty_cols" "$winops_cols"
fi
if [ -n "$dsr_rows" ]; then
   report "stty vs DSR rows" "$stty_rows" "$dsr_rows"
   report "stty vs DSR cols" "$stty_cols" "$dsr_cols"
fi

echo ""
echo "Results: $pass passed, $fail failed"
echo ""
echo "If stty matches but CSI 18t or DSR disagree, the terminal's"
echo "internal size tracking is out of sync with the PTY."
echo "This would cause htop/vim to use wrong dimensions after resize."
