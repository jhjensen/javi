# VT100 Terminal Test Programs

Simple test programs that print VT100 escape sequences.
Run in both a real terminal (xterm/gnome-terminal) and Javi shell,
then compare screen output to find conformance issues.

## Usage

```bash
# On rdesk in Docker (for reference terminal):
docker run --rm -it ubuntu bash
# Install: apt-get update && apt-get install -y python3

# Run each test:
python3 test_cursor.py
python3 test_color.py
python3 test_scroll.py
python3 test_erase.py
python3 test_attributes.py
```

## Capture-based comparison

Each test writes predictable output.
Use `script` or `tmux capture-pane` on rdesk to capture reference output.
Then run the same programs in Javi `:vt` and compare visually.
