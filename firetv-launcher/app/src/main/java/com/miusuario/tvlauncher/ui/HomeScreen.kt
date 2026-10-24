package com.miusuario.tvlauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.miusuario.tvlauncher.CalidadVidrio
import com.miusuario.tvlauncher.Config
import com.miusuario.tvlauncher.LauncherViewModel
import com.miusuario.tvlauncher.ModoTema
import com.miusuario.tvlauncher.esNoche
import com.miusuario.tvlauncher.model.AppEntry
import com.miusuario.tvlauncher.model.FolderEntry
import com.miusuario.tvlauncher.model.GridEntry
import com.miusuario.tvlauncher.model.LayoutState
import com.miusuario.tvlauncher.ui.glass.BackdropState
import com.miusuario.tvlauncher.ui.glass.FpsMonitor
import com.miusuario.tvlauncher.ui.glass.LocalBackdrop
import com.miusuario.tvlauncher.ui.glass.LocalCalidad
import com.miusuario.tvlauncher.ui.glass.LocalAtenuacion
import com.miusuario.tvlauncher.ui.glass.LocalEspecular
import com.miusuario.tvlauncher.ui.theme.LauncherTheme
import com.miusuario.tvlauncher.ui.theme.LocalGlass
import com.miusuario.tvlauncher.ui.theme.Paleta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

/** Modo de la rejilla principal. */
private enum class Modo { NORMAL, MOVER, DESTINO }

/** Qué menú de edición está abierto. */
private sealed interface Menu {
    data class Rejilla(val indice: Int) : Menu
    data class EnCarpeta(val folderId: String, val indice: Int) : Menu
}

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val ajustes by vm.ajustes.collectAsState()
    val hora by produceState(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        while (true) {
            value = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            delay(60_000)
        }
    }
    val dark = when (ajustes.modoTema) {
        ModoTema.CLARO -> false
        ModoTema.NOCTURNO -> true
        ModoTema.AUTOMATICO -> esNoche(hora, ajustes.nocheInicio, ajustes.nocheFin)
    }
    // La calidad elegida puede rebajarse en esta sesión si los fps caen por debajo de 50.
    val calidad = remember(ajustes.calidad) { mutableStateOf(ajustes.calidad) }
    val fps = remember { FpsMonitor { calidad.value = calidad.value.inferior() } }
    val backdrop = remember { BackdropState() }
    val scope = rememberCoroutineScope()
    val parallax = remember { Parallax(scope) }

    LauncherTheme(dark) {
        CompositionLocalProvider(
            LocalCalidad provides calidad.value,
            LocalBackdrop provides backdrop,
            LocalEspecular provides parallax.especular,
        ) {
            Pantalla(vm, calidad.value, fps, backdrop, parallax)
        }
    }
}

