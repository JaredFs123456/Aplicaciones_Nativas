package com.example.practica3.flash

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.core.content.ContextCompat

class TorchController(private val ctx: Context) {

    private val cm = ctx.getSystemService(CameraManager::class.java)
    private var cameraId: String? = null
    private var lastMs = 0L

    init {
        // Buscar una cámara con flash (idealmente trasera)
        runCatching {
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && (facing == CameraCharacteristics.LENS_FACING_BACK || cameraId == null)) {
                    cameraId = id
                }
            }
        }
    }

    private fun hasFlash(): Boolean =
        cameraId != null &&
                ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun on() {
        if (!hasFlash() || !hasPermission() || Build.VERSION.SDK_INT < 23) return
        runCatching { cm.setTorchMode(cameraId!!, true) }
    }

    fun off() {
        if (!hasFlash() || !hasPermission() || Build.VERSION.SDK_INT < 23) return
        runCatching { cm.setTorchMode(cameraId!!, false) }
    }

    /**
     * Enciende brevemente la linterna, pero con un enfriamiento mínimo entre pulsos
     * para evitar saturar la API / parpadeo excesivo.
     */
    fun pulse(minIntervalMs: Long = 70, onMs: Long = 50) {
        val now = System.currentTimeMillis()
        if (now - lastMs < minIntervalMs) return
        lastMs = now
        on()
        // apagar de forma diferida
        android.os.Handler(ctx.mainLooper).postDelayed({ off() }, onMs)
    }
}
