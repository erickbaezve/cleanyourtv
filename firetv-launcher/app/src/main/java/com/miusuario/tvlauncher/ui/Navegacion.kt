package com.miusuario.tvlauncher.ui

import androidx.compose.ui.input.key.Key

/** Resultado de mover el foco con el D-pad en una rejilla. */
sealed interface Paso {
    data class A(val indice: Int) : Paso
    /** Se intentó subir desde la primera fila. */
    data object Arriba : Paso
    /** Borde de la rejilla: no se mueve. */
    data object Borde : Paso
}

/** Movimiento del D-pad en una rejilla de [columnas] columnas y [total] elementos. */
fun navegar(key: Key, indice: Int, total: Int, columnas: Int): Paso? {
    if (total == 0) return if (key == Key.DirectionUp) Paso.Arriba else null
    val col = indice % columnas
    return when (key) {
        Key.DirectionLeft -> if (col > 0) Paso.A(indice - 1) else Paso.Borde
        Key.DirectionRight -> if (col < columnas - 1 && indice + 1 < total) Paso.A(indice + 1) else Paso.Borde
        Key.DirectionUp -> if (indice >= columnas) Paso.A(indice - columnas) else Paso.Arriba
        Key.DirectionDown -> {
            val filaSiguiente = (indice / columnas + 1) * columnas
            when {
                indice + columnas < total -> Paso.A(indice + columnas)
                filaSiguiente < total -> Paso.A(total - 1) // última fila incompleta: al último icono
                else -> Paso.Borde
            }
        }
        else -> null
    }
}

fun Key.esOk(): Boolean = this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter
