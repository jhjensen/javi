#!/bin/bash
# test_sequences.sh — Terminal capability test for javi-256color
#
# Usage:  TERM=javi-256color bash terminfo/test_sequences.sh
#    or:  bash terminfo/test_sequences.sh
#
# Tests escape sequences that javi's VT100 emulator supports.
# Each test prints a label, sends sequences, and shows the result.
# Visual comparison: run in javi and in a reference terminal (xterm/iTerm2).

set -e

ESC=$'\033'
CSI="${ESC}["

pass=0
fail=0
total=0

##############################################################################
# Helpers
##############################################################################

# Read cursor position via DSR (CSI 6 n) — response is CSI row ; col R
read_cursor_pos() {
   local row col
   IFS=';' read -sdR -p "${CSI}6n" row col </dev/tty 2>/dev/null
   row=${row##*[}  # strip CSI prefix
   echo "$row $col"
}

check_cursor() {
   local label="$1" expect_row="$2" expect_col="$3"
   local pos row col
   total=$((total + 1))
   pos=$(read_cursor_pos)
   row=${pos% *}
   col=${pos#* }
   if [[ "$row" == "$expect_row" && "$col" == "$expect_col" ]]; then
      pass=$((pass + 1))
      printf "  PASS: %s (row=%s col=%s)\n" "$label" "$row" "$col"
   else
      fail=$((fail + 1))
      printf "  FAIL: %s expected=(%s,%s) got=(%s,%s)\n" \
         "$label" "$expect_row" "$expect_col" "$row" "$col"
   fi
}

section() {
   printf "\n=== %s ===\n" "$1"
}

##############################################################################
# 1. Device Status Report / Device Attributes
##############################################################################
section "Device Status Report"

# DSR 5 — terminal should respond CSI 0 n
printf "DSR 5 (status query): "
response=""
IFS= read -sdt1 -p "${CSI}5n" response </dev/tty 2>/dev/null || true
if [[ "$response" == *"0n"* ]]; then
   printf "OK — terminal reports ready\n"
else
   printf "response='%s' (expected CSI 0 n)\n" "$response"
fi

# Primary DA — terminal should respond with device attributes
printf "Primary DA (CSI c): "
response=""
IFS= read -sdt1 -p "${CSI}c" response </dev/tty 2>/dev/null || true
if [[ -n "$response" ]]; then
   printf "OK — got response\n"
else
   printf "no response\n"
fi

##############################################################################
# 2. Cursor Movement
##############################################################################
section "Cursor Movement"

# Move to known position and verify
printf "${CSI}H"              # Home (1,1)
check_cursor "CUP home" 1 1

printf "${CSI}5;10H"          # Move to row 5, col 10
check_cursor "CUP 5;10" 5 10

printf "${CSI}A"              # CUU — cursor up 1
check_cursor "CUU 1" 4 10

printf "${CSI}2B"             # CUD — cursor down 2
check_cursor "CUD 2" 6 10

printf "${CSI}3C"             # CUF — cursor forward 3
check_cursor "CUF 3" 6 13

printf "${CSI}5D"             # CUB — cursor back 5
check_cursor "CUB 5" 6 8

printf "${CSI}1G"             # CHA — column 1
check_cursor "CHA 1" 6 1

printf "${CSI}20G"            # CHA — column 20
check_cursor "CHA 20" 6 20

printf "${CSI}3d"             # VPA — row 3
check_cursor "VPA 3" 3 20

##############################################################################
# 3. Erase Operations (visual — check for clean output)
##############################################################################
section "Erase Operations"

# Fill line, then erase to end
printf "${CSI}10;1H"
printf "XXXXXXXXXXXXXXXXXXXXXX"
printf "${CSI}10;11H"
printf "${CSI}K"              # EL 0 — erase from cursor to EOL
printf "${CSI}10;1H"
pos=$(read_cursor_pos)
printf "  EL 0: first 10 chars should be X, rest blank\n"

# EL 1 — erase from beginning to cursor
printf "${CSI}11;1H"
printf "XXXXXXXXXXXXXXXXXXXXXX"
printf "${CSI}11;11H"
printf "${CSI}1K"             # EL 1
printf "  EL 1: first 10 chars blank, rest X\n"

# ED 2 — erase entire screen
printf "${CSI}2J"
printf "${CSI}H"
printf "  ED 2: screen cleared\n"

##############################################################################
# 4. SGR — Colors
##############################################################################
section "SGR Colors"

# Standard 8 ANSI foreground colors
printf "  FG standard:  "
for i in 30 31 32 33 34 35 36 37; do
   printf "${CSI}${i}m##"
done
printf "${CSI}0m\n"

# Bright ANSI foreground colors
printf "  FG bright:    "
for i in 90 91 92 93 94 95 96 97; do
   printf "${CSI}${i}m##"
done
printf "${CSI}0m\n"

# Standard 8 ANSI background colors
printf "  BG standard:  "
for i in 40 41 42 43 44 45 46 47; do
   printf "${CSI}${i}m  "
done
printf "${CSI}0m\n"

# Bright ANSI background colors
printf "  BG bright:    "
for i in 100 101 102 103 104 105 106 107; do
   printf "${CSI}${i}m  "
done
printf "${CSI}0m\n"

# 256-color foreground (first 16 + some from cube + grayscale)
printf "  256-color FG: "
for i in 1 2 3 4 5 6 9 10 11 12 13 14 196 208 226 46 21 201; do
   printf "${CSI}38;5;${i}m##"
done
printf "${CSI}0m\n"

# 256-color background ramp
printf "  256-color BG: "
for i in 232 235 238 241 244 247 250 253 255; do
   printf "${CSI}48;5;${i}m  "
done
printf "${CSI}0m\n"

# Truecolor (if supported — javi approximates to nearest 256-color)
printf "  Truecolor FG: "
printf "${CSI}38;2;255;0;0mRED "
printf "${CSI}38;2;0;255;0mGRN "
printf "${CSI}38;2;0;0;255mBLU "
printf "${CSI}38;2;255;165;0mORG "
printf "${CSI}38;2;128;0;128mPUR "
printf "${CSI}0m\n"

##############################################################################
# 5. Text Attributes
##############################################################################
section "Text Attributes"

printf "  ${CSI}1mBold${CSI}0m  "
printf "${CSI}4mUnderline${CSI}0m  "
printf "${CSI}7mReverse${CSI}0m  "
printf "${CSI}1;4mBold+UL${CSI}0m  "
printf "${CSI}1;7mBold+Rev${CSI}0m\n"

# Combined: bold + color
printf "  ${CSI}1;31mBold Red${CSI}0m  "
printf "${CSI}4;34mUnderline Blue${CSI}0m  "
printf "${CSI}7;32mReverse Green${CSI}0m\n"

##############################################################################
# 6. Scroll Region (DECSTBM)
##############################################################################
section "Scroll Region"

printf "${CSI}2J${CSI}H"     # Clear screen
printf "${CSI}3;8r"           # Set scroll region rows 3-8
printf "${CSI}3;1H"           # Move into region

# Fill region
for i in 3 4 5 6 7 8; do
   printf "${CSI}${i};1HLine $i in scroll region"
done

# Scroll up by adding text at bottom
printf "${CSI}8;1H"
printf "\nScrolled line 1\nScrolled line 2"
printf "${CSI}r"              # Reset scroll region
printf "${CSI}10;1H"
printf "  DECSTBM: lines above row 3 and below row 8 should be intact\n"

##############################################################################
# 7. Tab Stops
##############################################################################
section "Tab Stops"

printf "${CSI}2J${CSI}H"
printf "${CSI}3g"             # TBC 3 — clear all tab stops
# Set custom stops at columns 5, 15, 25, 40
printf "${CSI}1;5H${ESC}H"   # HTS at col 5
printf "${CSI}1;15H${ESC}H"  # HTS at col 15
printf "${CSI}1;25H${ESC}H"  # HTS at col 25
printf "${CSI}1;40H${ESC}H"  # HTS at col 40
printf "${CSI}2;1H"           # Go to row 2, col 1
printf "1\t2\t3\t4\t5"       # Tab between stops
printf "\n  HTS/TBC: numbers should appear at columns 1, 5, 15, 25, 40\n"

# Restore default tab stops
printf "${CSI}3g"
for col in 9 17 25 33 41 49 57 65 73; do
   printf "${CSI}1;${col}H${ESC}H"
done

##############################################################################
# 8. DECSC/DECRC (Save/Restore Cursor)
##############################################################################
section "Save/Restore Cursor"

printf "${CSI}2J${CSI}H"
printf "${CSI}5;20H"                   # Position cursor
printf "${ESC}7"                       # DECSC — save
printf "${CSI}1;1H"                    # Move away
printf "${ESC}8"                       # DECRC — restore
check_cursor "DECSC/DECRC" 5 20

##############################################################################
# 9. Alternate Screen Buffer
##############################################################################
section "Alternate Screen Buffer"

printf "  Switching to alt screen... "
printf "${CSI}?1049h"         # smcup — switch to alt screen
printf "${CSI}2J${CSI}H"
printf "This is the alternate screen buffer. Press any key..."
# Don't actually wait — just show it briefly
sleep 0.3
printf "${CSI}?1049l"         # rmcup — back to normal
printf "back to normal screen\n"

##############################################################################
# 10. Line Drawing Characters (ACS)
##############################################################################
section "ACS Line Drawing"

printf "  Box: "
printf "${ESC}(0"             # smacs — enter line drawing mode
printf "lqqqqqqqqk"           # top:    ┌──────────┐
printf "\n        "
printf "x        x"           # middle: │          │
printf "\n        "
printf "mqqqqqqqqj"           # bottom: └──────────┘
printf "${ESC}(B"             # rmacs — exit line drawing mode
printf "\n"

##############################################################################
# 11. Cursor Visibility
##############################################################################
section "Cursor Visibility"

printf "  Hide cursor: ${CSI}?25l"
sleep 0.3
printf " (cursor should be hidden)"
printf "  Show cursor: ${CSI}?25h"
printf " (cursor visible again)\n"

##############################################################################
# 12. DECALN (Screen Alignment Test)
##############################################################################
section "DECALN"

printf "${CSI}2J${CSI}H"
printf "${ESC}#8"             # DECALN — fill screen with E's
sleep 0.3
printf "${CSI}2J${CSI}H"     # Clear
printf "  DECALN: screen briefly filled with E's\n"

##############################################################################
# Summary
##############################################################################
section "Summary"
printf "  Cursor position tests: %d passed, %d failed, %d total\n" \
   "$pass" "$fail" "$total"
printf "  Visual tests require manual comparison between javi and reference terminal.\n"
printf "  Run with: TERM=javi-256color bash terminfo/test_sequences.sh\n"
