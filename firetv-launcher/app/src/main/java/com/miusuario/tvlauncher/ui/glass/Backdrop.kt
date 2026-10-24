package com.miusuario.tvlauncher.ui.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.miusuario.tvlauncher.CalidadVidrio

/** Fondo de pantalla ya desenfocado y su copia más desenfocada para el interior del cristal. */
class Capa(val fondo: ImageBitmap, val vidrio: ImageBitmap)

/**
 * El fondo lo controla el launcher (degradado del color de la app enfocada y su banner), así que se renderiza una vez
 * por cambio de foco a un bitmap pequeño, se desenfoca y se guarda. Las superficies de cristal dibujan su recorte
 * alineado con la pantalla. Al cambiar el foco las capas se funden con un resorte.
 */
@Stable
class BackdropState {
    var actual by mutableStateOf<Capa?>(null)
        private set
    var anterior by mutableStateOf<Capa?>(null)
        private set
    val fundido = Animatable(1f)

    /** Tamaño de la pantalla en px, para alinear el cristal con el fondo. */
    var pantalla by mutableStateOf(IntSize.Zero)

    suspend fun mostrar(capa: Capa) {
        anterior = actual
        actual = capa
        fundido.snapTo(0f)
        fundido.animateTo(1f, spring(stiffness = Spring.StiffnessLow))
        anterior = null
    }

    /** Calidad baja: sin capas; el fondo vuelve a ser un degradado dibujado directamente. */
    fun limpiar() {
        anterior = null
        actual = null
    }

    /** Dibuja la capa elegida a pantalla completa, desplazada para que coincida con la posición [origen] del elemento. */
    fun dibujar(
        scope: DrawScope,
        origen: Offset,
        escala: Float,
        alpha: Float = 1f,
        elegir: (Capa) -> ImageBitmap,
    ) = with(scope) {
        if (pantalla == IntSize.Zero) return@with
        withTransform({
            scale(escala, escala, pivot = center)
            translate(-origen.x, -origen.y)
        }) {
            val prev = anterior
            val cur = actual
            val f = fundido.value
            if (prev != null && f < 1f) pintar(elegir(prev), alpha)
            if (cur != null) pintar(elegir(cur), alpha * if (prev != null) f else 1f)
        }
    }

    private fun DrawScope.pintar(img: ImageBitmap, alpha: Float) = drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset.Zero,
        dstSize = pantalla,
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )

    companion object {
        private const val ANCHO = 192
        private const val ALTO = 108

        /**
         * Renderiza el fondo (llamar fuera del hilo principal). Devuelve null en calidad baja: entonces el fondo es
         * un degradado dibujado directamente y el cristal solo es translúcido.
         */
        fun renderizar(
            base: Color,
            colorApp: Color,
            banner: ImageBitmap?,
            intensidad: Float,
            dark: Boolean,
            calidad: CalidadVidrio,
        ): Capa? {
            if (calidad == CalidadVidrio.BAJA) return null
            val bmp = Bitmap.createBitmap(ANCHO, ALTO, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(base.toArgb())
            val app = colorApp.toArgb()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            // Degradado vivo: el color de la app desde arriba a la izquierda hacia el fondo.
            paint.shader = LinearGradient(
                0f, 0f, ANCHO.toFloat(), ALTO.toFloat(),
                conAlfa(app, intensidad), conAlfa(app, 0f), Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, ANCHO.toFloat(), ALTO.toFloat(), paint)
            paint.shader = RadialGradient(
                ANCHO * 0.8f, ALTO * 0.15f, ANCHO * 0.55f,
                conAlfa(app, intensidad * 0.9f), conAlfa(app, 0f), Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, ANCHO.toFloat(), ALTO.toFloat(), paint)
            paint.shader = null
            // El arte de la app, grande y tenue, para que el cristal tenga algo que "refractar".
            if (banner != null) {
                val b = banner.asAndroidBitmap()
                paint.alpha = if (dark) 70 else 55
                c.drawBitmap(b, Rect(0, 0, b.width, b.height), RectF(ANCHO * 0.35f, -ALTO * 0.1f, ANCHO * 1.1f, ALTO * 0.75f), paint)
            }
            val fondo = StackBlur.blur(bmp, 4)
            val vidrio = StackBlur.blur(fondo, 10)
            bmp.recycle()
            return Capa(fondo.asImageBitmap(), vidrio.asImageBitmap())
        }

        private fun conAlfa(argb: Int, a: Float): Int =
            ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (argb and 0x00FFFFFF)
    }
}

val LocalBackdrop = staticCompositionLocalOf { BackdropState() }
