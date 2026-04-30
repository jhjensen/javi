#!/bin/bash
# test_fullscreen.sh — Simulate fullscreen app behavior for resize testing
#
# This script mimics what htop/vi does:
# 1. Switch to alternate screen buffer
# 2. Set scroll region
# 3. Draw a bordered display
# 4. Handle SIGWINCH (resize) by querying dimensions and redrawing
#
# Usage: bash terminfo/test_fullscreen.sh
# Press 'q' to quit.

ESC=$'\033'
CSI="${ESC}["

# Save terminal settings and switch to alternate screen
stty -echo
printf "${CSI}?1049h"  # switch to alternate screen + save cursor
printf "${CSI}?25l"     # hide cursor

cleanup() {
   printf "${CSI}?25h"     # show cursor
   printf "${CSI}?1049l"   # restore main screen
   stty echo
   exit 0
}

trap cleanup EXIT INT TERM

draw_screen() {
   local rows cols r

   # Query actual terminal dimensions from stty
   local stty_size
   stty_size=$(stty size 2>/dev/null)
   rows=${stty_size% *}
   cols=${stty_size#* }

   # Fallback
   rows=${rows:-24}
   cols=${cols:-80}

   # Clear screen and home cursor
   printf "${CSI}2J${CSI}H"

   # Set scroll region to full screen
   printf "${CSI}1;${rows}r"

   # Draw top border
   printf "${CSI}1;1H"
   printf "+"
   for ((c=2; c<cols; c++)); do printf "-"; done
   printf "+"

   # Draw side borders and content
   for ((r=2; r<rows; r++)); do
      printf "${CSI}${r};1H|"
      printf "${CSI}${r};${cols}H|"
   done

   # Draw bottom border
   printf "${CSI}${rows};1H+"
   for ((c=2; c<cols; c++)); do printf "-"; done
   printf "+"

   # Draw status info in center
   local mid_row=$(( rows / 2 ))
   local msg="Terminal: ${rows} rows x ${cols} cols"
   local msg_len=${#msg}
   local mid_col=$(( (cols - msg_len) / 2 ))
   printf "${CSI}${mid_row};${mid_col}H${msg}"

   local stty_msg="stty size: ${stty_size}"
   local stty_len=${#stty_msg}
   local stty_col=$(( (cols - stty_len) / 2 ))
   printf "${CSI}$((mid_row + 1));${stty_col}H${stty_msg}"

   printf "${CSI}$((mid_row + 2));${stty_col}HPress 'q' to quit"

   # Home cursor
   printf "${CSI}${rows};1H"
}

# Initial draw
draw_screen

# Handle SIGWINCH
handle_resize() {
   draw_screen
}
trap handle_resize WINCH

# Main loop — read keys
while true; do
   if read -rsn1 -t 0.5 key 2>/dev/null; then
      case "$key" in
         q|Q) break ;;
      esac
   fi
done
