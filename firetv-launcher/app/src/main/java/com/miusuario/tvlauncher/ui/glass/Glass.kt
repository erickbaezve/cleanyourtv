package com.miusuario.tvlauncher.ui.glass

import androidx.compose.foundation.border
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miusuario.tvlauncher.CalidadVidrio
import com.miusuario.tvlauncher.ui.theme.LocalGlass

/** Calidad efectiva del vidrio: la elegida en ajustes, rebajada si los fps caen. */
val LocalCalidad = staticCompositionLocalOf { CalidadVidrio.ALTA }

/**
 * Posición del brillo especular, de -1 (izquierda) a 1 (derecha). Sigue al icono enfocado y su inclinación.
 * Es un State para que solo se redibuje, sin recomponer.
 */
val LocalEspecular = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/** Cuánto está apagado el fondo (0..1, cuando el foco baja a la rejilla): el cristal se apaga igual. */
val LocalAtenuacion = staticCompositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/**
 * Superficie Liquid Glass:
 * 1. Copia desenfocada del fondo recortada a la forma y alineada con la pantalla (escalada 1.05 en calidad alta,
 *    para sugerir refracción). Solo si [conFondo]: hay un máximo de 3 superficies así a la vez.
 * 2. Tinte blanco del tema.
 * 3. Brillo especular que se desplaza con el foco.
 * 4. Borde de 1 dp con degradado: brillante arriba a la izquierda, tenue abajo a la derecha.
 */
fun Modifier.glass(
    shape: Shape,
    conFondo: Boolean = true,
    elevacion: Dp = 0.dp,
    colorSombra: Color? = null,
): Modifier = composed {
    val tokens = LocalGlass.current
    val calidad = LocalCalidad.current
    val backdrop = LocalBackdrop.current
    val especular = LocalEspecular.current
    val atenuacion = LocalAtenuacion.current
    var origen by remember { mutableStateOf(Offset.Zero) }
    val usarFondo = conFondo && calidad != CalidadVidrio.BAJA && backdrop.actual != null
    val sombra = if (calidad == CalidadVidrio.ALTA && colorSombra != null) colorSombra else tokens.sombra

    this
        .then(if (elevacion > 0.dp) Modifier.shadow(elevacion, shape, ambientColor = sombra, spotColor = sombra) else Modifier)
        .onGloballyPositioned { if (usarFondo) origen = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            if (usarFondo) {
                backdrop.dibujar(this, origen, if (calidad == CalidadVidrio.ALTA) 1.05f else 1f) { it.vidrio }
                if (atenuacion.value > 0f) drawRect(tokens.fondo.copy(alpha = 0.86f * atenuacion.value))
            } else {
                // Calidad baja: solo translúcido con degradado.
                drawRect(Brush.verticalGradient(listOf(tokens.tinte, tokens.tinte.copy(alpha = tokens.tinte.alpha * 0.4f))))
            }
            drawRect(tokens.tinte)
            if (calidad != CalidadVidrio.BAJA) {
                val x = if (calidad == CalidadVidrio.ALTA) especular.value else -0.4f
                val cx = size.width * (0.5f + 0.4f * x)
                drawRect(
                    Brush.radialGradient(
                        listOf(tokens.especular, Color.Transparent),
                        center = Offset(cx, 0f),
                        radius = maxOf(size.width, size.height) * 0.6f,
                    ),
                )
            }
        }
        .border(
            1.dp,
            Brush.linearGradient(listOf(tokens.bordeClaro, tokens.bordeTenue), start = Offset.Zero, end = Offset.Infinite),
            shape,
        )
}
