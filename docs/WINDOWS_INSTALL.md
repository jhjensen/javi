# Installing Javi on Windows 11

A native Windows installation guide — no WSL or Cygwin required.

## Prerequisites

### Java 21 (or later)

Javi requires Java 21+.
Download and install one of:

- **Eclipse Temurin 21** (recommended): https://adoptium.net/temurin/releases/?os=windows&arch=x64&package=jdk&version=21
- **Oracle JDK 21**: https://www.oracle.com/java/technologies/downloads/#jdk21-windows

During installation, select the option to set `JAVA_HOME` and add Java to `PATH`.

Verify from PowerShell:

```powershell
java -version
# Should show version 21.x or later
```

If `java` is not found, add it manually:

```powershell
# Example for Temurin default install location
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Eclipse Adoptium\jdk-21.0.x-hotspot", "User")
[Environment]::SetEnvironmentVariable("Path", "$env:Path;$env:JAVA_HOME\bin", "User")
```

Restart your terminal after setting environment variables.

## Building

Open PowerShell in the javi source directory.

```powershell
.\gradlew.bat installDist
```

This downloads Gradle 8.14 automatically (first run only), compiles javi, and creates a distribution at:

```
build\install\javi\
├── bin\
│   ├── javi        # bash launcher (ignore)
│   └── javi.bat    # Windows launcher
└── lib\
    └── *.jar       # All dependency JARs
```

### Other Useful Build Commands

```powershell
.\gradlew.bat build          # Full build + tests
.\gradlew.bat test           # Run JUnit tests only
.\gradlew.bat shadowJar      # Single fat JAR (build\libs\javi-all.jar)
.\gradlew.bat clean          # Remove build artifacts
```

## Running

### From the Build Tree

```powershell
.\build\install\javi\bin\javi.bat [files...]
```

### Adding to PATH (Permanent)

```powershell
# Add javi to your user PATH (adjust path to your clone location)
$javibin = "C:\Users\YourName\javi\build\install\javi\bin"
[Environment]::SetEnvironmentVariable("Path", "$env:Path;$javibin", "User")
```

After restarting your terminal:

```powershell
javi myfile.txt
```

### Using the Fat JAR

Alternatively, run the fat JAR directly:

```powershell
java -jar build\libs\javi-all.jar [files...]
```

## Configuration (.javini)

Javi reads a `.javini` file from the current working directory on startup.
Create it in your home directory or project directory:

```powershell
# Create .javini in your home directory
notepad $env:USERPROFILE\.javini
```

Example `.javini` contents:

```
# Font settings (Windows fonts)
fontname Consolas
fontsize 14.0
fontweight 1.0

# Window height in lines
lines 50

# Load git integration
loadclass javi.git.GitCommands
```

### Font Recommendations

Windows fonts that work well with javi (monospace, good Unicode coverage):

| Font | Notes |
|------|-------|
| Consolas | Built-in, excellent for code |
| Cascadia Code | Microsoft's modern monospace (install from GitHub) |
| JetBrains Mono | Free, ligature support |
| DejaVu Sans Mono | Good Unicode coverage |

Set the font in `.javini`:

```
fontname Cascadia Code
fontsize 13.0
```

## Windows-Specific Notes

### Terminal vs GUI

Javi is a graphical AWT application — it opens its own window.
It does NOT run inside a terminal (cmd/PowerShell).
Double-clicking `javi.bat` or running it from a terminal both work.

### File Associations

To open files with javi from Explorer, create a file association:

```powershell
# Associate .txt files (example — adjust extensions as desired)
cmd /c assoc .txt=javifile
cmd /c ftype javifile="C:\Users\YourName\javi\build\install\javi\bin\javi.bat" "%1"
```

### Path Separators

Javi handles both `/` and `\` in file paths.
You can use forward slashes in commands: `:e src/main/java/javi/Javi.java`

### Line Endings

Javi reads both LF and CRLF files.
Configure git to keep LF in the repository:

```powershell
git config --global core.autocrlf input
```

### High-DPI Displays

Java AWT should handle DPI scaling automatically on Windows 11.
If text appears too small, increase `fontsize` in `.javini` or set the JVM flag:

```powershell
# In javi.bat or as JAVA_OPTS
set JAVA_OPTS=-Dsun.java2d.uiScale=1.5
```

## Optional Tools

These enhance javi but are not required:

| Tool | Purpose | Install |
|------|---------|---------|
| Git | Git integration (status, diff, commit) | https://git-scm.com/download/win |
| clang-format | Code formatting | `winget install LLVM.LLVM` |

## Troubleshooting

### "JAVA_HOME is not set"

The Gradle wrapper needs `JAVA_HOME` or `java` on PATH.
Set it as described in the Prerequisites section.

### Window Does Not Appear

Ensure you are not running in a headless environment.
Remote Desktop and standard Windows desktop both work fine.
If using SSH, you need X11 forwarding or a VNC session (rare on Windows).

### Fonts Look Wrong

If the configured font is not installed, Java falls back to a default.
Check available monospace fonts in Settings → Personalization → Fonts.
Use an installed font name exactly as shown there.

### Slow Startup on First Run

The first `gradlew.bat` invocation downloads Gradle and all dependencies.
Subsequent builds are fast (cached in `%USERPROFILE%\.gradle\`).

### Antivirus Blocking

Some antivirus software flags new Java applications.
If javi fails to start, check your antivirus quarantine/logs and add an exception for the javi directory.
