#!/bin/bash
# test_tmux_capture.sh — Automated terminal test using tmux screen capture
#
# Uses tmux to create a virtual terminal, runs test sequences, and
# captures the resulting screen content for comparison.
#
# Usage:
#   bash test_tmux_capture.sh [output_dir]
#
# Requirements: tmux must be installed
#
# Output: text files in output_dir/ with captured screen content
# after each test.  Compare these files between terminals.

set -e

outdir="${1:-results}"
session="javi-test-$$"
ROWS=24
COLS=80

mkdir -p "$outdir"

# Check tmux availability
if ! command -v tmux &>/dev/null; then
   echo "ERROR: tmux not found. Install with: apt-get install tmux"
   exit 1
fi

# Kill any leftover session
tmux kill-session -t "$session" 2>/dev/null || true

# Create detached tmux session with fixed geometry
tmux new-session -d -s "$session" -x "$COLS" -y "$ROWS"

# Helper: send keys to tmux, wait, then capture
run_test() {
   local name="$1"
   shift
   # Clear screen first
   tmux send-keys -t "$session" "printf '\\033[2J\\033[H'" Enter
   sleep 0.2

   # Send the test commands
   for cmd in "$@"; do
      tmux send-keys -t "$session" "$cmd" Enter
      sleep 0.1
   done
   sleep 0.3

   # Capture pane content
   tmux capture-pane -t "$session" -p > "$outdir/${name}.txt"
   echo "  Captured: $outdir/${name}.txt"
}

# Helper: send raw escape sequence (not as a shell command)
send_raw() {
   tmux send-keys -t "$session" -l "$1"
}

capture() {
   local name="$1"
   sleep 0.3
   tmux capture-pane -t "$session" -p > "$outdir/${name}.txt"
   echo "  Captured: $outdir/${name}.txt"
}

echo "=== Terminal Screen Capture Tests ==="
echo "Session: $session  Size: ${COLS}x${ROWS}"
echo "Output: $outdir/"
echo ""

##############################################################################
echo "Test 1: DECALN (fill with E's)"
##############################################################################
# Run a small script that fills screen and carves out
tmux send-keys -t "$session" "printf '\\033#8'" Enter
sleep 0.3
capture "01_decaln_fill"

# Now erase line 3 and partial line 5
tmux send-keys -t "$session" "printf '\\033[3;1H\\033[2K'" Enter
tmux send-keys -t "$session" "printf '\\033[5;10H\\033[K'" Enter
sleep 0.3
capture "02_decaln_carved"

##############################################################################
echo "Test 2: SGR Colors"
##############################################################################
tmux send-keys -t "$session" "printf '\\033[2J\\033[H'" Enter
sleep 0.2
# Standard ANSI foreground
tmux send-keys -t "$session" \
   "for i in 30 31 32 33 34 35 36 37; do printf \"\\033[\${i}m#\"; done; printf '\\033[0m\\n'" Enter
# Bright foreground
tmux send-keys -t "$session" \
   "for i in 90 91 92 93 94 95 96 97; do printf \"\\033[\${i}m#\"; done; printf '\\033[0m\\n'" Enter
# Bold, underline, reverse
tmux send-keys -t "$session" \
   "printf '\\033[1mBOLD\\033[0m \\033[4mUNDER\\033[0m \\033[7mREVER\\033[0m\\n'" Enter
sleep 0.3
capture "03_sgr_colors"

##############################################################################
echo "Test 3: Cursor movement"
##############################################################################
tmux send-keys -t "$session" "printf '\\033[2J\\033[H'" Enter
sleep 0.2
tmux send-keys -t "$session" "printf '\\033[1;1HA\\033[1;40HB\\033[12;20HC\\033[24;1HD\\033[24;80HE'" Enter
sleep 0.3
capture "04_cursor_movement"

##############################################################################
echo "Test 4: Scroll region"
##############################################################################
tmux send-keys -t "$session" "printf '\\033[2J\\033[H'" Enter
sleep 0.2
# Set scroll region rows 5-15, fill it
tmux send-keys -t "$session" "printf '\\033[5;15r'" Enter
for i in $(seq 5 15); do
   tmux send-keys -t "$session" "printf '\\033[${i};1HLine %02d' $i" Enter
done
# IND at bottom to scroll
tmux send-keys -t "$session" "printf '\\033[15;1H\\033D'" Enter
tmux send-keys -t "$session" "printf 'SCROLLED'" Enter
# Reset region
tmux send-keys -t "$session" "printf '\\033[r'" Enter
sleep 0.3
capture "05_scroll_region"

##############################################################################
echo "Test 5: Box drawing (ACS)"
##############################################################################
tmux send-keys -t "$session" "printf '\\033[2J\\033[H'" Enter
sleep 0.2
tmux send-keys -t "$session" "printf '\\033(0lqqqqqk\\n'" Enter
tmux send-keys -t "$session" "printf '\\033(0x     x\\n'" Enter
tmux send-keys -t "$session" "printf '\\033(0mqqqqqj\\n'" Enter
tmux send-keys -t "$session" "printf '\\033(B'" Enter  # back to ASCII
sleep 0.3
capture "06_box_drawing"

##############################################################################
echo "Test 6: Tab stops"
##############################################################################
tmux send-keys -t "$session" "printf '\\033[2J\\033[H'" Enter
sleep 0.2
tmux send-keys -t "$session" "printf '1\\t2\\t3\\t4\\n'" Enter
sleep 0.3
capture "07_tab_stops"

##############################################################################
echo "Test 7: Insert/Delete characters"
##############################################################################
tmux send-keys -t "$session" "printf '\\033[2J\\033[H'" Enter
sleep 0.2
tmux send-keys -t "$session" "printf '\\033[1;1HHello World'" Enter
# ICH 3 at col 6
tmux send-keys -t "$session" "printf '\\033[1;6H\\033[3@'" Enter
sleep 0.3
capture "08_insert_chars"

##############################################################################
echo ""
echo "=== All tests complete ==="
echo "Results in: $outdir/"
echo ""
##############################################################################

# Cleanup
tmux kill-session -t "$session" 2>/dev/null || true

echo "Compare results between terminals using: diff -r dir1/ dir2/"
