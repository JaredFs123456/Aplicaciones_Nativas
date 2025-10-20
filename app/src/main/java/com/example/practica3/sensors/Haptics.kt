package com.example.practica3.sensors

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

fun Context.hapticClick() {
    val vib = getSystemService(Vibrator::class.java) ?: return
    when {
        // Android 10+ (29): efectos predefinidos
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
            vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        }
        // Android 8.0–9 (26–28): one shot
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
            vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        // Android 7.x (<=25): API vieja
        else -> {
            @Suppress("DEPRECATION")
            vib.vibrate(30)
        }
    }
}
