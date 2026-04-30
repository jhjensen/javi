#!/usr/bin/env python3
"""Test SGR color and attribute sequences."""
import sys

ESC = '\033'

def sgr(code, text):
    sys.stdout.write(f'{ESC}[{code}m{text}{ESC}[0m')

def main():
    sys.stdout.write(f'{ESC}[2J{ESC}[1;1H')

    print('=== Basic Attributes ===')
    sgr('1', 'Bold')
    print('  ', end='')
    sgr('2', 'Dim')
    print('  ', end='')
    sgr('3', 'Italic')
    print('  ', end='')
    sgr('4', 'Underline')
    print('  ', end='')
    sgr('7', 'Reverse')
    print('  ', end='')
    sgr('9', 'Strikethrough')
    print()
    print()

    print('=== 8 Standard Colors (fg) ===')
    for i in range(30, 38):
        sgr(str(i), f' {i} ')
    print()
    print()

    print('=== 8 Bright Colors (fg) ===')
    for i in range(90, 98):
        sgr(str(i), f' {i} ')
    print()
    print()

    print('=== 8 Standard Colors (bg) ===')
    for i in range(40, 48):
        sgr(str(i), f' {i} ')
    print()
    print()

    print('=== 256 Color (selected) ===')
    for i in [0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
              16, 51, 87, 123, 196, 208, 220, 255]:
        sgr(f'38;5;{i}', f' {i:3d}')
    print()
    print()

    print('=== Truecolor (24-bit) ===')
    # Gradient red to blue
    for i in range(0, 20):
        r = 255 - i * 12
        b = i * 12
        sgr(f'38;2;{r};0;{b}', '\u2588')
    print('  red->blue gradient')
    print()

    print('=== Combined Attributes ===')
    sgr('1;31', 'Bold Red')
    print('  ', end='')
    sgr('1;4;32', 'Bold Underline Green')
    print('  ', end='')
    sgr('3;7;35', 'Italic Reverse Magenta')
    print()

    sys.stdout.flush()

if __name__ == '__main__':
    main()
