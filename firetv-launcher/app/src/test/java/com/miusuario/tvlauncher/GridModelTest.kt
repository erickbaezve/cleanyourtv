package com.miusuario.tvlauncher

import com.miusuario.tvlauncher.model.AppEntry
import com.miusuario.tvlauncher.model.Categoria
import com.miusuario.tvlauncher.model.FolderEntry
import com.miusuario.tvlauncher.model.LayoutJson
import com.miusuario.tvlauncher.model.LayoutState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GridModelTest {
    private val cat: (String) -> Categoria = LayoutState::categoriaPorPaquete

    private fun state(vararg pkgs: String) = LayoutState().reconcile(pkgs.toList())

    @Test
    fun primerArranqueEnElOrdenRecibido() {
        val s = state("a", "b", "c")
        assertEquals(listOf("a", "b", "c"), s.entries.map { (it as AppEntry).pkg })
    }

    @Test
    fun appNuevaAlFinalYDesinstaladaFuera() {
        val s = state("a", "b", "c").move(2, 0).reconcile(listOf("a", "b", "c", "d").filter { it != "b" })
        assertEquals(listOf("c", "a", "d"), s.entries.map { (it as AppEntry).pkg })
    }

    @Test
    fun soltarAppSobreAppCreaCarpetaConNombre() {
        val s = state("com.netflix.ninja", "x", "com.google.android.youtube.tv").dropOnto(0, 2, cat)
        assertEquals(2, s.entries.size)
        val f = s.entries[1] as FolderEntry
        assertEquals(listOf("com.google.android.youtube.tv", "com.netflix.ninja"), f.apps)
        assertEquals("Streaming", f.name)
    }

    @Test
    fun nombreCarpetaSinCategoria() {
        val s = state("a", "b").dropOnto(1, 0, cat)
        assertEquals("Carpeta", (s.entries[0] as FolderEntry).name)
    }

    @Test
    fun nombreCarpetaEmpateEsCarpeta() {
        val s = state("tv.pluto.android", "com.esaba.downloader").dropOnto(1, 0, cat)
        assertEquals("Carpeta", (s.entries[0] as FolderEntry).name)
        assertEquals("Juegos", LayoutState.autoName(listOf("com.retroarch", "com.games.x", "com.netflix.ninja"), cat))
    }

    @Test
    fun soltarSobreCarpetaLaAmplia() {
        val s = state("a", "b", "c").dropOnto(1, 0, cat).dropOnto(1, 0, cat)
        val f = s.entries.single() as FolderEntry
        assertEquals(listOf("a", "b", "c"), f.apps)
    }

    @Test
    fun carpetasNoSeAnidan() {
        val s = state("a", "b", "c", "d").dropOnto(1, 0, cat).dropOnto(2, 1, cat)
        val antes = s
        assertEquals(antes, s.dropOnto(0, 1, cat)) // carpeta sobre carpeta: nada
        assertEquals(antes, s.dropOnto(0, 0, cat))
    }

    @Test
    fun carpetaConUnaAppSeDisuelve() {
        val s = state("a", "b", "c").dropOnto(1, 0, cat)
        val id = (s.entries[0] as FolderEntry).folderId
        val fuera = s.takeOutOfFolder(id, "a")
        assertEquals(listOf("b", "a", "c"), fuera.entries.map { (it as AppEntry).pkg })
    }

    @Test
    fun desinstalarDentroDeCarpetaLaDisuelve() {
        val s = state("a", "b", "c").dropOnto(1, 0, cat).reconcile(listOf("a", "c"))
        assertEquals(listOf("a", "c"), s.entries.map { (it as AppEntry).pkg })
    }

    @Test
    fun eliminarCarpetaDevuelveLasApps() {
        val s = state("a", "b", "c", "d").dropOnto(1, 0, cat).dropOnto(1, 0, cat)
        val id = (s.entries[0] as FolderEntry).folderId
        assertEquals(listOf("a", "b", "c", "d"), s.deleteFolder(id).entries.map { (it as AppEntry).pkg })
    }

    @Test
    fun unaAppEnUnSoloSitio() {
        var s = state("a", "b", "c").dropOnto(1, 0, cat).hide("c")
        s = s.reconcile(listOf("a", "b", "c"))
        val todas = s.entries.flatMap { if (it is FolderEntry) it.apps else listOf((it as AppEntry).pkg) } + s.hidden
        assertEquals(todas.size, todas.toSet().size)
        assertTrue("c" in s.hidden)
        assertEquals(listOf("a", "b", "c"), s.unhide("c").placed().sorted())
    }

    @Test
    fun ocultarDesdeCarpetaLaDisuelveSiQuedaUna() {
        val s = state("a", "b").dropOnto(1, 0, cat).hide("a")
        assertEquals(listOf(AppEntry("b")), s.entries)
    }

    @Test
    fun moverYRenombrarDentroDeCarpeta() {
        val s = state("a", "b", "c").dropOnto(1, 0, cat).dropOnto(1, 0, cat)
        val id = (s.entries[0] as FolderEntry).folderId
        val m = s.moveInFolder(id, 0, 2).renameFolder(id, "  Mías ")
        val f = m.folder(id)!!
        assertEquals(listOf("b", "c", "a"), f.apps)
        assertEquals("Mías", f.name)
        assertEquals("Mías", m.renameFolder(id, "   ").folder(id)!!.name)
    }

    @Test
    fun jsonIdaYVuelta() {
        val s = state("a", "b", "c", "d").dropOnto(1, 0, cat).hide("d")
        assertEquals(s, LayoutJson.decode(LayoutJson.encode(s)))
        assertEquals(LayoutState(), LayoutJson.decode("{roto"))
        assertEquals(LayoutState(), LayoutJson.decode(null))
    }

    @Test
    fun jsonConDuplicadosNoRepiteApps() {
        val json = """{"grid":[{"app":"a"},{"folder":"f1","name":"X","apps":["a","b"]}],"hidden":["b","c"]}"""
        val s = LayoutJson.decode(json)
        assertEquals(listOf("a", "b", "c"), s.placed().sorted())
        assertEquals(setOf("c"), s.hidden)
    }

    @Test
    fun temaAutomatico() {
        assertTrue(esNoche(19, 19, 7))
        assertTrue(esNoche(2, 19, 7))
        assertFalse(esNoche(7, 19, 7))
        assertFalse(esNoche(12, 19, 7))
        assertTrue(esNoche(10, 9, 11))
        assertFalse(esNoche(11, 9, 11))
        assertFalse(esNoche(5, 5, 5))
    }
}
