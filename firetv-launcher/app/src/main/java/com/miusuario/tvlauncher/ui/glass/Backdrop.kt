package com.miusuario.tvlauncher.ui.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
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

/** Copia pequeña del fondo y su versión desenfocada, que es la que se ve dentro del cristal. */
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
         * Renderiza una copia pequeña del fondo de la tarjeta principal (llamar fuera del hilo principal): el arte de
         * la app a pantalla completa con los mismos velos que [com.miusuario.tvlauncher.ui.FondoHero], desenfocada.
         * Devuelve null en calidad baja: entonces el cristal solo es translúcido.
         */
        fun renderizar(
            base: Color,
            colorApp: Color,
            banner: ImageBitmap?,
            calidad: CalidadVidrio,
        ): Capa? {
            if (calidad == CalidadVidrio.BAJA) return null
            val bmp = Bitmap.createBitmap(ANCHO, ALTO, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val w = ANCHO.toFloat()
            val h = ALTO.toFloat()
            val app = colorApp.toArgb()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            paint.shader = LinearGradient(0f, 0f, w, h, app, oscurecer(app), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = null
            if (banner != null) {
                // Recorte centrado a 16:9, como ContentScale.Crop.
                val b = banner.asAndroidBitmap()
                val escala = maxOf(w / b.width, h / b.height)
                val dw = b.width * escala
                val dh = b.height * escala
                c.drawBitmap(b, Rect(0, 0, b.width, b.height), RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), paint)
            }
            val fondo = base.toArgb()
            paint.shader = LinearGradient(
                0f, 0f, w, 0f,
                intArrayOf(conAlfa(fondo, 0.92f), conAlfa(fondo, 0.55f), conAlfa(fondo, 0f)),
                floatArrayOf(0f, 0.42f, 0.75f), Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, w, h, paint)
            paint.shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(conAlfa(fondo, 0.55f), conAlfa(fondo, 0f), conAlfa(fondo, 0f), conAlfa(fondo, 0.85f), conAlfa(fondo, 1f)),
                floatArrayOf(0f, 0.18f, 0.52f, 0.82f, 1f), Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, w, h, paint)
            val vidrio = StackBlur.blur(bmp, 8)
            return Capa(bmp.asImageBitmap(), vidrio.asImageBitmap())
        }

        private fun oscurecer(argb: Int): Int {
            val r = ((argb shr 16) and 0xFF) * 4 / 10
            val g = ((argb shr 8) and 0xFF) * 4 / 10
            val b = (argb and 0xFF) * 4 / 10
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        private fun conAlfa(argb: Int, a: Float): Int =
            ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (argb and 0x00FFFFFF)
    }
}

val LocalBackdrop = staticCompositionLocalOf { BackdropState() }
