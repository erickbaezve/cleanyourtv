#!/usr/bin/env bash
# Desactiva un grupo de paquetes que hayas aprobado, uno por llamada, y deja cómo deshacerlo.
# Uso: scripts/quitar_bloatware.sh <IP_DEL_FIRE_TV> <grupo.txt>
#   grupo.txt: un paquete por línea; las líneas con # se ignoran.
#
# - Solo desactiva (pm disable-user --user 0): sin root, sin desinstalar, se deshace con pm enable.
# - Se niega a tocar paquetes críticos (launcher de Amazon, ajustes, SystemUI, red, Bluetooth, mando, Alexa,
#   OTA, DRM, teclado, servicios de Android) y no hay forma de saltarse esa lista.
# - Pide confirmación escrita antes de cambiar nada.
# - Apunta cada paquete en bloatware_eliminado.txt y regenera restaurar_bloatware.sh.
# Después de cada grupo: reinicia (adb reboot) y comprueba que todo funciona antes del siguiente.
set -euo pipefail

IP="${1:-}"
GRUPO="${2:-}"
if [ -z "$IP" ] || [ -z "$GRUPO" ] || [ ! -f "$GRUPO" ]; then
  echo "Uso: $0 <IP_DEL_FIRE_TV> <grupo.txt>" >&2
  exit 2
fi
cd "$(dirname "$0")/.."
GRUPO_ABS="$(cd "$(dirname "$GRUPO")" && pwd)/$(basename "$GRUPO")"

protegido() {
  local p="$1"
  case "$p" in
    android|com.amazon.tv.launcher|com.amazon.tv.settings|com.amazon.tv.settings.v2|com.android.systemui|\
    com.android.settings|com.android.shell|com.android.phone|com.android.tv.settings|com.android.bluetooth|\
    com.android.packageinstaller|com.android.permissioncontroller|com.amazon.vizzini|com.amazon.tv.ime|\
    com.amazon.tv.oobe|com.amazon.tv.btservice|com.amazon.device.software.ota|com.amazon.device.software.ota.override|\
    com.amazon.tv.forcedotaupdater.v2|com.miusuario.tvlauncher)
      return 0 ;;
  esac
  [[ "$p" =~ ^com\.android\.providers\. ]] && return 0
  [[ "$p" =~ ^com\.google\.android\.(ext|overlay|modulemetadata|permissioncontroller|packageinstaller) ]] && return 0
  [[ "$p" =~ (launcher|systemui|bluetooth|wifi|ota|drm|ime|keyboard|remote|alexa|playready|widevine)$ ]] && return 0
  return 1
}

mapfile -t PAQUETES < <(sed -e 's/#.*//' -e 's/[[:space:]]//g' "$GRUPO_ABS" | grep -v '^$')
if [ "${#PAQUETES[@]}" -eq 0 ]; then
  echo "El grupo está vacío." >&2
  exit 2
fi
for p in "${PAQUETES[@]}"; do
  if protegido "$p"; then
    echo "NO: $p es crítico para el Fire TV. Quítalo del grupo; no se ha cambiado nada." >&2
    exit 1
  fi
done

adb connect "$IP:5555" >/dev/null || true
A=(adb -s "$IP:5555" shell)

echo "Antes de quitar nada, el launcher debe abrir bien y lanzar apps (scripts/instalar.sh)."
read -r -p "¿Lo has comprobado en la TV? (si/no) " ok
[ "$ok" = "si" ] || { echo "Primero compruébalo. No se ha cambiado nada."; exit 1; }

echo "Se van a DESACTIVAR (reversible con pm enable):"
printf '  %s\n' "${PAQUETES[@]}"
read -r -p "Escribe SI para continuar: " confirmar
[ "$confirmar" = "SI" ] || { echo "Cancelado. No se ha cambiado nada."; exit 1; }

touch bloatware_eliminado.txt
for p in "${PAQUETES[@]}"; do
  if ! "${A[@]}" pm path "$p" 2>/dev/null | grep -q package:; then
    echo "  $p no está instalado, se omite."
    continue
  fi
  if salida="$("${A[@]}" pm disable-user --user 0 "$p" 2>&1)" && grep -qi "disabled" <<<"$salida"; then
    echo "  desactivado: $p"
    grep -qxF "$p" bloatware_eliminado.txt || echo "$p" >> bloatware_eliminado.txt
  else
    echo "  NO se pudo desactivar $p: $salida"
  fi
done

{
  echo "#!/usr/bin/env bash"
  echo "# Generado por scripts/quitar_bloatware.sh: vuelve a activar todo lo de bloatware_eliminado.txt."
  echo "# Uso: ./restaurar_bloatware.sh <IP_DEL_FIRE_TV>"
  echo 'set -u; IP="${1:?Uso: $0 <IP_DEL_FIRE_TV>}"; adb connect "$IP:5555" >/dev/null || true'
  while read -r p; do
    [ -n "$p" ] && echo "adb -s \"\$IP:5555\" shell pm enable $p"
  done < bloatware_eliminado.txt
} > restaurar_bloatware.sh
chmod +x restaurar_bloatware.sh

echo
echo "Hecho. Lista en bloatware_eliminado.txt; para deshacer: ./restaurar_bloatware.sh $IP"
echo "Ahora reinicia (adb -s $IP:5555 reboot) y comprueba que la TV, el mando, el Wi-Fi y tus apps funcionan"
echo "antes de pasar al siguiente grupo. Las actualizaciones de Fire OS pueden volver a activar algunas apps."
