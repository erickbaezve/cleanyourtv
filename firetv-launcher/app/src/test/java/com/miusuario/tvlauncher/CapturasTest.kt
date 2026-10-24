package com.miusuario.tvlauncher

import android.app.Application
import androidx.activity.ComponentActivity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.core.app.ApplicationProvider
import com.miusuario.tvlauncher.data.Ajustes
import com.miusuario.tvlauncher.data.AppInfo
import com.miusuario.tvlauncher.model.FolderEntry
import com.miusuario.tvlauncher.model.LayoutState
import com.miusuario.tvlauncher.ui.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renderiza la pantalla real con apps falsas (Robolectric, gráficos nativos) y guarda capturas en
 * `app/build/capturas/`. Sirve para revisar la rejilla, la tarjeta principal, las carpetas y los temas sin un Fire TV.
 * Pantalla de 1080p en dp de TV: 960x540 dp.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w960dp-h540dp-land-television-xhdpi")
class CapturasTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val salida = File("build/capturas").apply { mkdirs() }

    private fun guardar(nombre: String) {
        rule.waitForIdle()
        val vista = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        vista.draw(Canvas(bmp))
        File(salida, "$nombre.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun esperar(ms: Long = 1500) {
        repeat((ms / 100).toInt()) {
            rule.mainClock.advanceTimeBy(100)
            Thread.sleep(15)
        }
        rule.waitForIdle()
    }

    /** Espera a que el ViewModel (DataStore en otro hilo, resultados en el looper principal) llegue a un estado. */
    private fun esperarA(que: String, condicion: () -> Boolean) {
        val fin = System.currentTimeMillis() + 15_000
        while (!condicion()) {
            check(System.currentTimeMillis() < fin) { "Sin $que" }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
    }

    /** La tarjeta principal y la etiqueta muestran el nombre de la app enfocada (el banner es un bitmap, sin texto). */
    private fun enfocada(nombre: String) {
        val n = rule.onAllNodesWithText(nombre).fetchSemanticsNodes().size
        assertTrue("Se esperaba el foco en $nombre", n >= 2)
    }

    /** Atrás del mando: pasa por el OnBackPressedDispatcher de la Activity, como en el Fire TV. */
    private fun atras() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        esperar(800)
    }

    private fun tecla(key: Key, veces: Int = 1) = repeat(veces) {
        rule.onRoot().performKeyInput { pressKey(key) }
        esperar(500)
    }

    @Test
    fun capturas() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = LauncherViewModel(app)
        vm.appsFijas = falsas()
        vm.guardarAjustes(Ajustes(modoTema = ModoTema.NOCTURNO))
        vm.refresh()
        esperarA("apps cargadas") { vm.layout.value.entries.size == FALSAS.size }
        esperarA("tema nocturno") { vm.ajustes.value.modoTema == ModoTema.NOCTURNO }
        // Una carpeta de streaming en primera posición.
        vm.cambiar { it.dropOnto(1, 0, vm::categoria).dropOnto(1, 0, vm::categoria) }
        assertTrue(vm.layout.value.entries[0] is FolderEntry)

        rule.setContent { HomeScreen(vm) }
        esperar(2500)
        guardar("01_nocturno_carpeta_enfocada")

        tecla(Key.DirectionRight, 2)
        esperar()
        enfocada("Spotify")
        guardar("02_nocturno_app_enfocada")

        tecla(Key.DirectionDown)
        esperar()
        enfocada("Downloader")
        guardar("03_nocturno_segunda_fila")

        tecla(Key.Menu)
        esperar()
        rule.onNodeWithText("Crear carpeta").assertExists()
        guardar("04_menu_edicion")
        atras()
        assertTrue("El menú debería cerrarse", rule.onAllNodesWithText("Crear carpeta").fetchSemanticsNodes().isEmpty())

        // Crear carpeta: Downloader se levanta con la app siguiente (Twitch) ya marcada como destino; el D-pad cambia
        // el destino (Pluto TV) y OK la suelta encima.
        tecla(Key.Menu)
        tecla(Key.DirectionDown, 2)
        tecla(Key.DirectionCenter)
        tecla(Key.DirectionRight)
        guardar("05_soltar_sobre_otra_app")
        tecla(Key.DirectionCenter)
        esperar()
        val carpetas = vm.layout.value.entries.filterIsInstance<FolderEntry>()
        assertEquals(2, carpetas.size)
        assertEquals(listOf("tv.pluto.android", "com.esaba.downloader"), carpetas[1].apps)
        assertEquals("Carpeta", carpetas[1].name) // vídeo + utilidad: no se puede deducir
        guardar("06_carpeta_nueva")

        vm.onHome()
        esperar()
        tecla(Key.DirectionCenter)
        esperar(2500)
        rule.onNodeWithText("Carpeta · 3 apps").assertExists()
        guardar("07_carpeta_abierta")
        atras()
        esperar(2000)
        // Carpeta cerrada: el D-pad vuelve a mover el foco de la rejilla principal.
        tecla(Key.DirectionRight)
        enfocada("YouTube")
        tecla(Key.DirectionLeft)

        vm.guardarAjustes(Ajustes(modoTema = ModoTema.CLARO))
        esperarA("tema claro") { vm.ajustes.value.modoTema == ModoTema.CLARO }
        tecla(Key.DirectionRight, 3)
        esperar(3000)
        guardar("08_claro")

        vm.guardarAjustes(Ajustes(modoTema = ModoTema.CLARO, calidad = CalidadVidrio.BAJA))
        esperarA("calidad baja") { vm.ajustes.value.calidad == CalidadVidrio.BAJA }
        esperar(3000)
        guardar("09_claro_calidad_baja")

        tecla(Key.DirectionUp) // del Dock a los botones de la tarjeta principal
        guardar("11_claro_botones_hero")
        tecla(Key.DirectionRight) // Información
        tecla(Key.DirectionCenter)
        esperar(1500)
        rule.onNodeWithText("Ajustes de la app").assertExists()
        guardar("12_informacion")
        atras()
        tecla(Key.DirectionUp) // a la búsqueda
        tecla(Key.DirectionRight) // al engranaje
        tecla(Key.DirectionCenter)
        esperar(1500)
        rule.onNodeWithText("Calidad del vidrio").assertExists()
        guardar("10_ajustes")
    }

    private fun falsas(): List<AppInfo> = FALSAS.map { (pkg, nombre, color, conBanner) ->
        val banner = if (conBanner) banner(nombre, color) else null
        val icono = icono(nombre, color)
        AppInfo(pkg, nombre, banner?.asImageBitmap(), icono.asImageBitmap(), Color(color), LayoutState.categoriaPorPaquete(pkg))
    }

    private fun banner(nombre: String, color: Int): Bitmap {
        val b = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 320f, 180f, color, oscurecer(color), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 320f, 180f, p)
        // Formas suaves para que el arte de fondo parezca una ilustración.
        p.shader = android.graphics.RadialGradient(250f, 40f, 140f, 0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(250f, 40f, 140f, p)
        p.shader = android.graphics.RadialGradient(60f, 170f, 110f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
        c.drawCircle(60f, 170f, 110f, p)
        p.shader = null
        p.color = android.graphics.Color.WHITE
        p.textSize = 40f
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        c.drawText(nombre, 200f, 104f, p)
        return b
    }

    private fun icono(nombre: String, color: Int): Bitmap {
        val b = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = android.graphics.Color.WHITE
        c.drawCircle(80f, 80f, 70f, p)
        p.color = color
        p.textSize = 80f
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        c.drawText(nombre.take(1), 80f, 108f, p)
        return b
    }

    private fun oscurecer(c: Int): Int {
        val f = 0.55f
        return android.graphics.Color.rgb(
            (android.graphics.Color.red(c) * f).toInt(),
            (android.graphics.Color.green(c) * f).toInt(),
            (android.graphics.Color.blue(c) * f).toInt(),
        )
    }

    private data class Falsa(val pkg: String, val nombre: String, val color: Int, val banner: Boolean)

    companion object {
        private val FALSAS = listOf(
            Falsa("com.netflix.ninja", "Netflix", 0xFFE50914.toInt(), true),
            Falsa("com.amazon.avod", "Prime Video", 0xFF1A98FF.toInt(), true),
            Falsa("com.disney.disneyplus", "Disney+", 0xFF113CCF.toInt(), true),
            Falsa("com.google.android.youtube.tv", "YouTube", 0xFFFF0000.toInt(), true),
            Falsa("com.spotify.tv.android", "Spotify", 0xFF1DB954.toInt(), true),
            Falsa("org.xbmc.kodi", "Kodi", 0xFF17B2E7.toInt(), true),
            Falsa("com.plexapp.android", "Plex", 0xFFE5A00D.toInt(), true),
            Falsa("com.amazon.tv.notas", "Notas", 0xFF8E8E93.toInt(), false),
            Falsa("org.mozilla.tv.firefox", "Firefox", 0xFFFF7139.toInt(), true),
            Falsa("com.retroarch", "RetroArch", 0xFF6A1B9A.toInt(), false),
            Falsa("com.esaba.downloader", "Downloader", 0xFFF57C00.toInt(), true),
            Falsa("com.twitch.android.app", "Twitch", 0xFF9146FF.toInt(), true),
            Falsa("tv.pluto.android", "Pluto TV", 0xFFFFE600.toInt(), true),
            Falsa("com.crunchyroll.crunchyroid", "Crunchyroll", 0xFFF47521.toInt(), true),
        )
    }
}
