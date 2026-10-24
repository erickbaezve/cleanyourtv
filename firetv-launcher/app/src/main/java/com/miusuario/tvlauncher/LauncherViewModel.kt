package com.miusuario.tvlauncher

import android.app.Application
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.miusuario.tvlauncher.data.Ajustes
import com.miusuario.tvlauncher.data.AppInfo
import com.miusuario.tvlauncher.data.AppRepository
import com.miusuario.tvlauncher.data.SettingsStore
import com.miusuario.tvlauncher.model.Categoria
import com.miusuario.tvlauncher.model.LayoutState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    val repo = AppRepository(app)
    private val store = SettingsStore(app)
    private val mutex = Mutex()
    private var cargado = false
    private var refreshJob: Job? = null

    /** Solo para las pruebas de capturas: apps falsas en lugar de las del PackageManager. */
    @VisibleForTesting
    internal var appsFijas: List<AppInfo>? = null

    private val _apps = MutableStateFlow<Map<String, AppInfo>>(emptyMap())
    val apps: StateFlow<Map<String, AppInfo>> = _apps.asStateFlow()

    private val _layout = MutableStateFlow(LayoutState())
    val layout: StateFlow<LayoutState> = _layout.asStateFlow()

    val ajustes: StateFlow<Ajustes> = store.ajustes.stateIn(viewModelScope, SharingStarted.Eagerly, Ajustes())

    /** Se emite al pulsar Home estando ya en el launcher: cierra paneles y vuelve al principio. */
    private val _home = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val home = _home.asSharedFlow()

    init {
        refresh()
    }

    fun onHome() {
        _home.tryEmit(Unit)
    }

    /** Relee las apps instaladas y ajusta la rejilla: desinstaladas fuera (también de carpetas), nuevas al final. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val lista = appsFijas ?: repo.loadApps()
            mutex.withLock {
                val base = if (cargado) _layout.value else store.layout.first()
                cargado = true
                _apps.value = lista.associateBy { it.pkg }
                val nuevo = base.reconcile(lista.map { it.pkg })
                _layout.value = nuevo
                if (nuevo != base) store.guardarLayout(nuevo)
            }
        }
    }

    fun categoria(pkg: String): Categoria = _apps.value[pkg]?.categoria ?: LayoutState.categoriaPorPaquete(pkg)

    /** Cambio en vivo (por ejemplo, mientras se mueve un icono). Se guarda con [guardar]. */
    fun vista(state: LayoutState) {
        _layout.value = state
    }

    /** Aplica y guarda un cambio de la rejilla. */
    fun cambiar(transform: (LayoutState) -> LayoutState) {
        _layout.value = transform(_layout.value)
        guardar()
    }

    fun guardar() {
        val snapshot = _layout.value
        viewModelScope.launch { store.guardarLayout(snapshot) }
    }

    fun guardarAjustes(a: Ajustes) {
        viewModelScope.launch { store.guardarAjustes(a) }
    }

    fun launch(pkg: String): Boolean = repo.launch(pkg)

    suspend fun heroBanner(pkg: String): ImageBitmap? = repo.heroBanner(pkg)

    suspend fun detalles(pkg: String) = repo.detalles(pkg)
}
