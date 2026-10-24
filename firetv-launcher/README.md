# Launcher propio para Fire TV 4K

Un launcher para Fire TV (Fire OS 7 y 8) con la rejilla de Apple TV, estilo Liquid Glass, modo claro y nocturno,
carpetas de apps y la barra superior de Fire TV. Sin publicidad, sin recomendaciones, sin permiso de Internet.

> Este proyecto vive en una carpeta aparte de [cleanyourtv](../README.md) y está pensado para moverse a su propio
> repositorio: un launcher nuevo, en Kotlin y para Fire TV, no entra en `launcher/` (ver
> [AGENTS.md](../AGENTS.md), "Where a change goes"). Cuando tenga su repo, se puede añadir una fila en
> [SHOWCASE.md](../SHOWCASE.md), en "Other platforms: Fire TV and LG webOS".

## Qué hace

- **Una sola tarjeta principal** arriba, que cambia con un resorte cada vez que el foco pasa a otra app: banner
  grande (`android:banner`, o el icono si no tiene), nombre y el color dominante de la app. Nunca lee el canal de
  recomendaciones (`TvContract` / Watch Next) de ninguna app.
- **Rejilla de Apple TV**: 6 iconos 5:3 por fila, margen lateral del 5% y separación del 2%, en dp (igual en
  1080p y 4K). Se ve la primera fila completa y asoma la segunda. Todas las apps con `LEANBACK_LAUNCHER`.
- **Foco**: escala 1.2, inclinación parallax según la columna y la dirección del D-pad, borde blanco fino, brillo
  especular, sombra del color de la app y el nombre debajo en una etiqueta de cristal.
- **OK** abre la app. **Mantener OK** (o el botón ☰ del mando) entra en modo edición, con vaivén, y muestra:
  - App: Mover, Ocultar, Crear carpeta, Cancelar.
  - Carpeta: Abrir, Mover, Renombrar, Eliminar carpeta, Cancelar.
- **Carpetas**: en "Crear carpeta" la app se levanta, el D-pad elige otra app (o una carpeta) y OK la suelta encima.
  Nombre automático por categoría ("Streaming", "Juegos", "Música", "Utilidades") o "Carpeta" si no se puede
  deducir. El icono lleva una mini-cuadrícula 3×3; OK abre un panel de cristal que se expande desde el icono y
  vuelve a él al cerrar con Atrás. Dentro: mover, sacar a la rejilla, ocultar, renombrar y eliminar. Una carpeta con
  1 o 0 apps se disuelve. Una app está en un solo sitio y las carpetas no se anidan.
- **Se guarda** en DataStore (JSON): orden, apps ocultas y carpetas (id, nombre, paquetes en orden). Una app
  desinstalada sale de su carpeta; una app nueva va al final.
- **Barra superior estilo Fire TV**: búsqueda (filtra las apps instaladas, también las de dentro de carpetas), reloj,
  engranaje de ajustes rápidos y perfil (abre los Ajustes de Fire TV). Arriba desde la mitad izquierda de la rejilla
  va a la búsqueda; desde la derecha, al engranaje.
