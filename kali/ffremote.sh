#!/usr/bin/env bash
set -euo pipefail
APP=it.fflab.remote
ACTION=it.fflab.remote.COMMAND
if ! command -v adb >/dev/null 2>&1; then echo 'Installa adb prima.' >&2; exit 1; fi
case "${1:-}" in
  --list) adb devices -l; exit 0 ;;
  --mdns) adb mdns services; exit 0 ;;
  --connect) adb connect "${2:?specifica IP:PORTA}"; exit 0 ;;
esac
SERIAL="${ANDROID_SERIAL:-}"
if [[ "${1:-}" == "--device" ]]; then SERIAL="${2:?seriale mancante}"; shift 2; fi
CMD="${1:-}"; shift || true
CMD="${CMD^^}"
case "$CMD" in
  PING|STATUS|VIBRATE|BEEP|STOP)
    [[ $# -eq 0 ]] || exit 2 ;;
  MESSAGE)
    [[ $# -gt 0 ]] || { echo 'Testo mancante'; exit 2; }
    MSG="$*"
    [[ ${#MSG} -le 120 ]] || { echo 'Massimo 120 caratteri'; exit 2; } ;;
  *) echo 'Uso: ffremote.sh [--device SERIAL] ping|status|vibrate|beep|stop|message TESTO'; exit 2 ;;
esac
if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVICES < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
  [[ ${#DEVICES[@]} -eq 1 ]] || { adb devices -l; echo 'Seleziona con --device SERIAL'; exit 1; }
  SERIAL="${DEVICES[0]}"
fi
adb -s "$SERIAL" get-state | grep -qx device
adb -s "$SERIAL" shell pm path "$APP" | grep -q package:
ARGS=(shell am broadcast -a "$ACTION" -p "$APP" --es ff.command "$CMD")
if [[ "$CMD" == "MESSAGE" ]]; then
  ENCODED="$(printf '%s' "$MSG" | base64 | tr -d '\r\n')"
  ARGS+=(--es ff.text64 "$ENCODED")
fi
adb -s "$SERIAL" "${ARGS[@]}"
echo "Comando inviato. Controlla il registro nell'app sul telefono."
