package com.example.slotmaster

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.lights.Light
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import com.example.slotmaster.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlin.random.Random

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicjalizacja czujnika światła
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor == null) {
            binding.tvLightInfo.text = "Czujnik światła niedostępny"
        } else {
            binding.tvLightInfo.text = "Oczekiwanie na dane z czujnika..."
        }

        // Przykładowe dane do wyświetlenia w layoucie
        binding.tvBalance.text = "Saldo: 1000"
        binding.slot1.text = "🍒"
        binding.slot2.text = "🍒"
        binding.slot3.text = "🍒"
        binding.tvLocationInfo.text = "Odwiedzone lokacje: 0/3"
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            if (sensorEvent.sensor.type == Sensor.TYPE_LIGHT) {
                val lightValue = sensorEvent.values[0]
                runOnUiThread {
                    if (lightValue < 50) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                        binding.tvLightInfo.text = "Tryb: Ciemny (${"%.1f".format(lightValue)} lux)"
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                        binding.tvLightInfo.text = "Tryb: Jasny (${"%.1f".format(lightValue)} lux)"
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }
}