- **Temas**: Claro (#F5F5F7), Nocturno (#121212) y Automático (nocturno de 19:00 a 07:00, horas configurables).
  El cambio se funde sin parpadeo. Los colores son tokens (`ColorScheme` de Material 3 + `GlassTokens`), y una
  prueba comprueba contraste de al menos 4.5:1 en los dos temas con colores de app extremos.
- **Home** estando en el launcher cierra paneles y vuelve al principio.

## Liquid Glass sin RenderEffect

Fire OS 7 y 8 no tienen `RenderEffect` ni AGSL, así que no hay desenfoque en vivo. Como el fondo lo dibuja el
launcher (degradado del color de la app enfocada y su banner), se renderiza **una vez por cambio de foco** a un
bitmap de 192×108, se desenfoca con stack blur en Kotlin (sin librerías nativas, vale para sticks de 32 bits) y se
funde con el anterior. Cada superficie de cristal dibuja su recorte de esa copia, alineado con la pantalla y
escalado 1.05 para sugerir refracción, y encima el tinte, el borde con degradado y el brillo especular.

- Como mucho 3 superficies con fondo desenfocado a la vez (búsqueda, texto de la tarjeta principal, etiqueta del
  foco o el panel abierto); el resto son solo translúcidas.
- `CALIDAD_VIDRIO` (Ajustes del launcher): **Alta** (todo), **Media** (sin refracción ni brillo móvil), **Baja**
  (solo translúcido con degradado).
- Si durante una animación los fps bajan de 50 dos veces seguidas, la calidad baja un nivel en esa sesión, y los
  Ajustes lo indican.

Las constantes de diseño están en [`Config.kt`](app/src/main/java/com/miusuario/tvlauncher/Config.kt):
`COLUMNAS = 6`, `RELACION = 5/3`, `ESCALA_FOCO = 1.2`, `RADIO_ESQUINA`, márgenes, inclinación y fps mínimos.

## Compilar

Necesitas JDK 17 o superior y el Android SDK (plataforma 34). Si falta el SDK:

```bash
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk (~2,2 MB)
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # reglas de rejilla y carpetas, JSON, tema automático, contraste y capturas
```

Para el Fire TV usa la **release**: va firmada con la clave debug (se instala igual por ADB) pero Compose está
optimizado; la debug es bastante más lenta en un stick. Las pruebas de capturas renderizan la pantalla real con
apps falsas (Robolectric) y dejan los PNG en `app/build/capturas/`.

## Instalar en el Fire TV

En el Fire TV: Ajustes > Mi Fire TV > Opciones para desarrolladores > **Depuración ADB** y **Apps de origen
desconocido**. El PC tiene que estar en la misma red.

```bash
scripts/comprobar_dispositivo.sh <IP_DEL_FIRE_TV>   # modelo, SDK, Fire OS; se detiene si es Vega OS o SDK < 28
scripts/instalar.sh <IP_DEL_FIRE_TV>                # compila, instala y abre el launcher (no toca nada de Amazon)
```

`DEBUG=1 scripts/instalar.sh ...` instala `app-debug.apk` en su lugar. Para abrirlo a mano:
`adb shell am start -n com.miusuario.tvlauncher/.MainActivity`.

## El botón Home (decide tú)

Amazon no deja elegir otro launcher y bloquea las apps que sustituyen su pantalla de inicio. El launcher de Amazon
**queda activo**: no lo desactives con `pm disable-user` a ciegas, el botón Home puede quedarse en blanco.

- **Opción A (recomendada, sin riesgo)**: una app de terceros que detecta Home y abre este launcher (por ejemplo
  Launcher Manager, de la comunidad AFTVnews). Puede verse un instante la pantalla de Amazon, y puede dejar de
  funcionar tras reposo, reinicios o actualizaciones. Otros proyectos lo hacen con un servicio de accesibilidad propio
  (ver la sección de Fire TV en [SHOWCASE.md](../SHOWCASE.md)).
- **Opción B (invasiva)**: el método de AFTVnews que da privilegios de usuario del sistema para desactivar el
  launcher de Amazon. Es un exploit, puede romper actualizaciones y afectar la garantía. **No está en este proyecto**
  y no se ejecuta sin tu aprobación expresa, después de leer su guía completa.

## Quitar bloatware

```bash
scripts/listar_paquetes.sh <IP_DEL_FIRE_TV>                # tabla en dispositivo/paquetes.md: CRÍTICO / CANDIDATO / REVISAR
scripts/quitar_bloatware.sh <IP_DEL_FIRE_TV> grupo1.txt    # un grupo pequeño, con confirmación escrita
adb -s <IP_DEL_FIRE_TV>:5555 reboot                        # y comprueba que todo funciona antes del siguiente grupo
```

- Antes del primer grupo, comprueba que el launcher abre y lanza apps (el script lo pregunta).
- Los scripts **desactivan** (`pm disable-user --user 0`), uno por llamada, y no desinstalan: así lo exige
  cleanyourtv para cualquier script (sin `pm uninstall`), y se deshace con `pm enable`. Si quieres desinstalar
  igualmente, `adb shell pm uninstall -k --user 0 <paquete>` se deshace con
  `adb shell cmd package install-existing <paquete>`.
- Se niegan a tocar lo crítico: launcher de Amazon, Ajustes, SystemUI, Wi-Fi, Bluetooth, mando, Alexa,
  actualizaciones del sistema, DRM, teclado, servicios de red y de Android. Los paquetes de Amazon dependen unos de
  otros: empieza por lo claramente prescindible (anuncios, telemetría, apps preinstaladas que no usas).
- Lo quitado queda en `bloatware_eliminado.txt` y `restaurar_bloatware.sh <IP>` lo vuelve a activar.
- Las actualizaciones de Fire OS pueden volver a activar o reinstalar algunas apps.

## Deshacerlo todo

```bash
scripts/revertir.sh <IP_DEL_FIRE_TV>              # pm enable del launcher de Amazon y de todo lo desactivado
adb -s <IP_DEL_FIRE_TV>:5555 uninstall com.miusuario.tvlauncher
```

Cuando termines, desactiva la Depuración ADB: el puerto 5555 va sin cifrar.

## Estructura

| Ruta | Qué es |
|---|---|
| `app/src/main/java/.../Config.kt` | Constantes de diseño, `CalidadVidrio`, `ModoTema`, horario automático |
| `.../model/` | Rejilla, carpetas y ocultas (Kotlin puro) y su JSON |
| `.../data/` | Apps instaladas, banners, color dominante (Palette) y DataStore |
| `.../ui/glass/` | Fondo desenfocado, superficie de cristal, stack blur y medidor de fps |
| `.../ui/theme/` | Tokens de color de cada tema y `LauncherTheme` |
| `.../ui/` | Pantalla, rejilla, icono, tarjeta principal, barra superior, carpeta y paneles |
| `app/src/test/` | Pruebas de la rejilla, contraste y capturas de pantalla |
| `scripts/` | Comprobar, instalar, listar y quitar bloatware, revertir |
