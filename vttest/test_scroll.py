#!/usr/bin/env python3
"""Test scroll region and scroll sequences."""
import sys

ESC = '\033'

def csi(s):
    sys.stdout.write(f'{ESC}[{s}')

def at(row, col, text):
    csi(f'{row};{col}H')
    sys.stdout.write(text)

def main():
    csi('2J')
    csi('1;1H')

    at(1, 1, '--- Top (outside scroll region) ---')
    at(20, 1, '--- Bottom (outside scroll region) ---')

    # Set scroll region rows 3-18
    csi('3;18r')

    # Fill scroll region
    for r in range(3, 19):
        at(r, 1, f'  Scroll line {r - 2:2d}')

    import time
    time.sleep(0.5)

    # Scroll up (new lines at bottom of region)
    csi('18;1H')  # move to bottom of region
    for i in range(3):
        sys.stdout.write(f'\n  New line UP-{i + 1}')

    time.sleep(0.5)

    # Reset scroll region
    csi('r')

    at(21, 1, 'Expected: Top/Bottom lines intact.')
    at(22, 1, 'Scroll region shifted up by 3 lines.')
    print()
    sys.stdout.flush()

if __name__ == '__main__':
    main()
