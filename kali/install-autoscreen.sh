#!/usr/bin/env bash
set -euo pipefail
BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$HOME/.local/bin" "$HOME/.config/autostart"
install -m 755 "$BASE_DIR/ffremote-autoscreen.py" "$HOME/.local/bin/ffremote-autoscreen"
cat > "$HOME/.config/autostart/ffremote-autoscreen.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=FF Remote Lab - Auto Screen
Comment=Show screen of an explicitly approved Android ADB device
Exec=$HOME/.local/bin/ffremote-autoscreen watch
Terminal=false
X-GNOME-Autostart-enabled=true
EOF
echo "Installazione completata. Il monitor parte al prossimo login grafico."
echo "Autorizza il Pixel con:"
echo "  $HOME/.local/bin/ffremote-autoscreen allow 192.168.1.31"
echo "Per avviarlo subito nella sessione Kali:"
echo "  $HOME/.local/bin/ffremote-autoscreen watch"
