package com.example.slotmaster

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.slotmaster.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

class MainActivity : AppCompatActivity(), SensorEventListener {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lightSensor: Sensor? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    
    private var balance: Int = 1000
    private var lastShakeTime: Long = 0
    private val SHAKE_THRESHOLD = 15f
    private val SHAKE_TIMEOUT = 1000
    
    // Sloty jako ImageView
    private lateinit var slot1: ImageView
    private lateinit var slot2: ImageView
    private lateinit var slot3: ImageView
    
    private val targetLocations = listOf(
        TargetLocation(52.2297, 21.0122, 1500.0, false),
        TargetLocation(50.0647, 19.9450, 1500.0, false),
        TargetLocation(51.1079, 17.0385, 1500.0, false)
    )
    
    private val PREFS_NAME = "SlotMasterPrefs"
    private val KEY_DARK_MODE = "dark_mode"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ZAŁADUJ ZAPISANY MOTYW NA SAMYM POCZĄTKU
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val darkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Inicjalizuj sloty
        slot1 = binding.slot1
        slot2 = binding.slot2
        slot3 = binding.slot3
        
        // Załaduj zapisany stan gry
        loadGameState()
        
        initializeSensors()
        initializeLocation()
        updateUI()
        setupClickListeners()
    }
    
    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        // Akcelerometr
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
        
        // Czujnik światła - AUTOMATYCZNA ZMIANA TRYBU
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            binding.tvLightInfo.text = "Czujnik światła: AKTYWNY"
        } else {
            binding.tvLightInfo.text = "Czujnik światła: NIE DOSTĘPNY"
        }
    }
    
    private fun initializeLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    checkLocationRewards(location)
                }
            }
        }
        
        startLocationUpdates()
    }
    
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && 
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
            return
        }
        
        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            null
        )
    }
    
    private fun checkLocationRewards(currentLocation: Location) {
        targetLocations.forEachIndexed { index, target ->
            if (!target.visited) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    currentLocation.latitude,
                    currentLocation.longitude,
                    target.latitude,
                    target.longitude,
                    results
                )
                
                val distance = results[0]
                if (distance <= target.radius) {
                    target.visited = true
                    balance += 100
                    saveGameState()
                    updateUI()
                    Toast.makeText(
                        this, 
                        "Zdobyto 100 punktów za odwiedzenie lokalizacji ${index + 1}!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.btnSpin.setOnClickListener {
            spinSlots()
        }
        
        binding.btnReset.setOnClickListener {
            resetGame()
        }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            when (sensorEvent.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    handleAccelerometer(sensorEvent.values)
                }
                Sensor.TYPE_LIGHT -> {
                    handleLightSensor(sensorEvent.values[0])
                }
            }
        }
    }
    
    private fun handleAccelerometer(values: FloatArray) {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        
        val acceleration = Math.sqrt(
            (x * x + y * y + z * z).toDouble()
        ).toFloat()
        
        val currentTime = System.currentTimeMillis()
        
        if (acceleration > SHAKE_THRESHOLD && 
            currentTime - lastShakeTime > SHAKE_TIMEOUT) {
            lastShakeTime = currentTime
            spinSlots()
        }
    }
    
    private fun handleLightSensor(lightValue: Float) {
        runOnUiThread {
            // AUTOMATYCZNA ZMIANA TRYBU z zapisywaniem stanu
            if (lightValue < 50) {
                if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    // ZAPISZ STAN MOTYWU
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_DARK_MODE, true).apply()
                    binding.tvLightInfo.text = "🌙 Tryb: CIEMNY (${"%.1f".format(lightValue)} lux)"
                } else {
                    binding.tvLightInfo.text = "🌙 Tryb: CIEMNY (${"%.1f".format(lightValue)} lux)"
                }
            } else {
                if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    // ZAPISZ STAN MOTYWU
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_DARK_MODE, false).apply()
                    binding.tvLightInfo.text = "☀️ Tryb: JASNY (${"%.1f".format(lightValue)} lux)"
                } else {
                    binding.tvLightInfo.text = "☀️ Tryb: JASNY (${"%.1f".format(lightValue)} lux)"
                }
            }
        }
    }
    
    private fun spinSlots() {
        if (balance < 10) {
            Toast.makeText(this, "Za mało punktów!", Toast.LENGTH_SHORT).show()
            return
        }
        
        balance -= 10
        saveGameState()
        
        // Efekt kręcenia - fioletowe tło + pytajnik
        slot1.setBackgroundColor(ContextCompat.getColor(this, R.color.neon_purple))
        slot2.setBackgroundColor(ContextCompat.getColor(this, R.color.neon_purple))
        slot3.setBackgroundColor(ContextCompat.getColor(this, R.color.neon_purple))
        
        // Pytajniki zamiast przezroczystości
        slot1.setImageResource(android.R.drawable.ic_menu_help)
        slot2.setImageResource(android.R.drawable.ic_menu_help)
        slot3.setImageResource(android.R.drawable.ic_menu_help)
        
        binding.root.postDelayed({
            val symbols = listOf(
                R.drawable.cherry,
                R.drawable.lemon, 
                R.drawable.orange,
                R.drawable.star,
                R.drawable.seven,
                R.drawable.bell
            )
            
            val result1 = symbols.random()
            val result2 = symbols.random()
            val result3 = symbols.random()
            
            slot1.setImageResource(result1)
            slot2.setImageResource(result2)
            slot3.setImageResource(result3)
            
            // Przywróć ciemne tło
            slot1.setBackgroundResource(R.drawable.slot_border_dark)
            slot2.setBackgroundResource(R.drawable.slot_border_dark)
            slot3.setBackgroundResource(R.drawable.slot_border_dark)
            
            checkWin(result1, result2, result3)
            updateUI()
        }, 1000)
    }
    
    private fun checkWin(symbol1: Int, symbol2: Int, symbol3: Int) {
        var winAmount = 0
        
        when {
            symbol1 == R.drawable.seven && symbol2 == R.drawable.seven && symbol3 == R.drawable.seven -> {
                winAmount = 500
                Toast.makeText(this, "JACKPOT! 777!", Toast.LENGTH_LONG).show()
            }
            symbol1 == symbol2 && symbol2 == symbol3 -> {
                winAmount = 100
                Toast.makeText(this, "Wygrana! Trzy takie same!", Toast.LENGTH_SHORT).show()
            }
            symbol1 == symbol2 || symbol2 == symbol3 || symbol1 == symbol3 -> {
                winAmount = 20
                Toast.makeText(this, "Mała wygrana!", Toast.LENGTH_SHORT).show()
            }
        }
        
        balance += winAmount
        saveGameState()
    }
    
    private fun resetGame() {
        balance = 1000
        targetLocations.forEach { it.visited = false }
        saveGameState()
        updateUI()
        Toast.makeText(this, "Gra zresetowana!", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateUI() {
        binding.tvBalance.text = "Saldo: $balance"
        
        if (slot1.drawable == null) slot1.setImageResource(R.drawable.cherry)
        if (slot2.drawable == null) slot2.setImageResource(R.drawable.cherry)
        if (slot3.drawable == null) slot3.setImageResource(R.drawable.cherry)
        
        val visitedCount = targetLocations.count { it.visited }
        binding.tvLocationInfo.text = "Odwiedzone lokacje: $visitedCount/${targetLocations.size}"
    }
    
    // ZAPISYWANIE STANU GRY
    private fun saveGameState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("balance", balance)
            putLong("lastShakeTime", lastShakeTime)
            putBoolean("location1_visited", targetLocations[0].visited)
            putBoolean("location2_visited", targetLocations[1].visited)
            putBoolean("location3_visited", targetLocations[2].visited)
            apply()
        }
    }
    
    private fun loadGameState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        balance = prefs.getInt("balance", 1000)
        lastShakeTime = prefs.getLong("lastShakeTime", 0)
        targetLocations[0].visited = prefs.getBoolean("location1_visited", false)
        targetLocations[1].visited = prefs.getBoolean("location2_visited", false)
        targetLocations[2].visited = prefs.getBoolean("location3_visited", false)
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        startLocationUpdates()
    }
    
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        saveGameState()
    }
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }
}

data class TargetLocation(
    val latitude: Double,
    val longitude: Double,
    val radius: Double,
    var visited: Boolean
)
