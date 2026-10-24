#!/usr/bin/env bash
# Compila e instala el launcher en el Fire TV y lo abre directamente (sin tocar el launcher de Amazon).
# Uso: scripts/instalar.sh <IP_DEL_FIRE_TV>            compila la versión release (rápida, firmada con la clave debug)
#      DEBUG=1 scripts/instalar.sh <IP_DEL_FIRE_TV>    compila app-debug.apk (más lenta: Compose sin optimizar)
#
# No desactiva ningún paquete. El botón Home sigue llevando a Amazon hasta que elijas una opción (README).
set -euo pipefail

IP="${1:-}"
if [ -z "$IP" ]; then
  echo "Uso: $0 <IP_DEL_FIRE_TV>" >&2
  exit 2
fi
cd "$(dirname "$0")/.."

if [ "${DEBUG:-0}" = "1" ]; then
  ./gradlew assembleDebug
  APK=app/build/outputs/apk/debug/app-debug.apk
else
  ./gradlew assembleRelease
  APK=app/build/outputs/apk/release/app-release.apk
fi

adb connect "$IP:5555" >/dev/null || true
adb -s "$IP:5555" install -r "$APK"
adb -s "$IP:5555" shell am start -n com.miusuario.tvlauncher/.MainActivity
echo
echo "Instalado y abierto. Comprueba que se ve bien, que el D-pad se mueve y que las apps se abren."
echo "Para volver a abrirlo: adb -s $IP:5555 shell am start -n com.miusuario.tvlauncher/.MainActivity"
