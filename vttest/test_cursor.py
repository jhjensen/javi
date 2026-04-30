#!/usr/bin/env python3
"""Test cursor movement sequences."""
import sys

ESC = '\033'

def csi(s):
    sys.stdout.write(f'{ESC}[{s}')

def at(row, col, text):
    csi(f'{row};{col}H')
    sys.stdout.write(text)

def main():
    csi('2J')    # clear screen
    csi('1;1H')  # home

    # Draw a border
    for c in range(1, 41):
        at(1, c, '-')
        at(10, c, '-')
    for r in range(1, 11):
        at(r, 1, '|')
        at(r, 40, '|')
    at(1, 1, '+')
    at(1, 40, '+')
    at(10, 1, '+')
    at(10, 40, '+')

    # Label
    at(3, 5, 'Cursor Movement Test')

    # CUU/CUD/CUF/CUB
    at(5, 3, 'A')   # start
    csi('1C')        # right 1
    sys.stdout.write('B')
    csi('1C')
    sys.stdout.write('C')
    csi('1B')        # down 1
    csi('2D')        # left 2
    sys.stdout.write('D')
    csi('1B')        # down 1
    csi('1D')        # left 1
    sys.stdout.write('E')

    # Save/restore cursor
    at(5, 20, 'S')
    csi('s')         # save
    at(7, 25, 'X')
    csi('u')         # restore
    sys.stdout.write('R')  # should be at 5,21

    at(12, 1, 'Expected: ABC on row 5, D below B, '
              'E below D, SR adjacent at row 5')
    sys.stdout.write('\n')
    sys.stdout.flush()

if __name__ == '__main__':
    main()
