package com.miusuario.tvlauncher.ui.glass

import android.graphics.Bitmap

/**
 * Desenfoque por pila (stack blur) en Kotlin puro, sin librerías nativas: Fire OS 7 y 8 no tienen RenderEffect.
 * Solo se usa sobre bitmaps pequeños (≈192x108) y una vez por cambio de foco, nunca por fotograma.
 */
object StackBlur {
    fun blur(src: Bitmap, radius: Int): Bitmap {
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        if (radius < 1) return bmp
        val w = bmp.width
        val h = bmp.height
        val pix = IntArray(w * h)
        bmp.getPixels(pix, 0, w, 0, 0, w, h)
        pasada(pix, w, h, radius, horizontal = true)
        pasada(pix, w, h, radius, horizontal = false)
        bmp.setPixels(pix, 0, w, 0, 0, w, h)
        return bmp
    }

    /** Una pasada 1D de stack blur (pesos triangulares) por filas o por columnas. */
    private fun pasada(pix: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val div = (r + 1) * (r + 1)
        val stackA = IntArray(2 * r + 1)
        val stackR = IntArray(2 * r + 1)
        val stackG = IntArray(2 * r + 1)
        val stackB = IntArray(2 * r + 1)
        val out = IntArray(len)
        for (line in 0 until lines) {
            fun idx(i: Int): Int {
                val c = i.coerceIn(0, len - 1)
                return if (horizontal) line * w + c else c * w + line
            }
            var sumA = 0; var sumR = 0; var sumG = 0; var sumB = 0
            var inA = 0; var inR = 0; var inG = 0; var inB = 0
            var outA = 0; var outR = 0; var outG = 0; var outB = 0
            for (i in -r..r) {
                val p = pix[idx(i)]
                val s = i + r
                stackA[s] = p ushr 24; stackR[s] = (p shr 16) and 0xFF; stackG[s] = (p shr 8) and 0xFF; stackB[s] = p and 0xFF
                val weight = r + 1 - kotlin.math.abs(i)
                sumA += stackA[s] * weight; sumR += stackR[s] * weight; sumG += stackG[s] * weight; sumB += stackB[s] * weight
                if (i > 0) {
                    inA += stackA[s]; inR += stackR[s]; inG += stackG[s]; inB += stackB[s]
                } else {
                    outA += stackA[s]; outR += stackR[s]; outG += stackG[s]; outB += stackB[s]
                }
            }
            var sp = r
            for (x in 0 until len) {
                out[x] = ((sumA / div) shl 24) or ((sumR / div) shl 16) or ((sumG / div) shl 8) or (sumB / div)
                sumA -= outA; sumR -= outR; sumG -= outG; sumB -= outB
                var start = sp - r + 2 * r + 1
                start %= (2 * r + 1)
                outA -= stackA[start]; outR -= stackR[start]; outG -= stackG[start]; outB -= stackB[start]
                val p = pix[idx(x + r + 1)]
                stackA[start] = p ushr 24; stackR[start] = (p shr 16) and 0xFF
                stackG[start] = (p shr 8) and 0xFF; stackB[start] = p and 0xFF
                inA += stackA[start]; inR += stackR[start]; inG += stackG[start]; inB += stackB[start]
                sumA += inA; sumR += inR; sumG += inG; sumB += inB
                sp = (sp + 1) % (2 * r + 1)
                outA += stackA[sp]; outR += stackR[sp]; outG += stackG[sp]; outB += stackB[sp]
                inA -= stackA[sp]; inR -= stackR[sp]; inG -= stackG[sp]; inB -= stackB[sp]
            }
            for (x in 0 until len) pix[if (horizontal) line * w + x else x * w + line] = out[x]
        }
    }
}
