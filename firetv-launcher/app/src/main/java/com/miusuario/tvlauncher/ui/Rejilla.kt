package com.miusuario.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miusuario.tvlauncher.Config
import com.miusuario.tvlauncher.data.AppInfo
import com.miusuario.tvlauncher.model.GridEntry
import com.miusuario.tvlauncher.ui.glass.glass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Espacio vertical entre filas: deja sitio al nombre bajo el icono enfocado. */
val ESPACIO_ETIQUETA = 34.dp

/**
 * Inclinación parallax del icono enfocado: una base según la columna (los de la izquierda miran a la derecha y al
 * revés) más un impulso en la dirección del D-pad que vuelve con un resorte.
 */
@Stable
class Parallax(private val scope: CoroutineScope) {
    private val x = Animatable(0f)
    private val y = Animatable(0f)
    private val baseY = Animatable(0f)

    val inclinacion: State<Offset> = derivedStateOf { Offset(x.value, baseY.value + y.value) }

    /** De -1 a 1, para el brillo especular. */
    val especular: State<Float> = derivedStateOf {
        ((baseY.value + y.value) / Config.INCLINACION_MAX).coerceIn(-1f, 1f)
    }

    fun enfocar(columna: Int, columnas: Int, dx: Int, dy: Int) {
        val max = Config.INCLINACION_MAX
        val fraccion = if (columnas > 1) columna.toFloat() / (columnas - 1) else 0.5f
        scope.launch { baseY.animateTo((fraccion - 0.5f) * max, spring(stiffness = Spring.StiffnessLow)) }
        if (dx != 0) scope.launch {
            y.snapTo(dx * max * 0.6f)
            y.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
        if (dy != 0) scope.launch {
            x.snapTo(-dy * max * 0.6f)
            x.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
    }
}

/**
 * Rejilla de iconos 5:3. El foco lo lleva la pantalla con un índice (D-pad gestionado a mano), lo que permite mover
 * iconos, soltarlos sobre otros y abrir carpetas sin pelearse con el sistema de foco.
 */
@Composable
fun RejillaIconos(
    entradas: List<GridEntry>,
    apps: Map<String, AppInfo>,
    columnas: Int,
    anchoIcono: Dp,
    separacion: Dp,
    margen: Dp,
    foco: Int,
    destino: Int?,
    levantado: Int?,
    mostrarFoco: Boolean,
    vaiven: Boolean,
    inclinacion: State<Offset>,
    etiquetaConFondo: Boolean,
    estado: LazyGridState,
    /** Alto visible de la rejilla: el relleno inferior permite subir hasta arriba también la última fila. */
    altoVisible: Dp,
    alPosicionarFoco: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    /** Índice global de la primera entrada (las de antes están en el Dock). Foco, destino y levantado son globales. */
    inicio: Int = 0,
) {
    val altoIcono = anchoIcono / Config.RELACION
    val rellenoSuperior = altoIcono * ((Config.ESCALA_FOCO - 1f) / 2f) + 8.dp
    val rellenoInferior = maxOf(ESPACIO_ETIQUETA, altoVisible - rellenoSuperior - altoIcono)
    val objetivo = ((destino ?: foco) - inicio).coerceAtLeast(0)
    val etiquetaPx = with(LocalDensity.current) { ESPACIO_ETIQUETA.roundToPx() }
    // La fila enfocada se queda donde está si se ve entera; si no, sube hasta arriba (nunca queda una fila cortada arriba).
    LaunchedEffect(objetivo, entradas.size) {
        if (entradas.isEmpty()) return@LaunchedEffect
        val inicioFila = (objetivo / columnas) * columnas
        val info = estado.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == inicioFila }
        val entera = item != null && item.offset.y >= 0 &&
            item.offset.y + item.size.height + etiquetaPx <= info.viewportEndOffset - info.afterContentPadding
        if (!entera) estado.animateScrollToItem(inicioFila)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnas),
        state = estado,
        // Arriba deja sitio al icono escalado; abajo, a su nombre.
        contentPadding = PaddingValues(
            start = margen,
            end = margen,
            top = rellenoSuperior,
            bottom = rellenoInferior,
        ),
        horizontalArrangement = Arrangement.spacedBy(separacion),
        verticalArrangement = Arrangement.spacedBy(ESPACIO_ETIQUETA),
        userScrollEnabled = false,
        modifier = modifier,
    ) {
        itemsIndexed(entradas, key = { _, e -> e.id }) { local, entry ->
            val i = local + inicio
            val estadoTile = when {
                levantado == i -> EstadoTile.LEVANTADO
                destino == i -> EstadoTile.DESTINO
                mostrarFoco && destino == null && i == foco -> EstadoTile.ENFOCADO
                else -> EstadoTile.NORMAL
            }
            Tile(
                entry = entry,
                apps = apps,
                ancho = anchoIcono,
                estado = estadoTile,
                vaiven = vaiven,
                fase = i,
                inclinacion = inclinacion,
                etiquetaConFondo = etiquetaConFondo,
                alPosicionar = if (i == foco) alPosicionarFoco else null,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/**
 * El Dock, como en Apple TV: la primera fila de la rejilla son las apps ancladas, sobre una repisa de cristal. Los
 * iconos quedan en las mismas columnas que la rejilla de abajo (mismo margen y separación); la repisa sobresale
 * [RELLENO_DOCK] por cada lado. La repisa se dibuja aparte para que el icono enfocado y su nombre no se recorten.
 */
@Composable
fun Dock(
    entradas: List<GridEntry>,
    apps: Map<String, AppInfo>,
    anchoIcono: Dp,
    separacion: Dp,
    margen: Dp,
    foco: Int,
    destino: Int?,
    levantado: Int?,
    mostrarFoco: Boolean,
    vaiven: Boolean,
    inclinacion: State<Offset>,
    etiquetaConFondo: Boolean,
    alPosicionarFoco: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val altoIcono = anchoIcono / Config.RELACION
    Box(modifier.fillMaxWidth().padding(horizontal = margen - RELLENO_DOCK)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(altoIcono + RELLENO_DOCK * 2)
                .glass(RoundedCornerShape(Config.RADIO_ESQUINA + RELLENO_DOCK), elevacion = 10.dp),
        )
        Row(
            Modifier.padding(RELLENO_DOCK),
            horizontalArrangement = Arrangement.spacedBy(separacion),
        ) {
            entradas.take(Config.DOCK).forEachIndexed { i, entry ->
                val estadoTile = when {
                    levantado == i -> EstadoTile.LEVANTADO
                    destino == i -> EstadoTile.DESTINO
                    mostrarFoco && destino == null && i == foco -> EstadoTile.ENFOCADO
                    else -> EstadoTile.NORMAL
                }
                key(entry.id) {
                    Tile(
                        entry = entry,
                        apps = apps,
                        ancho = anchoIcono,
                        estado = estadoTile,
                        vaiven = vaiven,
                        fase = i,
                        inclinacion = inclinacion,
                        etiquetaConFondo = etiquetaConFondo,
                        alPosicionar = if (i == foco) alPosicionarFoco else null,
                    )
                }
            }
        }
    }
}

/** Lo que la repisa del Dock sobresale de los iconos por cada lado. */
val RELLENO_DOCK = 14.dp
