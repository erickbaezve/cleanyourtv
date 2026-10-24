package com.miusuario.tvlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.miusuario.tvlauncher.CalidadVidrio
import com.miusuario.tvlauncher.Config
import com.miusuario.tvlauncher.ModoTema
import com.miusuario.tvlauncher.model.LayoutJson
import com.miusuario.tvlauncher.model.LayoutState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher")

data class Ajustes(
    val modoTema: ModoTema = ModoTema.AUTOMATICO,
    val nocheInicio: Int = Config.NOCHE_INICIO,
    val nocheFin: Int = Config.NOCHE_FIN,
    val calidad: CalidadVidrio = CalidadVidrio.ALTA,
)

/** Todo lo que se guarda: ajustes y la disposición de la rejilla (JSON) en DataStore. */
class SettingsStore(private val context: Context) {
    private object Keys {
        val TEMA = stringPreferencesKey("tema")
        val NOCHE_INICIO = intPreferencesKey("noche_inicio")
        val NOCHE_FIN = intPreferencesKey("noche_fin")
        val CALIDAD = stringPreferencesKey("calidad_vidrio")
        val LAYOUT = stringPreferencesKey("layout_json")
    }

    val ajustes: Flow<Ajustes> = context.dataStore.data.map { p ->
        Ajustes(
            modoTema = p[Keys.TEMA]?.let { runCatching { ModoTema.valueOf(it) }.getOrNull() } ?: ModoTema.AUTOMATICO,
            nocheInicio = p[Keys.NOCHE_INICIO] ?: Config.NOCHE_INICIO,
            nocheFin = p[Keys.NOCHE_FIN] ?: Config.NOCHE_FIN,
            calidad = p[Keys.CALIDAD]?.let { runCatching { CalidadVidrio.valueOf(it) }.getOrNull() }
                ?: CalidadVidrio.ALTA,
        )
    }

    val layout: Flow<LayoutState> = context.dataStore.data.map { LayoutJson.decode(it[Keys.LAYOUT]) }

    suspend fun guardarLayout(state: LayoutState) {
        context.dataStore.edit { it[Keys.LAYOUT] = LayoutJson.encode(state) }
    }

    suspend fun guardarAjustes(a: Ajustes) {
        context.dataStore.edit {
            it[Keys.TEMA] = a.modoTema.name
            it[Keys.NOCHE_INICIO] = a.nocheInicio
            it[Keys.NOCHE_FIN] = a.nocheFin
            it[Keys.CALIDAD] = a.calidad.name
        }
    }
}
