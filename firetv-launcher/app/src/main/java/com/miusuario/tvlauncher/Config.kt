package com.miusuario.tvlauncher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Valores de diseño fáciles de cambiar. Todo lo demás se calcula a partir de ellos. */
object Config {
    /** Iconos por fila, como en Apple TV. */
    const val COLUMNAS = 6

    /** Proporción ancho/alto del icono (400x240 de tvOS). */
    const val RELACION = 5f / 3f

    /** Escala del icono enfocado. */
    const val ESCALA_FOCO = 1.2f

    /** Radio de las esquinas del icono. */
    val RADIO_ESQUINA = 12.dp

    /** Margen lateral, fracción del ancho de pantalla. */
    const val MARGEN_LATERAL = 0.05f

    /** Separación entre iconos, fracción del ancho de pantalla. */
    const val SEPARACION = 0.02f

    /** Inclinación máxima (grados) del efecto parallax del icono enfocado. */
    const val INCLINACION_MAX = 6f

    /** Acento azul cian de Fire TV. */
    val CIAN_FIRE_TV = Color(0xFF00A8E1)

    /** Apps por carpeta dibujadas en la mini-cuadrícula del icono (3x3). */
    const val MINI_CUADRICULA = 9

    /** Por debajo de estos fps durante una animación, el launcher baja la calidad del vidrio. */
    const val FPS_MINIMOS = 50

    /** Horario por defecto del tema Automático: nocturno de 19:00 a 07:00. */
    const val NOCHE_INICIO = 19
    const val NOCHE_FIN = 7
}

/** Calidad del efecto Liquid Glass. Se cambia en los ajustes del launcher. */
enum class CalidadVidrio(val etiqueta: String) {
    /** Fondo desenfocado dentro del cristal, refracción 1.05, brillo especular y sombras de color. */
    ALTA("Alta"),

    /** Fondo desenfocado dentro del cristal, sin refracción ni brillo móvil. */
    MEDIA("Media"),

    /** Solo translúcido con degradado. */
    BAJA("Baja");

    fun inferior(): CalidadVidrio = when (this) {
        ALTA -> MEDIA
        MEDIA, BAJA -> BAJA
    }
}

/** Modo de tema elegido en los ajustes. */
enum class ModoTema(val etiqueta: String) { CLARO("Claro"), NOCTURNO("Nocturno"), AUTOMATICO("Automático") }

/** Tema Automático: ¿es de noche a la hora [hora] (0..23) con el horario [inicio]..[fin]? Admite cruzar medianoche. */
fun esNoche(hora: Int, inicio: Int, fin: Int): Boolean = when {
    inicio == fin -> false
    inicio > fin -> hora >= inicio || hora < fin
    else -> hora in inicio until fin
}
