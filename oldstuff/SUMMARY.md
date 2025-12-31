# Oldstuff Directory Summary

This directory contains legacy code that is no longer part of the main build. These files represent earlier implementations and experiments that were superseded or abandoned.

## Deprecated UI Components

- **aldialog.java** - Old dialog implementation
- **myCheckbox.java** - Custom checkbox component
- **mycomp.java** - Custom component base class
- **tcomp.java** - Test component
- **propedit.java** - Property editor

## Legacy Utilities

- **CharFifo.java** - Character FIFO buffer (uses deprecated StringBuffer)
- **Chunk.java** - Chunk data structure
- **RanFileArray.java** - Random access file array
- **Result.java** - Result wrapper class

## Obsolete Features

- **Browser.java** - Browser component
- **FileProperties.java** - Old file properties (newer version in main)
- **html.java** - HTML handling
- **htmlv.java** - HTML viewer

## Testing/Development

- **JDebugger.java** - Java debugger integration
- **JlintRunner.java** - JLint integration (uses deprecated Hashtable, StringBuffer)
- **listTest.java** - List testing
- **crusttest.java** - Crust testing
- **test.java** - General test code

## Version Control Integration

- **pvcs.java** - PVCS version control integration (uses deprecated Hashtable, StringBuffer)
- **vcs.java** - Version control system base (uses deprecated Hashtable, StringBuffer)

## Text Layout

- **TabbedTextLayout.java** - Tabbed text layout manager
- **TabbedTextLayoutTest.java** - Tests for TabbedTextLayout

## I/O Components

- **bashio.java** - Bash I/O integration
- **HackIo.java** - Experimental I/O

## Other

- **Regexp.java** - Regular expression utilities
- **Task.java** - Task abstraction

## Remote Package (oldstuff/remote/)

- **Compute.java** - Remote computation interface
- **RtestClient.java** - Remote test client

## Configuration Files

- **crust.cfg** - Crust configuration
- **crustq** - Crust query script
- **uncrus** - Uncrust script
- **vi.bat** - Windows batch file for vi
- **vi.cpp** - C++ vi wrapper

## Notes

- Most files use deprecated Java APIs (Hashtable, StringBuffer, Vector)
- These represent ~10+ years of legacy code
- Some may be useful as reference for understanding original design decisions
- Consider archiving or removing if not needed for historical reference
