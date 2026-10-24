#!/usr/bin/env bash
# Lista las apps del usuario (-3) y del sistema (-s) del Fire TV en una tabla con lo que hace cada paquete conocido.
# Uso: scripts/listar_paquetes.sh <IP_DEL_FIRE_TV>
# No cambia nada. Escribe dispositivo/paquetes.md (ignorado por git).
#
# Clasificación:
#   CRÍTICO     nunca se propone: launcher de Amazon, ajustes, SystemUI, red, Bluetooth, mando, Alexa, OTA, DRM, teclado.
#   CANDIDATO   prescindible para la mayoría (publicidad, telemetría, apps preinstaladas). Aun así, decide tú.
#   REVISAR     no está en la lista o depende del uso. Antes de tocarlo: adb shell dumpsys package <paquete>
set -euo pipefail

IP="${1:-}"
if [ -z "$IP" ]; then
  echo "Uso: $0 <IP_DEL_FIRE_TV>" >&2
  exit 2
fi
cd "$(dirname "$0")/.."
adb connect "$IP:5555" >/dev/null || true
A=(adb -s "$IP:5555" shell)

# paquete|clase|qué hace. Solo paquetes con nombre y función conocidos; lo demás sale como REVISAR.
read -r -d '' CONOCIDOS <<'EOF' || true
com.amazon.tv.launcher|CRÍTICO|Launcher de Amazon (pantalla de inicio y botón Home)
com.amazon.tv.settings.v2|CRÍTICO|Ajustes de Fire TV
com.amazon.tv.settings|CRÍTICO|Ajustes de Fire TV
com.android.systemui|CRÍTICO|Interfaz del sistema
com.android.settings|CRÍTICO|Ajustes de Android
com.android.shell|CRÍTICO|Shell de ADB
com.android.packageinstaller|CRÍTICO|Instalador de paquetes
com.android.permissioncontroller|CRÍTICO|Permisos
com.android.providers.settings|CRÍTICO|Almacén de ajustes
com.amazon.device.software.ota|CRÍTICO|Actualizaciones del sistema (OTA)
com.amazon.device.software.ota.override|CRÍTICO|Actualizaciones del sistema (OTA)
com.amazon.tv.forcedotaupdater.v2|CRÍTICO|Actualizaciones obligatorias del sistema
com.amazon.vizzini|CRÍTICO|Alexa en el Fire TV (voz del mando)
com.amazon.tv.ime|CRÍTICO|Teclado en pantalla
com.amazon.tv.oobe|CRÍTICO|Configuración inicial
com.amazon.tv.btservice|CRÍTICO|Bluetooth y mando
com.android.bluetooth|CRÍTICO|Bluetooth
com.amazon.wifilocker|REVISAR|Guarda las contraseñas Wi-Fi en tu cuenta de Amazon
com.amazon.venezia|REVISAR|Amazon Appstore (también actualiza tus apps)
com.amazon.hedwig|REVISAR|Mensajería de dispositivo de Amazon (notificaciones push)
com.amazon.avod|REVISAR|Prime Video
com.amazon.kso|CANDIDATO|Ofertas especiales y anuncios de Amazon
com.amazon.client.metrics|CANDIDATO|Telemetría de Amazon
com.amazon.client.metrics.api|CANDIDATO|Telemetría de Amazon
com.amazon.device.crashmanager|CANDIDATO|Envío de informes de fallos a Amazon
com.amazon.dp.logger|CANDIDATO|Registro de uso para Amazon
com.amazon.tv.csapp|CANDIDATO|Ayuda y atención al cliente
com.amazon.tv.legal.notices|CANDIDATO|Avisos legales
com.amazon.bueller.photos|CANDIDATO|Amazon Photos
com.amazon.bueller.music|CANDIDATO|Amazon Music
com.amazon.ags.app|CANDIDATO|Amazon GameCircle
com.amazon.imdb.tv.android.app|CANDIDATO|Freevee / IMDb TV
EOF

describir() {
  local pkg="$1" linea
  linea="$(printf '%s\n' "$CONOCIDOS" | grep -F "$pkg|" | head -n1 || true)"
  if [ -n "$linea" ]; then
    printf '%s' "$linea" | awk -F'|' '{print $2"|"$3}'
  elif [[ "$pkg" =~ (launcher|settings|systemui|bluetooth|wifi|network|ota|drm|ime|keyboard|remote|alexa|vizzini|provider|permission|packageinstaller|shell)$ ]] \
    || [[ "$pkg" =~ ^android$|^com\.android\.(phone|providers\.) ]]; then
    printf 'CRÍTICO|Parece del sistema (%s)' "$(sed 's/.*\.//' <<<"$pkg")"
  else
    printf 'REVISAR|Desconocido: mira adb shell dumpsys package %s' "$pkg"
  fi
}

mkdir -p dispositivo
{
  for tipo in 3 s; do
    if [ "$tipo" = 3 ]; then echo "## Apps del usuario (pm list packages -3)"; else echo "## Apps del sistema (pm list packages -s)"; fi
    echo
    echo "| Paquete | Clase | Qué hace |"
    echo "|---|---|---|"
    "${A[@]}" pm list packages -"$tipo" | tr -d '\r' | sed 's/^package://' | sort | while read -r pkg; do
      IFS='|' read -r clase que <<<"$(describir "$pkg")"
      echo "| \`$pkg\` | $clase | $que |"
    done
    echo
  done
} | tee dispositivo/paquetes.md
echo "Tabla guardada en dispositivo/paquetes.md. Elige grupos pequeños y pásalos a scripts/quitar_bloatware.sh."
