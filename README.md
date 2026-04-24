# javi

A vi-like text editor written in Java/AWT with terminal emulation, git integration, and plugin support.

## Requirements

- **Java 21** or later (LTS recommended; tested on Java 21 and 25)

## Build

```bash
./gradlew build
```

## Install

```bash
./gradlew installDist
```

This creates `build/install/javi/` with a launcher script at `build/install/javi/bin/javi`.
Add it to your PATH or symlink:

```bash
ln -s "$(pwd)/build/install/javi/bin/javi" ~/.local/bin/javi
```

## Run

```bash
build/install/javi/bin/javi [files...]
```

## Documentation

See [docs/javi_manual.typ](docs/javi_manual.typ) for the full manual (Typst format).

## Optional External Dependencies

These tools enhance javi but are not required:

| Tool | Purpose |
|------|---------|
| `git` | Git integration (status, diff, commit, log) |
| `clang-format` | Code formatting |
| `lid` (idutils) | Cross-reference lookup |

## License

GPL-3.0-or-later — see [LICENSE](LICENSE).
