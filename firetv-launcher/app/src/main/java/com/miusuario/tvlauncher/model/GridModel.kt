package com.miusuario.tvlauncher.model

/**
 * Estado de la rejilla: orden, carpetas y apps ocultas. Es Kotlin puro e inmutable, para poder probarlo en la JVM.
 *
 * Reglas:
 * - Una app está en un solo sitio: en la rejilla, dentro de una carpeta o en ocultas.
 * - Las carpetas no se anidan.
 * - Una carpeta con 1 o 0 apps se disuelve sola (su app, si queda, ocupa el sitio de la carpeta).
 */
sealed interface GridEntry {
    val id: String
}

data class AppEntry(val pkg: String) : GridEntry {
    override val id: String get() = "app:$pkg"
}

data class FolderEntry(val folderId: String, val name: String, val apps: List<String>) : GridEntry {
    override val id: String get() = "folder:$folderId"
}

/** Categoría de una app, para dar nombre automático a una carpeta. */
enum class Categoria(val nombreCarpeta: String?) {
    STREAMING("Streaming"),
    JUEGOS("Juegos"),
    MUSICA("Música"),
    UTILIDADES("Utilidades"),
    DESCONOCIDA(null),
}

data class LayoutState(
    val entries: List<GridEntry> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val nextFolderId: Int = 1,
) {
    /** Todos los paquetes colocados: en la rejilla, en carpetas u ocultos. */
    fun placed(): Set<String> = buildSet {
        entries.forEach {
            when (it) {
                is AppEntry -> add(it.pkg)
                is FolderEntry -> addAll(it.apps)
            }
        }
        addAll(hidden)
    }

    fun folder(folderId: String): FolderEntry? =
        entries.firstOrNull { it is FolderEntry && it.folderId == folderId } as FolderEntry?

    /**
     * Ajusta el estado a las apps instaladas: quita las desinstaladas (de la rejilla, de su carpeta y de ocultas),
     * disuelve las carpetas que se quedan con 1 o 0 apps y añade las apps nuevas al final.
     * [installed] llega ya ordenado (por nombre) para que el primer arranque salga alfabético.
     */
    fun reconcile(installed: List<String>): LayoutState {
        val present = installed.toSet()
        val kept = entries.mapNotNull { entry ->
            when (entry) {
                is AppEntry -> entry.takeIf { it.pkg in present }
                is FolderEntry -> entry.copy(apps = entry.apps.filter { it in present }.distinct())
            }
        }
        val cleaned = copy(entries = kept, hidden = hidden.filter { it in present }.toSet()).dissolveSmallFolders()
        val placed = cleaned.placed()
        val nuevas = installed.filter { it !in placed }.distinct().map { AppEntry(it) }
        return cleaned.copy(entries = cleaned.entries + nuevas)
    }

    /** Mueve la entrada de [from] a [to] (índices de la rejilla). */
    fun move(from: Int, to: Int): LayoutState {
        if (from !in entries.indices || to !in entries.indices || from == to) return this
        val list = entries.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        return copy(entries = list)
    }

    /** Oculta una app de la rejilla o de una carpeta. */
    fun hide(pkg: String): LayoutState {
        val list = entries.mapNotNull { entry ->
            when (entry) {
                is AppEntry -> entry.takeIf { it.pkg != pkg }
                is FolderEntry -> entry.copy(apps = entry.apps - pkg)
            }
        }
        return copy(entries = list, hidden = hidden + pkg).dissolveSmallFolders()
    }

    /** Vuelve a mostrar una app oculta, al final de la rejilla. */
    fun unhide(pkg: String): LayoutState {
        if (pkg !in hidden) return this
        return copy(entries = entries + AppEntry(pkg), hidden = hidden - pkg)
    }

    /**
     * Suelta la entrada [from] sobre la entrada [onto]:
     * app sobre app crea una carpeta con las dos en el sitio de la de destino; app sobre carpeta la añade al final.
     * Una carpeta no se puede soltar sobre nada (no hay carpetas anidadas): no cambia nada.
     */
    fun dropOnto(from: Int, onto: Int, categoria: (String) -> Categoria): LayoutState {
        if (from !in entries.indices || onto !in entries.indices || from == onto) return this
        val source = entries[from] as? AppEntry ?: return this
        val target = entries[onto]
        val list = entries.toMutableList()
        val replacement: GridEntry
        var next = nextFolderId
        when (target) {
            is AppEntry -> {
                val apps = listOf(target.pkg, source.pkg)
                replacement = FolderEntry("f$next", autoName(apps, categoria), apps)
                next++
            }
            is FolderEntry -> replacement = target.copy(apps = target.apps + source.pkg)
        }
        list[onto] = replacement
        list.removeAt(from)
        return copy(entries = list, nextFolderId = next)
    }

    fun renameFolder(folderId: String, name: String): LayoutState = mapFolder(folderId) {
        it.copy(name = name.trim().ifEmpty { it.name })
    }

    /** Mueve una app dentro de su carpeta. */
    fun moveInFolder(folderId: String, from: Int, to: Int): LayoutState = mapFolder(folderId) {
        if (from !in it.apps.indices || to !in it.apps.indices) return@mapFolder it
        val apps = it.apps.toMutableList()
        apps.add(to, apps.removeAt(from))
        it.copy(apps = apps)
    }

    /** Saca una app de su carpeta y la deja en la rejilla, justo después de la carpeta. */
    fun takeOutOfFolder(folderId: String, pkg: String): LayoutState {
        val index = entries.indexOfFirst { it is FolderEntry && it.folderId == folderId }
        if (index < 0) return this
        val folder = entries[index] as FolderEntry
        if (pkg !in folder.apps) return this
        val list = entries.toMutableList()
        list[index] = folder.copy(apps = folder.apps - pkg)
        list.add(index + 1, AppEntry(pkg))
        return copy(entries = list).dissolveSmallFolders()
    }

    /** Elimina la carpeta: sus apps vuelven a la rejilla en el sitio de la carpeta. */
    fun deleteFolder(folderId: String): LayoutState {
        val index = entries.indexOfFirst { it is FolderEntry && it.folderId == folderId }
        if (index < 0) return this
        val folder = entries[index] as FolderEntry
        val list = entries.toMutableList()
        list.removeAt(index)
        list.addAll(index, folder.apps.map { AppEntry(it) })
        return copy(entries = list)
    }

    private inline fun mapFolder(folderId: String, transform: (FolderEntry) -> FolderEntry): LayoutState =
        copy(entries = entries.map { if (it is FolderEntry && it.folderId == folderId) transform(it) else it })

    private fun dissolveSmallFolders(): LayoutState = copy(entries = entries.flatMap { entry ->
        if (entry is FolderEntry && entry.apps.size <= 1) entry.apps.map { AppEntry(it) } else listOf(entry)
    })

    companion object {
        /** Nombre automático: la categoría de más de la mitad de las apps, o "Carpeta" si no se puede deducir. */
        fun autoName(apps: List<String>, categoria: (String) -> Categoria): String {
            val conocidas = apps.map(categoria).filter { it.nombreCarpeta != null }
            if (conocidas.isEmpty()) return "Carpeta"
            val (mejor, veces) = conocidas.groupingBy { it }.eachCount().maxBy { it.value }
            return if (veces * 2 > apps.size) mejor.nombreCarpeta!! else "Carpeta"
        }

        /** Categoría por el nombre de paquete, cuando la app no declara `android:appCategory`. */
        fun categoriaPorPaquete(pkg: String): Categoria {
            val p = pkg.lowercase()
            return when {
                listOf("spotify", "music", "deezer", "tidal", "pandora", "soundcloud", "audible", "radio")
                    .any { it in p } -> Categoria.MUSICA
                listOf("netflix", "avod", "primevideo", "disney", "hbo", ".max", "hulu", "youtube", "plex",
                    "kodi", "twitch", "crunchyroll", "paramount", "peacock", "jellyfin", "emby", "pluto",
                    "tubi", "dazn", "vix", "appletv", "stremio", "mubi", "filmin", "atresplayer", "rtve")
                    .any { it in p } -> Categoria.STREAMING
                listOf("game", "games", "retroarch", "luna", "xbox", "steamlink", "moonlight", "geforce")
                    .any { it in p } -> Categoria.JUEGOS
                listOf("settings", "files", "filemanager", "browser", "silk", "firefox", "downloader",
                    "vpn", "analiti", "speedtest", "x-plore", "toolbox")
                    .any { it in p } -> Categoria.UTILIDADES
                else -> Categoria.DESCONOCIDA
            }
        }
    }
}
