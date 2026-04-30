#!/bin/bash
# esctest2-runner.sh — Runs esctest2 inside a javi shell in Docker
# This script is the SHELL wrapper: when javi does :shellnew, it forks this.
# esctest2 talks to javi's VT100 emulator via the PTY.

set -e

ESCTEST_DIR=/esctest2/esctest
LOGFILE=/results/esctest2.log

cd "$ESCTEST_DIR"

# Run esctest2 at VT level 2 (VT220), xterm-compatible mode
# --logfile points to results dir (not /tmp per AGENTS.MD rules)
# --timeout=2 gives javi more time to respond
python3 esctest.py \
    --expected-terminal=xterm \
    --max-vt-level=2 \
    --logfile="$LOGFILE" \
    --timeout=2 \
    --no-print-logs \
    2>/results/esctest2-stderr.log

echo "esctest2 exit code: $?" > /results/esctest2-exit.txt
