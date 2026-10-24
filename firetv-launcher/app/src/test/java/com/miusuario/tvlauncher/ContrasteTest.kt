package com.miusuario.tvlauncher

import com.miusuario.tvlauncher.ui.theme.Paleta
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El texto debe tener al menos 4.5:1 en ambos temas, también sobre el cristal teñido con el color más extremo que
 * puede tener una app enfocada (el degradado del fondo mezcla ese color con el fondo del tema).
 */
class ContrasteTest {
    private val coloresApp = listOf(0xFFE50914, 0xFF00A8E1, 0xFFFFFFFF, 0xFF000000, 0xFFFFD400, 0xFF1DB954, 0xFF6A1B9A)

    private fun comprobar(p: Paleta, nombre: String) {
        for (app in coloresApp) {
            // Fondo con el máximo de color de app que mete el degradado, luego tinte del cristal y velo del texto.
            val fondo = Paleta.mezcla(p.fondo, Paleta.colorFondo(app, p), p.intensidadDegradado)
            val cristal = Paleta.sobre(p.veloTexto, Paleta.sobre(p.tinteVidrio, fondo))
            for ((texto, etiqueta) in listOf(p.texto to "texto", p.textoSecundario to "secundario")) {
                val c = Paleta.contraste(texto, cristal)
                assertTrue("$nombre $etiqueta sobre app ${app.toString(16)}: $c", c >= 4.5)
            }
        }
        assertTrue("$nombre texto sobre fondo", Paleta.contraste(p.texto, p.fondo) >= 4.5)
        assertTrue("$nombre acento sobre fondo", Paleta.contraste(p.acento, p.fondo) >= 3.0)
    }

    @Test
    fun nocturno() = comprobar(Paleta.NOCTURNO, "Nocturno")

    @Test
    fun claro() = comprobar(Paleta.CLARO, "Claro")
}
