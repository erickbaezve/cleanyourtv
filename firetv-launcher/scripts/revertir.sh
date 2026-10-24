#!/usr/bin/env bash
# Deja el Fire TV como estaba: vuelve a activar el launcher de Amazon y todo lo que se desactivó.
# Uso: scripts/revertir.sh <IP_DEL_FIRE_TV>
set -u

IP="${1:-}"
if [ -z "$IP" ]; then
  echo "Uso: $0 <IP_DEL_FIRE_TV>" >&2
  exit 2
fi
cd "$(dirname "$0")/.."
adb connect "$IP:5555" >/dev/null || true
A=(adb -s "$IP:5555" shell)

"${A[@]}" pm enable com.amazon.tv.launcher
if [ -f bloatware_eliminado.txt ]; then
  while read -r p; do
    [ -n "$p" ] && "${A[@]}" pm enable "$p"
  done < bloatware_eliminado.txt
fi
echo
echo "Paquetes activados de nuevo. El launcher propio sigue instalado; para quitarlo:"
echo "  adb -s $IP:5555 uninstall com.miusuario.tvlauncher"
echo "Reinicia para terminar: adb -s $IP:5555 reboot"
