package com.example.practica3.sensors

import android.content.Context
import android.hardware.*

class ShakeSensor(
    ctx: Context,
    private val onShake: () -> Unit
) : SensorEventListener {

    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val accel: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var last = 0L

    fun start() { accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }
    fun stop()  { sm.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val gX = e.values[0] / SensorManager.GRAVITY_EARTH
        val gY = e.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = e.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = Math.sqrt((gX*gX + gY*gY + gZ*gZ).toDouble())
        val now = System.currentTimeMillis()
        if (gForce > 2.2 && now - last > 800) { // umbral + debounce
            last = now
            onShake()
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
