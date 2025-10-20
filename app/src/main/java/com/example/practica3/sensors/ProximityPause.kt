package com.example.practica3.sensors

import android.content.Context
import android.hardware.*

class ProximityPause(
    ctx: Context,
    private val onNear: () -> Unit
) : SensorEventListener {

    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val prox: Sensor? = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    fun start() { prox?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) } }
    fun stop()  { sm.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type != Sensor.TYPE_PROXIMITY) return
        if (e.values.firstOrNull() == 0f) onNear() // 0 = cerca en la mayoría
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
