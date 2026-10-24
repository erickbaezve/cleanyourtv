package com.miusuario.tvlauncher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.miusuario.tvlauncher.Config
import com.miusuario.tvlauncher.data.AppInfo
import com.miusuario.tvlauncher.model.AppEntry
import com.miusuario.tvlauncher.model.FolderEntry
import com.miusuario.tvlauncher.ui.glass.glass
import com.miusuario.tvlauncher.ui.theme.LocalGlass

/** Columnas del panel de carpeta (los iconos tienen el mismo tamaño que en la rejilla principal). */
const val COLUMNAS_CARPETA = 5

/**
 * Panel flotante de cristal con las apps de una carpeta. Se expande desde el icono de la carpeta ([origen]) con un
 * resorte (transición tipo morph) y, al cerrar ([cerrando]), vuelve a él antes de llamar a [alCerrado].
 */
@Composable
fun FolderPanel(
    folder: FolderEntry,
    apps: Map<String, AppInfo>,
    origen: Rect,
    cerrando: Boolean,
    anchoIcono: Dp,
    separacion: Dp,
    foco: Int,
    levantado: Int?,
    vaiven: Boolean,
    inclinacion: State<Offset>,
    focusRequester: FocusRequester,
    alTecla: (KeyEvent) -> Boolean,
    alCerrado: () -> Unit,
) {
    val tokens = LocalGlass.current
    val progreso = remember { Animatable(0f) }
    var destinoRect by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(cerrando) {
        progreso.animateTo(
            if (cerrando) 0f else 1f,
            spring(dampingRatio = if (cerrando) 1f else 0.78f, stiffness = Spring.StiffnessMediumLow),
        )
        if (cerrando) alCerrado()
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val columnas = COLUMNAS_CARPETA
    val altoIcono = anchoIcono / Config.RELACION
    val filas = (folder.apps.size + columnas - 1) / columnas
    val filasVisibles = minOf(filas.toFloat(), 2.5f)
    val relleno = 28.dp
    val anchoPanel = anchoIcono * columnas + separacion * (columnas - 1) + relleno * 2
    val altoRejilla = (altoIcono + ESPACIO_ETIQUETA) * filasVisibles + altoIcono * 0.1f + 8.dp + ESPACIO_ETIQUETA * 0.2f
    val estado = rememberLazyGridState()

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progreso.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(anchoPanel)
                .onGloballyPositioned { destinoRect = it.boundsInRoot() }
                .graphicsLayer {
                    val p = progreso.value
                    if (destinoRect.width > 0f && origen.width > 0f) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        val sx = origen.width / destinoRect.width
                        val sy = origen.height / destinoRect.height
                        scaleX = sx + (1f - sx) * p
                        scaleY = sy + (1f - sy) * p
                        translationX = (origen.left - destinoRect.left) * (1f - p)
                        translationY = (origen.top - destinoRect.top) * (1f - p)
                    }
                }
                .glass(RoundedCornerShape(32.dp), elevacion = 24.dp)
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val contenido = ((progreso.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
            Text(
                folder.name,
                style = MaterialTheme.typography.titleLarge,
                color = tokens.texto,
                modifier = Modifier.padding(horizontal = relleno).graphicsLayer { alpha = contenido },
            )
            RejillaIconos(
                entradas = folder.apps.map { AppEntry(it) },
                apps = apps,
                columnas = columnas,
                anchoIcono = anchoIcono,
                separacion = separacion,
                margen = relleno,
                foco = foco,
                destino = null,
                levantado = levantado,
                mostrarFoco = true,
                vaiven = vaiven,
                inclinacion = inclinacion,
                etiquetaConFondo = false,
                estado = estado,
                altoVisible = altoRejilla,
                alPosicionarFoco = {},
                modifier = Modifier
                    .height(altoRejilla)
                    .graphicsLayer { alpha = contenido }
                    .focusRequester(focusRequester)
                    .onKeyEvent(alTecla)
                    .focusable(),
            )
        }
    }
}