@Composable
private fun Pantalla(
    vm: LauncherViewModel,
    calidad: CalidadVidrio,
    fps: FpsMonitor,
    backdrop: BackdropState,
    parallax: Parallax,
) {
    val apps by vm.apps.collectAsState()
    val layout by vm.layout.collectAsState()
    val ajustes by vm.ajustes.collectAsState()
    val tokens = LocalGlass.current

    val rejillaFR = remember { FocusRequester() }
    val buscarFR = remember { FocusRequester() }
    val ajustesFR = remember { FocusRequester() }
    val carpetaFR = remember { FocusRequester() }
    val heroFR = remember { FocusRequester() }
    val estadoRejilla = rememberLazyGridState()

    var foco by remember { mutableIntStateOf(0) }
    var focoEnRejilla by remember { mutableStateOf(false) }
    var modo by remember { mutableStateOf(Modo.NORMAL) }
    var origen by remember { mutableIntStateOf(0) }
    var destino by remember { mutableIntStateOf(0) }
    var copia by remember { mutableStateOf<LayoutState?>(null) }
    var okLargo by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Menu?>(null) }
    var renombrar by remember { mutableStateOf<String?>(null) }
    var ajustesAbiertos by remember { mutableStateOf(false) }
    var buscando by remember { mutableStateOf(false) }
    var consulta by remember { mutableStateOf("") }
    var rectFoco by remember { mutableStateOf(Rect.Zero) }
    var info by remember { mutableStateOf<GridEntry?>(null) }

    var carpeta by remember { mutableStateOf<String?>(null) }
    var carpetaOrigen by remember { mutableStateOf(Rect.Zero) }
    var carpetaCerrando by remember { mutableStateOf(false) }
    var focoCarpeta by remember { mutableIntStateOf(0) }
    var moviendoEnCarpeta by remember { mutableStateOf(false) }

    // Con búsqueda, la rejilla muestra las apps que coinciden (también las de dentro de carpetas), sin edición.
    val entradas: List<GridEntry> = if (consulta.isBlank()) {
        layout.entries
    } else {
        apps.values
            .filter { it.pkg !in layout.hidden && it.label.contains(consulta.trim(), ignoreCase = true) }
            .sortedBy { it.label.lowercase() }
            .map { AppEntry(it.pkg) }
    }
    val f = foco.coerceIn(0, maxOf(0, entradas.size - 1))
    val carpetaAbierta = carpeta?.let { layout.folder(it) }

    // Si la carpeta abierta se disuelve (queda con 1 app) o se elimina, el panel se cierra.
    LaunchedEffect(carpeta, carpetaAbierta == null) {
        if (carpeta != null && carpetaAbierta == null) {
            carpeta = null
            moviendoEnCarpeta = false
            rejillaFR.requestFocus()
        }
    }

    val enfocada: GridEntry? = when {
        carpetaAbierta != null -> carpetaAbierta
        else -> entradas.getOrNull(f)
    }

    // Fondo: se renderiza y desenfoca una vez por cambio de foco, nunca por fotograma.
    val paleta = if (tokens.dark) Paleta.NOCTURNO else Paleta.CLARO
    val colorFoco = Color(Paleta.colorFondo((enfocada?.color(apps) ?: tokens.acento).toArgb().toLong() and 0xFFFFFFFFL, paleta))
    LaunchedEffect(enfocada?.id, tokens.dark, calidad, colorFoco) {
        delay(90)
        val p = paleta
        val banner = (enfocada as? AppEntry)?.let { apps[it.pkg]?.banner }
        val capa = withContext(Dispatchers.Default) {
            BackdropState.renderizar(Color(p.fondo), colorFoco, banner, calidad)
        }
        if (capa != null) backdrop.mostrar(capa) else backdrop.limpiar()
    }

    LaunchedEffect(Unit) {
        vm.home.collect {
            menu = null
            renombrar = null
            ajustesAbiertos = false
            info = null
            carpeta = null
            moviendoEnCarpeta = false
            copia?.let { vm.vista(it) }
            copia = null
            modo = Modo.NORMAL
            buscando = false
            consulta = ""
            foco = 0
            estadoRejilla.animateScrollToItem(0)
            rejillaFR.requestFocus()
        }
    }

    fun empezarEdicion(nuevo: Modo) {
        copia = layout
        origen = f
        modo = nuevo
        if (nuevo == Modo.DESTINO) destino = if (f + 1 < entradas.size) f + 1 else maxOf(0, f - 1)
        menu = null
        rejillaFR.requestFocus()
    }

    fun abrirCarpeta(folder: FolderEntry) {
        carpetaOrigen = rectFoco
        carpeta = folder.folderId
        carpetaCerrando = false
        focoCarpeta = 0
    }

    fun teclaOk(e: KeyEvent, alLargo: () -> Unit, alCorto: () -> Unit): Boolean {
        when (e.type) {
            KeyEventType.KeyDown -> {
                if (e.nativeKeyEvent.repeatCount == 0) {
                    okLargo = false
                } else if (!okLargo) {
                    okLargo = true
                }
            }
            KeyEventType.KeyUp -> {
                // El menú se abre al soltar, para que la misma pulsación no active su primera opción.
                if (okLargo) alLargo() else alCorto()
                okLargo = false
            }
        }
        return true
    }

    fun teclaRejilla(e: KeyEvent): Boolean {
        val edicionPermitida = consulta.isBlank()
        if (e.key.esOk()) {
            return teclaOk(
                e,
                alLargo = { if (modo == Modo.NORMAL && edicionPermitida && entradas.isNotEmpty()) menu = Menu.Rejilla(f) },
                alCorto = {
                    when (modo) {
                        Modo.NORMAL -> when (val entry = entradas.getOrNull(f)) {
                            is AppEntry -> vm.launch(entry.pkg)
                            is FolderEntry -> abrirCarpeta(entry)
                            null -> Unit
                        }
                        Modo.MOVER -> {
                            vm.guardar()
                            copia = null
                            modo = Modo.NORMAL
                        }
                        Modo.DESTINO -> {
                            val antes = layout
                            vm.cambiar { it.dropOnto(origen, destino, vm::categoria) }
                            if (vm.layout.value != antes) foco = if (origen < destino) destino - 1 else destino
                            copia = null
                            modo = Modo.NORMAL
                        }
                    }
                },
            )
        }
        if (e.key == Key.Menu && e.type == KeyEventType.KeyUp && modo == Modo.NORMAL && edicionPermitida && entradas.isNotEmpty()) {
            menu = Menu.Rejilla(f)
            return true
        }
        if (e.type != KeyEventType.KeyDown) return false
        val actual = if (modo == Modo.DESTINO) destino else f
        val paso = navegar(e.key, actual, entradas.size, Config.COLUMNAS) ?: return false
        fps.medir()
        when (paso) {
            is Paso.A -> {
                val dx = (paso.indice % Config.COLUMNAS) - (actual % Config.COLUMNAS)
                val dy = (paso.indice / Config.COLUMNAS) - (actual / Config.COLUMNAS)
                parallax.enfocar(paso.indice % Config.COLUMNAS, Config.COLUMNAS, dx.coerceIn(-1, 1), dy.coerceIn(-1, 1))
                when (modo) {
                    Modo.NORMAL -> foco = paso.indice
                    Modo.MOVER -> {
                        vm.vista(layout.move(f, paso.indice))
                        foco = paso.indice
                    }
                    Modo.DESTINO -> destino = if (paso.indice == origen) {
                        // Se salta el propio icono que se está soltando.
                        navegar(e.key, paso.indice, entradas.size, Config.COLUMNAS).let { (it as? Paso.A)?.indice ?: destino }
                    } else {
                        paso.indice
                    }
                }
            }
            // Arriba desde el Dock: a los botones de la tarjeta principal.
            Paso.Arriba -> if (modo == Modo.NORMAL) heroFR.requestFocus()
            Paso.Borde -> Unit
        }
        return true
    }

    fun teclaCarpeta(e: KeyEvent): Boolean {
        val folder = carpetaAbierta ?: return false
        if (carpetaCerrando) return true
        if (e.key.esOk()) {
            return teclaOk(
                e,
                alLargo = { if (!moviendoEnCarpeta) menu = Menu.EnCarpeta(folder.folderId, focoCarpeta) },
                alCorto = {
                    if (moviendoEnCarpeta) {
                        vm.guardar()
                        copia = null
                        moviendoEnCarpeta = false
                    } else {
                        folder.apps.getOrNull(focoCarpeta)?.let { vm.launch(it) }
                    }
                },
            )
        }
        if (e.key == Key.Menu && e.type == KeyEventType.KeyUp && !moviendoEnCarpeta) {
            menu = Menu.EnCarpeta(folder.folderId, focoCarpeta)
            return true
        }
        if (e.type != KeyEventType.KeyDown) return false
        val paso = navegar(e.key, focoCarpeta, folder.apps.size, COLUMNAS_CARPETA) ?: return false
        fps.medir()
        if (paso is Paso.A) {
            val dx = (paso.indice % COLUMNAS_CARPETA) - (focoCarpeta % COLUMNAS_CARPETA)
            val dy = (paso.indice / COLUMNAS_CARPETA) - (focoCarpeta / COLUMNAS_CARPETA)
            parallax.enfocar(paso.indice % COLUMNAS_CARPETA, COLUMNAS_CARPETA, dx.coerceIn(-1, 1), dy.coerceIn(-1, 1))
            if (moviendoEnCarpeta) vm.vista(layout.moveInFolder(folder.folderId, focoCarpeta, paso.indice))
            focoCarpeta = paso.indice
        }
        return true
    }

    // Atrás, de la capa más alta a la más baja. Menús, ajustes y diálogos registran el suyo.
    BackHandler(enabled = modo != Modo.NORMAL && carpeta == null) {
        copia?.let { vm.vista(it) }
        copia = null
        foco = origen
        modo = Modo.NORMAL
    }
    BackHandler(enabled = carpeta != null && !carpetaCerrando) {
        if (moviendoEnCarpeta) {
            copia?.let { vm.vista(it) }
            copia = null
            moviendoEnCarpeta = false
        } else {
            carpetaCerrando = true
        }
    }
    BackHandler(enabled = carpeta == null && modo == Modo.NORMAL && (consulta.isNotEmpty() || buscando)) {
        consulta = ""
        buscando = false
        foco = 0
        rejillaFR.requestFocus()
    }

    // Con el foco por debajo del Dock, la página sube y el fondo se apaga (como al bajar por la rejilla de Apple TV).
    val desplazado = f >= Config.DOCK && entradas.size > Config.DOCK && carpeta == null
    val avance = animateFloatAsState(
        if (desplazado) 1f else 0f,
        spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
        label = "desplazamiento",
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onSizeChanged { backdrop.pantalla = it },
    ) {
        // Tamaños: margen ≈ 5%, separación ≈ 2%, 6 iconos 5:3 por fila. En dp, igual en 1080p y 4K.
        // Todo (barra, información, Dock y rejilla) empieza en el mismo margen izquierdo.
        val margen = maxWidth * Config.MARGEN_LATERAL
        val separacion = maxWidth * Config.SEPARACION
        val anchoIcono = (maxWidth - margen * 2 - separacion * (Config.COLUMNAS - 1)) / Config.COLUMNAS
        val altoIcono = anchoIcono / Config.RELACION
        val altoBarra = 64.dp
        val altoDock = altoIcono + RELLENO_DOCK * 2
        val separacionDock = 20.dp
        val rellenoRejilla = altoIcono * ((Config.ESCALA_FOCO - 1f) / 2f) + 8.dp
        // Bajo el Dock asoma la siguiente fila.
        val asoma = separacionDock + rellenoRejilla + altoIcono * 0.32f
        val altoHero = maxHeight - asoma - altoDock - altoBarra
        val altoRejilla = maxHeight - altoBarra - altoDock - separacionDock

        val vaiven = modo != Modo.NORMAL || moviendoEnCarpeta || okLargo || menu != null
        val capaEncima = carpeta != null || menu != null || renombrar != null || ajustesAbiertos || info != null
        val levantado = when (modo) {
            Modo.MOVER -> f
            Modo.DESTINO -> origen
            Modo.NORMAL -> null
        }
        val mostrarFoco = (focoEnRejilla || menu is Menu.Rejilla) && carpeta == null

        CompositionLocalProvider(LocalAtenuacion provides avance) {
            FondoHero(enfocada, apps, avance.value, vm::heroBanner)

            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = -avance.value * altoHero.toPx() },
            ) {
                Box(Modifier.padding(top = altoBarra).fillMaxWidth().height(altoHero)) {
                    InfoHero(
                        entry = enfocada,
                        apps = apps,
                        abrirFR = heroFR,
                        arriba = buscarFR,
                        abajo = rejillaFR,
                        alAbrir = {
                            when (val e = entradas.getOrNull(f)) {
                                is AppEntry -> vm.launch(e.pkg)
                                is FolderEntry -> abrirCarpeta(e)
                                null -> Unit
                            }
                        },
                        alInfo = { info = entradas.getOrNull(f) },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = margen, bottom = 26.dp)
                            .fillMaxWidth(0.5f)
                            .graphicsLayer { alpha = 1f - avance.value },
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(rejillaFR)
                        .onFocusChanged {
                            focoEnRejilla = it.isFocused
                            if (it.isFocused) buscando = false
                        }
                        .onKeyEvent { teclaRejilla(it) }
                        .focusable(),
                ) {
                    Dock(
                        entradas = entradas,
                        apps = apps,
                        anchoIcono = anchoIcono,
                        separacion = separacion,
                        margen = margen,
                        foco = f,
                        destino = if (modo == Modo.DESTINO) destino else null,
                        levantado = levantado,
                        mostrarFoco = mostrarFoco,
                        vaiven = vaiven && carpeta == null,
                        inclinacion = parallax.inclinacion,
                        etiquetaConFondo = !capaEncima,
                        alPosicionarFoco = { rectFoco = it },
                    )
                    Box(Modifier.padding(top = separacionDock).fillMaxWidth().height(altoRejilla)) {
                        if (entradas.isEmpty()) {
                            Text(
                                if (consulta.isBlank()) "Cargando apps…" else "Ninguna app coincide con \"$consulta\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = tokens.textoSecundario,
                                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                            )
                        }
                        RejillaIconos(
                            entradas = entradas.drop(Config.DOCK),
                            apps = apps,
                            columnas = Config.COLUMNAS,
                            anchoIcono = anchoIcono,
                            separacion = separacion,
                            margen = margen,
                            foco = f,
                            destino = if (modo == Modo.DESTINO) destino else null,
                            levantado = levantado,
                            mostrarFoco = mostrarFoco,
                            vaiven = vaiven && carpeta == null,
                            inclinacion = parallax.inclinacion,
                            etiquetaConFondo = false,
                            estado = estadoRejilla,
                            altoVisible = altoRejilla,
                            alPosicionarFoco = { rectFoco = it },
                            inicio = Config.DOCK,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            TopBar(
                margen = margen,
                buscando = buscando,
                consulta = consulta,
                alConsultar = {
                    consulta = it
                    foco = 0
                },
                alActivarBusqueda = { buscando = true },
                buscarFR = buscarFR,
                ajustesFR = ajustesFR,
                rejillaFR = heroFR,
                alAjustes = { ajustesAbiertos = true },
                alPerfil = { vm.repo.openSystemSettings() },
                pildoraConFondo = true,
            )
        }

        // Foco inicial en la rejilla. Va aquí dentro porque BoxWithConstraints compone su contenido durante el layout.
        LaunchedEffect(Unit) { rejillaFR.requestFocus() }

        if (carpeta != null && carpetaAbierta != null) {
            FolderPanel(
                folder = carpetaAbierta,
                apps = apps,
                origen = carpetaOrigen,
                cerrando = carpetaCerrando,
                anchoIcono = anchoIcono,
                separacion = separacion,
                foco = focoCarpeta.coerceIn(0, maxOf(0, carpetaAbierta.apps.size - 1)),
                levantado = if (moviendoEnCarpeta) focoCarpeta else null,
                vaiven = moviendoEnCarpeta || okLargo || menu is Menu.EnCarpeta,
                inclinacion = parallax.inclinacion,
                focusRequester = carpetaFR,
                alTecla = { teclaCarpeta(it) },
                alCerrado = {
                    carpeta = null
                    carpetaCerrando = false
                    rejillaFR.requestFocus()
                },
            )
        }

        when (val m = menu) {
            is Menu.Rejilla -> {
                val entry = entradas.getOrNull(m.indice)
                val cerrar = {
                    menu = null
                    rejillaFR.requestFocus()
                }
                // Anclar al Dock = pasar a la primera fila (al final); quitar = primera posición fuera del Dock.
                fun anclar(i: Int): Pair<String, () -> Unit>? = when {
                    entradas.size <= Config.DOCK -> null
                    i < Config.DOCK -> "Quitar del Dock" to {
                        vm.cambiar { it.move(i, Config.DOCK) }
                        foco = Config.DOCK
                        cerrar()
                    }
                    else -> "Anclar al Dock" to {
                        vm.cambiar { it.move(i, Config.DOCK - 1) }
                        foco = Config.DOCK - 1
                        cerrar()
                    }
                }
                val opciones = when (entry) {
                    is AppEntry -> listOfNotNull(
                        "Mover" to { empezarEdicion(Modo.MOVER) },
                        anclar(m.indice),
                        "Ocultar" to {
                            vm.cambiar { it.hide(entry.pkg) }
                            cerrar()
                        },
                        "Crear carpeta" to { empezarEdicion(Modo.DESTINO) },
                        "Cancelar" to cerrar,
                    )
                    is FolderEntry -> listOfNotNull(
                        "Abrir" to {
                            menu = null
                            abrirCarpeta(entry)
                        },
                        "Mover" to { empezarEdicion(Modo.MOVER) },
                        anclar(m.indice),
                        "Renombrar" to {
                            menu = null
                            renombrar = entry.folderId
                        },
                        "Eliminar carpeta" to {
                            vm.cambiar { it.deleteFolder(entry.folderId) }
                            cerrar()
                        },
                        "Cancelar" to cerrar,
                    )
                    null -> listOf("Cancelar" to cerrar)
                }
                val titulo = entry?.titulo(apps) ?: ""
                MenuEdicion(
                    titulo,
                    opciones,
                    conFondo = true,
                    alCerrar = cerrar,
                )
            }
            is Menu.EnCarpeta -> {
                val folder = layout.folder(m.folderId)
                val pkg = folder?.apps?.getOrNull(m.indice)
                val cerrar = {
                    menu = null
                    carpetaFR.requestFocus()
                }
                val opciones = buildList {
                    if (pkg != null) {
                        add("Mover" to {
                            copia = layout
                            moviendoEnCarpeta = true
                            cerrar()
                        })
                        add("Sacar a la rejilla" to {
                            vm.cambiar { it.takeOutOfFolder(m.folderId, pkg) }
                            focoCarpeta = maxOf(0, focoCarpeta - 1)
                            cerrar()
                        })
                        add("Ocultar" to {
                            vm.cambiar { it.hide(pkg) }
                            focoCarpeta = maxOf(0, focoCarpeta - 1)
                            cerrar()
                        })
                    }
                    add("Renombrar carpeta" to {
                        menu = null
                        renombrar = m.folderId
                    })
                    add("Eliminar carpeta" to {
                        menu = null
                        vm.cambiar { it.deleteFolder(m.folderId) }
                    })
                    add("Cancelar" to cerrar)
                }
                MenuEdicion(
                    pkg?.let { apps[it]?.label } ?: (folder?.name ?: ""),
                    opciones,
                    conFondo = false,
                    alCerrar = cerrar,
                )
            }
            null -> Unit
        }

        renombrar?.let { id ->
            val volver = {
                renombrar = null
                if (carpeta != null) carpetaFR.requestFocus() else rejillaFR.requestFocus()
            }
            DialogoTexto(
                titulo = "Nombre de la carpeta",
                inicial = layout.folder(id)?.name ?: "",
                conFondo = carpeta == null,
                alAceptar = { nombre ->
                    vm.cambiar { it.renameFolder(id, nombre) }
                    volver()
                },
                alCancelar = volver,
            )
        }

        info?.let { e ->
            val cerrarInfo = {
                info = null
                heroFR.requestFocus()
            }
            when (e) {
                is AppEntry -> {
                    val app = apps[e.pkg]
                    val det by produceState<com.miusuario.tvlauncher.data.Detalles?>(null, e.pkg) { value = vm.detalles(e.pkg) }
                    val fecha = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                    PanelInfo(
                        titulo = app?.label ?: e.pkg,
                        lineas = listOfNotNull(
                            "Tipo" to nombreCategoria(app?.categoria),
                            det?.version?.takeIf { it.isNotBlank() }?.let { "Versión" to it },
                            det?.let { "Actualizada" to fecha.format(java.util.Date(it.actualizada)) },
                            det?.let { "Instalada" to fecha.format(java.util.Date(it.instalada)) },
                            "Paquete" to e.pkg,
                        ),
                        acciones = listOf(
                            "Abrir" to {
                                info = null
                                vm.launch(e.pkg)
                            },
                            "Ajustes de la app" to { vm.repo.openAppSettings(e.pkg) },
                        ),
                        alCerrar = cerrarInfo,
                    )
                }
                is FolderEntry -> PanelInfo(
                    titulo = e.name,
                    lineas = listOf("Apps" to e.apps.joinToString(", ") { apps[it]?.label ?: it }),
                    acciones = listOf("Abrir carpeta" to {
                        info = null
                        abrirCarpeta(e)
                    }),
                    alCerrar = cerrarInfo,
                )
            }
        }

        if (ajustesAbiertos) {
            PanelAjustes(
                ajustes = ajustes,
                calidadEfectiva = calidad,
                ocultas = layout.hidden.map { it to (apps[it]?.label ?: it) }.sortedBy { it.second.lowercase() },
                alCambiar = vm::guardarAjustes,
                alMostrar = { pkg -> vm.cambiar { it.unhide(pkg) } },
                alAbrirSistema = { vm.repo.openSystemSettings() },
                alCerrar = {
                    ajustesAbiertos = false
                    rejillaFR.requestFocus()
                },
            )
        }
    }
}
