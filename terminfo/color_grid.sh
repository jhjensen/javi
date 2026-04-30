#!/bin/bash
# color_grid.sh — Display 256-color grid for visual comparison
#
# Usage:  TERM=javi-256color bash terminfo/color_grid.sh
#
# Produces a deterministic visual output that can be screenshot-compared
# between javi and a reference terminal (xterm, iTerm2).

ESC=$'\033'
CSI="${ESC}["

printf "=== 256-Color Grid (TERM=%s) ===\n\n" "$TERM"

# Standard colors (0-7)
printf "Standard (0-7):   "
for c in $(seq 0 7); do
   printf "${CSI}48;5;${c}m  "
done
printf "${CSI}0m\n"

# Bright colors (8-15)
printf "Bright (8-15):    "
for c in $(seq 8 15); do
   printf "${CSI}48;5;${c}m  "
done
printf "${CSI}0m\n\n"

# 6x6x6 Color Cube (16-231)
printf "Color Cube (16-231):\n"
for row in $(seq 0 5); do
   printf "  "
   for green in $(seq 0 5); do
      for blue in $(seq 0 5); do
         c=$((16 + row * 36 + green * 6 + blue))
         printf "${CSI}48;5;${c}m "
      done
      printf "${CSI}0m "
   done
   printf "\n"
done
printf "\n"

# Grayscale Ramp (232-255)
printf "Grayscale (232-255): "
for c in $(seq 232 255); do
   printf "${CSI}48;5;${c}m "
done
printf "${CSI}0m\n\n"

# SGR attribute combinations
printf "Attributes:\n"
printf "  ${CSI}0mNormal${CSI}0m "
printf "  ${CSI}1mBold${CSI}0m "
printf "  ${CSI}4mUnderline${CSI}0m "
printf "  ${CSI}7mReverse${CSI}0m "
printf "  ${CSI}1;4mBold+UL${CSI}0m "
printf "  ${CSI}1;7mBold+Rev${CSI}0m\n"

# Named colors with labels
printf "\n  ${CSI}31mRed FG${CSI}0m    "
printf "${CSI}32mGreen FG${CSI}0m  "
printf "${CSI}33mYellow FG${CSI}0m "
printf "${CSI}34mBlue FG${CSI}0m   "
printf "${CSI}35mMagenta FG${CSI}0m "
printf "${CSI}36mCyan FG${CSI}0m\n"

printf "  ${CSI}91mBrt Red${CSI}0m   "
printf "${CSI}92mBrt Green${CSI}0m "
printf "${CSI}93mBrt Yel${CSI}0m   "
printf "${CSI}94mBrt Blue${CSI}0m  "
printf "${CSI}95mBrt Mag${CSI}0m    "
printf "${CSI}96mBrt Cyan${CSI}0m\n"

printf "\n  ${CSI}41m  Red BG   ${CSI}0m "
printf "${CSI}42m  Green BG ${CSI}0m "
printf "${CSI}43m Yellow BG ${CSI}0m "
printf "${CSI}44m  Blue BG  ${CSI}0m\n"
printf "  ${CSI}101m Brt Red BG ${CSI}0m "
printf "${CSI}102m Brt Grn BG ${CSI}0m "
printf "${CSI}103m Brt Yel BG ${CSI}0m "
printf "${CSI}104m Brt Blu BG ${CSI}0m\n"

# Truecolor gradient (to nearest palette)
printf "\nTruecolor Red Gradient:   "
for i in $(seq 0 15 255); do
   printf "${CSI}48;2;${i};0;0m "
done
printf "${CSI}0m\n"

printf "Truecolor Green Gradient: "
for i in $(seq 0 15 255); do
   printf "${CSI}48;2;0;${i};0m "
done
printf "${CSI}0m\n"

printf "Truecolor Blue Gradient:  "
for i in $(seq 0 15 255); do
   printf "${CSI}48;2;0;0;${i}m "
done
printf "${CSI}0m\n"

# ACS Box Drawing
printf "\nBox Drawing (ACS):\n"
printf "  ${ESC}(0lqqqqqqqqqqqqqk${ESC}(B\n"
printf "  ${ESC}(0x${ESC}(B Hello World ${ESC}(0x${ESC}(B\n"
printf "  ${ESC}(0mqqqqqqqqqqqqqj${ESC}(B\n"
