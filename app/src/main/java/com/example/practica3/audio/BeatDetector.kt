package com.example.practica3.audio

import android.media.audiofx.Visualizer
import kotlin.math.hypot
import kotlin.math.min

/**
 * Detector simple de beats con umbral dinámico.
 * - Mide energía en bandas bajas (bins 2..12 aprox. <300Hz).
 * - Mantiene un baseline (EMA) y dispara si energy > baseline * ratio.
 */
class BeatDetector(
    private val onBeat: () -> Unit,
    private val minIntervalMs: Long = 120,  // anti-rebote
    private val ratio: Float = 1.8f         // sensibilidad (↓ = más sensible)
) {
    private var vis: Visualizer? = null
    private var lastBeat = 0L
    private var baseline = 0f

    fun attachToSession(sessionId: Int) {
        release()
        if (sessionId == 0) return
        try {
            val v = Visualizer(sessionId)
            v.captureSize = Visualizer.getCaptureSizeRange()[1]
            v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)

            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vz: Visualizer?, data: ByteArray?, rate: Int) {
                    // Usamos FFT, no waveform.
                }

                override fun onFftDataCapture(vz: Visualizer?, fft: ByteArray?, rate: Int) {
                    if (fft == null) return

                    // Energía en bajas frecuencias (bins pares: re/im)
                    var energy = 0f
                    var i = 2
                    val end = min(fft.size - 1, 24) // bins 2..12
                    while (i < end) {
                        val re = fft[i].toFloat()
                        val im = fft[i + 1].toFloat()
                        energy += hypot(re, im)
                        i += 2
                    }

                    // Baseline exponencial
                    if (baseline == 0f) baseline = energy
                    baseline = 0.90f * baseline + 0.10f * energy

                    val now = System.currentTimeMillis()
                    if (energy > baseline * ratio && now - lastBeat > minIntervalMs) {
                        lastBeat = now
                        onBeat()
                    }
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)

            v.enabled = true
            vis = v
        } catch (_: Throwable) {
            // En algunos dispositivos podría requerir RECORD_AUDIO; lo ignoramos aquí.
        }
    }

    fun start()   { vis?.enabled = true }
    fun stop()    { vis?.enabled = false }
    fun release() { vis?.release(); vis = null; baseline = 0f }
}
