package com.miusuario.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.miusuario.tvlauncher.ui.HomeScreen

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    /** Apps instaladas, actualizadas o desinstaladas: la rejilla se ajusta sola. */
    private val paquetes = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = vm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filtro = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(paquetes, filtro)
        // Un launcher no se cierra con Atrás. Los paneles registran su propio BackHandler, que tiene prioridad.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        setContent { HomeScreen(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: pulsar Home (o abrir el launcher otra vez) estando aquí vuelve al inicio de la rejilla.
        vm.onHome()
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    override fun onDestroy() {
        unregisterReceiver(paquetes)
        super.onDestroy()
    }
}
