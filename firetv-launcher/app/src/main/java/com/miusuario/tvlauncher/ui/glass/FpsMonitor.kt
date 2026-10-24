package com.miusuario.tvlauncher.ui.glass

import android.view.Choreographer
import com.miusuario.tvlauncher.Config

/**
 * Mide los fps durante un segundo después de cada interacción (cuando hay animaciones). Si dos medidas seguidas
 * quedan por debajo de [Config.FPS_MINIMOS], llama a [alBajar] para que el launcher reduzca los efectos.
 * Fuera de esas ventanas no registra callbacks, para no despertar la CPU en reposo.
 */
class FpsMonitor(private val alBajar: () -> Unit) : Choreographer.FrameCallback {
    private var activo = false
    private var inicio = 0L
    private var frames = 0
    private var bajas = 0

    var ultimoFps = 0.0
        private set

    fun medir() {
        if (activo) return
        activo = true
        inicio = 0L
        frames = 0
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (inicio == 0L) {
            inicio = frameTimeNanos
        } else {
            frames++
        }
        val dur = frameTimeNanos - inicio
        if (dur >= 1_000_000_000L) {
            activo = false
            ultimoFps = frames * 1e9 / dur
            if (ultimoFps < Config.FPS_MINIMOS) bajas++ else bajas = 0
            if (bajas >= 2) {
                bajas = 0
                alBajar()
            }
        } else {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
}
