#!/usr/bin/env bash
# Comprueba el Fire TV antes de instalar nada.
# Uso: scripts/comprobar_dispositivo.sh <IP_DEL_FIRE_TV>
#
# - Conecta por ADB (puede que tengas que aceptar el aviso en la TV).
# - Muestra modelo, versión de Android (SDK) y de Fire OS.
# - Se detiene si ADB no responde o el dispositivo no es Android (Fire TV Stick 4K Select usa Vega OS).
# - Se detiene si el SDK es menor que 25 (el minSdk del launcher: Fire OS 6 / Android 7.1).
# - Lista los paquetes de launcher. No cambia nada en la TV.
# Guarda el resultado en dispositivo/info.txt (ignorado por git: no lo subas).
set -euo pipefail

IP="${1:-}"
if [ -z "$IP" ]; then
  echo "Uso: $0 <IP_DEL_FIRE_TV>" >&2
  exit 2
fi
command -v adb >/dev/null || { echo "Falta adb (Android platform-tools) en el PATH." >&2; exit 1; }

adb connect "$IP:5555" || true
sleep 2
adb devices
if ! adb -s "$IP:5555" get-state 2>/dev/null | grep -q device; then
  echo
  echo "DETENTE: ADB no responde en $IP:5555."
  echo "Revisa Ajustes > Mi Fire TV > Opciones para desarrolladores > Depuración ADB y acepta el aviso en la TV."
  echo "Si es un Fire TV Stick 4K Select (Vega OS, no Android), esto no sirve."
  exit 1
fi

A=(adb -s "$IP:5555" shell)
SDK="$("${A[@]}" getprop ro.build.version.sdk | tr -d '\r')"
MODELO="$("${A[@]}" getprop ro.product.model | tr -d '\r')"
FIREOS="$("${A[@]}" getprop ro.build.version.name | tr -d '\r')"

if [ -z "$SDK" ] || ! [[ "$SDK" =~ ^[0-9]+$ ]]; then
  echo "DETENTE: el dispositivo no devuelve un SDK de Android. Si es Vega OS, este launcher no sirve."
  exit 1
fi

mkdir -p dispositivo
{
  echo "Modelo: $MODELO"
  echo "SDK de Android: $SDK"
  echo "Versión: $FIREOS"
  echo
  echo "Paquetes de launcher:"
  "${A[@]}" pm list packages | tr -d '\r' | grep -i launcher || echo "(ninguno con 'launcher' en el nombre)"
} | tee dispositivo/info.txt

case "$SDK" in
  25) echo "Fire OS 6 (Android 7.1)." ;;
  28) echo "Fire OS 7 (Android 9)." ;;
  30) echo "Fire OS 8 (Android 11)." ;;
esac
if [ "$SDK" -lt 25 ]; then
  echo "DETENTE: SDK $SDK es menor que 25, el mínimo del launcher (Fire OS 6)."
  exit 1
fi
echo
echo "Todo listo para instalar: scripts/instalar.sh $IP"
