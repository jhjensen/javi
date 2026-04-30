#!/usr/bin/env python3
"""Test erase sequences (ED, EL)."""
import sys

ESC = '\033'

def csi(s):
    sys.stdout.write(f'{ESC}[{s}')

def at(row, col, text):
    csi(f'{row};{col}H')
    sys.stdout.write(text)

def main():
    csi('2J')    # clear screen
    csi('1;1H')

    # Fill rows 1-8 with text
    for r in range(1, 9):
        at(r, 1, f'Row {r}: ' + 'X' * 30)

    import time
    time.sleep(0.5)

    # EL 0 — erase from cursor to end of line
    at(2, 15, '')
    csi('0K')  # erase to right

    # EL 1 — erase from start to cursor
    at(3, 15, '')
    csi('1K')  # erase to left

    # EL 2 — erase entire line
    at(4, 15, '')
    csi('2K')  # erase whole line

    # ED 0 — erase from cursor to end of screen
    at(7, 10, '')
    csi('0J')  # erase below

    at(10, 1, 'Row 2: right half erased')
    at(11, 1, 'Row 3: left half erased')
    at(12, 1, 'Row 4: entire line erased')
    at(13, 1, 'Rows 7-8: erased (ED 0 from row 7)')
    print()
    sys.stdout.flush()

if __name__ == '__main__':
    main()
