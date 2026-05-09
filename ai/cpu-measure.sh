#!/bin/bash
# cpu-measure.sh — Measure javi CPU ticks in focused and unfocused states.
# Runs inside Docker with Xvfb + openbox.
set -e

export DISPLAY=:99

# Start Xvfb
Xvfb :99 -screen 0 1024x768x24 -nolisten tcp &
XVFB_PID=$!

# Wait for X server to be ready
for i in $(seq 1 20); do
   if xdotool getactivewindow >/dev/null 2>&1 || [ -e /tmp/.X11-unix/X99 ]; then
      break
   fi
   sleep 0.5
done
sleep 1

# Start window manager (needed for focus/minimize)
openbox &
sleep 1

echo "=== Starting javi ==="
java -jar build/libs/*-all.jar /dev/null &
JPID=$!

# Wait for javi to start and settle
sleep 5

# Verify javi is still running
if ! kill -0 $JPID 2>/dev/null; then
   echo "ERROR: javi process $JPID died before measurement"
   exit 1
fi

echo "=== Focused idle 30s ==="
T1=$(awk '{print $14+$15}' /proc/$JPID/stat)
sleep 30
T2=$(awk '{print $14+$15}' /proc/$JPID/stat)
FOCUSED=$((T2-T1))
echo "Focused ticks: $FOCUSED"

echo "=== Unfocused 30s ==="
WID=$(xdotool getactivewindow 2>/dev/null || true)
if [ -n "$WID" ]; then
   xdotool windowminimize "$WID"
   echo "Minimized window $WID"
else
   echo "WARNING: no active window found, using xdotool key to unfocus"
   xdotool key super 2>/dev/null || true
fi
sleep 2

T3=$(awk '{print $14+$15}' /proc/$JPID/stat)
sleep 30
T4=$(awk '{print $14+$15}' /proc/$JPID/stat)
UNFOCUSED=$((T4-T3))
echo "Unfocused ticks: $UNFOCUSED"

echo "=== Summary ==="
echo "Focused:   $FOCUSED ticks in 30s"
echo "Unfocused: $UNFOCUSED ticks in 30s"

# Calculate approximate CPU percentage (100 ticks/sec on most Linux)
HZ=100
FOCUSED_PCT=$(awk "BEGIN {printf \"%.1f\", $FOCUSED / (30 * $HZ) * 100}")
UNFOCUSED_PCT=$(awk "BEGIN {printf \"%.1f\", $UNFOCUSED / (30 * $HZ) * 100}")
echo "Focused:   ${FOCUSED_PCT}%"
echo "Unfocused: ${UNFOCUSED_PCT}%"

kill $JPID 2>/dev/null || true
kill $XVFB_PID 2>/dev/null || true
