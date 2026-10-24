package com.miusuario.tvlauncher.ui.theme

import kotlin.math.pow

/**
 * Tokens de color de cada tema como ARGB, sin dependencias de Android, para comprobar el contraste en la JVM.
 * [LauncherTheme] los convierte en el ColorScheme de Material y en [GlassTokens].
 */
data class Paleta(
    val fondo: Long,
    val superficie: Long,
    val texto: Long,
    val textoSecundario: Long,
    /** Tinte blanco del cristal (alfa incluido). */
    val tinteVidrio: Long,
    /** Borde del cristal: más brillante arriba a la izquierda, tenue abajo a la derecha. */
    val bordeClaro: Long,
    val bordeTenue: Long,
    /** Velo bajo el texto del cristal, para garantizar 4.5:1 sobre cualquier color de app. */
    val veloTexto: Long,
    val brilloEspecular: Long,
    val sombra: Long,
    /** Cuánto se mezcla el color de la app enfocada con el fondo (0..1). */
    val intensidadDegradado: Float,
    val bordeFoco: Long,
    val acento: Long,
) {
    companion object {
        val NOCTURNO = Paleta(
            fondo = 0xFF121212,
            superficie = 0xFF1E1E1E,
            texto = 0xFFF2F2F2,
            textoSecundario = 0xFFB8B8B8,
            tinteVidrio = 0x1AFFFFFF,
            bordeClaro = 0x66FFFFFF,
            bordeTenue = 0x14FFFFFF,
            veloTexto = 0x66121212,
            brilloEspecular = 0x33FFFFFF,
            sombra = 0x66000000,
            intensidadDegradado = 0.45f,
            bordeFoco = 0xE6FFFFFF,
            acento = 0xFF00A8E1,
        )

        val CLARO = Paleta(
            fondo = 0xFFF5F5F7,
            superficie = 0xFFFFFFFF,
            texto = 0xFF1C1C1E,
            textoSecundario = 0xFF48484A,
            tinteVidrio = 0x8CFFFFFF,
            bordeClaro = 0xCCFFFFFF,
            bordeTenue = 0x33808088,
            veloTexto = 0x80F5F5F7,
            brilloEspecular = 0x66FFFFFF,
            sombra = 0x52000000,
            intensidadDegradado = 0.30f,
            bordeFoco = 0xFFFFFFFF,
            acento = 0xFF0079A3, // cian de Fire TV oscurecido para 4.5:1 sobre fondo claro
        )

        /**
         * Color de la app ajustado para teñir el fondo: en el tema nocturno los colores muy claros se oscurecen,
         * para que el fondo siga oscuro y el texto mantenga 4.5:1 aunque la app tenga un banner blanco.
         */
        fun colorFondo(app: Long, p: Paleta): Long {
            if (p != NOCTURNO) return app or 0xFF000000
            var c = app or 0xFF000000
            var paso = 0
            while (luminancia(c) > 0.2 && paso < 10) {
                c = mezcla(c, 0xFF000000, 0.15f)
                paso++
            }
            return c
        }

        /** Mezcla `top` (con su alfa) sobre `bottom` opaco. */
        fun sobre(top: Long, bottom: Long): Long {
            val a = ((top shr 24) and 0xFF) / 255.0
            fun ch(shift: Int): Long {
                val t = (top shr shift) and 0xFF
                val b = (bottom shr shift) and 0xFF
                return Math.round(t * a + b * (1 - a))
            }
            return (0xFFL shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        /** Mezcla lineal entre dos colores opacos. */
        fun mezcla(a: Long, b: Long, t: Float): Long {
            fun ch(shift: Int): Long {
                val x = (a shr shift) and 0xFF
                val y = (b shr shift) and 0xFF
                return Math.round(x + (y - x) * t.toDouble())
            }
            return (0xFFL shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }

        fun luminancia(c: Long): Double {
            fun lin(shift: Int): Double {
                val v = ((c shr shift) and 0xFF) / 255.0
                return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * lin(16) + 0.7152 * lin(8) + 0.0722 * lin(0)
        }

        /** Contraste WCAG entre dos colores opacos. */
        fun contraste(a: Long, b: Long): Double {
            val la = luminancia(a)
            val lb = luminancia(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }
    }
}
