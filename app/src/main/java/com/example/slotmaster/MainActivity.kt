package com.example.slotmaster

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lightSensor: Sensor? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var balance: Int = 10000
    private var lastShakeTime: Long = 0
    private val SHAKE_THRESHOLD = 15f
    private val SHAKE_TIMEOUT = 1000
    private val KEY_DARK_MODE = "dark_mode"

    // Sloty jako ImageView
    private lateinit var slot1: ImageView
    private lateinit var slot2: ImageView
    private lateinit var slot3: ImageView
    private lateinit var slot4: ImageView
    private lateinit var slot5: ImageView
    private lateinit var slot6: ImageView
    private lateinit var slot7: ImageView
    private lateinit var slot8: ImageView
    private lateinit var slot9: ImageView

    // NOWE: Zmienne dla systemu linii
    private var baseBet = 10
    private var selectedLines = 1
    private val symbols = listOf(
        R.drawable.cherry,
        R.drawable.lemon,
        R.drawable.orange,
        R.drawable.star,
        R.drawable.seven,
        R.drawable.bell
    )
    private val symbolValues = mapOf(
        R.drawable.cherry to 2,
        R.drawable.lemon to 3,
        R.drawable.orange to 4,
        R.drawable.star to 15,
        R.drawable.seven to 50,
        R.drawable.bell to 10
    )

    // NOWE: Linie wygrywajÄce (indeksy slotĂłw)
    private val winningLines = listOf(
        listOf(0, 1, 2),  // Linia 1 - gĂłrny wiersz
        listOf(3, 4, 5),  // Linia 2 - Ĺrodkowy wiersz
        listOf(6, 7, 8),  // Linia 3 - dolny wiersz
        listOf(0, 4, 8),  // Linia 4 - przekÄtna \
        listOf(2, 4, 6)   // Linia 5 - przekÄtna /
    )

    private val targetLocations = listOf(
        TargetLocation(52.2297, 21.0122, 15000.0, false),
        TargetLocation(50.0647, 19.9450, 15000.0, false),
        TargetLocation(49.6067, 20.7031, 15000.0, false)
    )

    private val PREFS_NAME = "SlotMasterPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ZAĹADUJ ZAPISANY MOTYW NA SAMYM POCZÄTKU
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val darkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicjalizuj sloty jako ImageView
        slot1 = binding.slot1
        slot2 = binding.slot2
        slot3 = binding.slot3
        slot4 = binding.slot4
        slot5 = binding.slot5
        slot6 = binding.slot6
        slot7 = binding.slot7
        slot8 = binding.slot8
        slot9 = binding.slot9

        // ZaĹaduj zapisany stan gry PRZED inicjalizacjÄ UI
        loadGameState()

        initializeSensors()
        initializeLocation()
        updateUI()
        setupClickListeners()
        setupLineCheckboxes()
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Akcelerometr
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Czujnik ĹwiatĹa
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            binding.tvLightInfo.text = "Czujnik ĹwiatĹa: AKTYWNY"
        } else {
            binding.tvLightInfo.text = "Czujnik ĹwiatĹa: NIE DOSTÄPNY"
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
                        "Zdobyto 100 punktĂłw za odwiedzenie lokalizacji nr${index + 1}!",
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

        // Listenery dla checkboxĂłw linii
        getLineCheckboxesList().forEach { checkbox ->
            checkbox.setOnCheckedChangeListener { _, _ ->
                updateSelectedLines()
            }
        }
    }

    private fun setupLineCheckboxes() {
        getLineCheckboxesList().forEachIndexed { index, checkbox ->
            checkbox.text = "Linia ${index + 1}"
        }
        updateSelectedLines()
    }

    private fun getLineCheckboxesList() = listOf(
        binding.cbLine1, binding.cbLine2, binding.cbLine3,
        binding.cbLine4, binding.cbLine5
    )

    private fun getSlotsList() = listOf(
        slot1, slot2, slot3,
        slot4, slot5, slot6,
        slot7, slot8, slot9
    )

    private fun updateSelectedLines() {
        val lineCheckboxes = getLineCheckboxesList()
        selectedLines = lineCheckboxes.count { it.isChecked }
        if (selectedLines == 0) {
            binding.cbLine1.isChecked = true
            selectedLines = 1
        }
        updateBetInfo()
    }

    private fun updateBetInfo() {
        val totalBet = baseBet * selectedLines
        binding.tvBetInfo.text = "Stawka: $totalBet punktĂłw ($selectedLines linii)"
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

        val acceleration = sqrt(
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
            if (lightValue < 15) {
                if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_DARK_MODE, true).apply()
                    binding.tvLightInfo.text = "đ Tryb: CIEMNY (${"%.1f".format(lightValue)} lux)"
                }
            } else {
                if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_NO) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_DARK_MODE, false).apply()
                    binding.tvLightInfo.text = "âď¸ Tryb: JASNY (${"%.1f".format(lightValue)} lux)"
                }
            }
        }
    }

    // NOWA ANIMACJA KRÄCENIA
    private fun spinSlots() {
        val totalBet = baseBet * selectedLines

        if (balance < totalBet) {
            Toast.makeText(this, "Za maĹo punktĂłw!", Toast.LENGTH_SHORT).show()
            return
        }

        balance -= totalBet
        saveGameState()

        val slots = getSlotsList()
        val handler = Handler(Looper.getMainLooper())
        binding.btnSpin.isEnabled = false

        // Reset animacji
        slots.forEach { slot ->
            slot.rotationY = 0f
            slot.scaleX = 1.0f
            slot.scaleY = 1.0f
            slot.alpha = 1.0f
        }

        // Faza 1: Szybkie krÄcenie z efektem rozmycia
        applySpinEffects(slots)

        var fastSpinCount = 0
        val maxFastSpins = 20

        val fastSpinRunnable = object : Runnable {
            override fun run() {
                slots.forEach { slot ->
                    val randomSymbol = symbols.random()
                    slot.setImageResource(randomSymbol)
                    slot.rotationY = (slot.rotationY + 45f) % 360f

                    // Efekt "migania" co 3 klatki
                    if (fastSpinCount % 3 == 0) {
                        slot.alpha = if (slot.alpha == 0.7f) 1.0f else 0.7f
                    }
                }

                fastSpinCount++
                if (fastSpinCount < maxFastSpins) {
                    handler.postDelayed(this, 40L)
                } else {
                    startSlowSpinPhase(handler)
                }
            }
        }

        handler.post(fastSpinRunnable)
    }

    private fun applySpinEffects(slots: List<ImageView>) {
        slots.forEach { slot ->
            // Efekt rozmycia
            slot.animate()
                .alpha(0.7f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100L)
                .start()
        }
    }

    private fun startSlowSpinPhase(handler: Handler) {
        val slots = getSlotsList()
        val finalResults = mutableListOf<Int>()

        repeat(9) { finalResults.add(symbols.random()) }

        var slowSpinCount = 0
        val maxSlowSpins = 15

        val slowSpinRunnable = object : Runnable {
            override fun run() {
                slots.forEachIndexed { index, slot ->
                    if (slowSpinCount >= index + 5) { // Prostsze zatrzymywanie
                        // Slot zatrzymany
                        slot.setImageResource(finalResults[index])
                        slot.tag = finalResults[index]
                        slot.alpha = 1.0f
                        slot.scaleX = 1.0f
                        slot.scaleY = 1.0f
                        slot.rotationY = 0f
                    } else {
                        // Nadal krÄÄ
                        val randomSymbol = symbols.random()
                        slot.setImageResource(randomSymbol)
                        slot.rotationY = slot.rotationY + 20f
                    }
                }

                slowSpinCount++

                if (slowSpinCount < maxSlowSpins) {
                    handler.postDelayed(this, 80L)
                } else {
                    // Koniec animacji
                    resetAllSlotsAfterSpin()
                    checkWin()
                    updateUI()
                    binding.btnSpin.isEnabled = true
                }
            }
        }

        handler.post(slowSpinRunnable)
    }

    private fun resetAllSlotsAfterSpin() {
        val slots = getSlotsList()
        slots.forEach { slot ->
            slot.alpha = 1.0f
            slot.scaleX = 1.0f
            slot.scaleY = 1.0f
            slot.rotationY = 0f
            slot.setBackgroundResource(R.drawable.slot_border_dark)
        }
    }

    private fun checkWin() {
        val slots = getSlotsList()
        val slotDrawables = slots.map { it.tag as? Int ?: R.drawable.cherry }

        var totalWin = 0
        val lineCheckboxes = getLineCheckboxesList()
        val winningLineIndices = mutableListOf<Int>()

        winningLines.forEachIndexed { index, line ->
            if (lineCheckboxes[index].isChecked) {
                val lineSymbols = line.map { slotDrawables[it] }
                if (lineSymbols.all { it == lineSymbols[0] }) {
                    val symbolValue = symbolValues[lineSymbols[0]] ?: 0
                    val lineWin = symbolValue * baseBet
                    totalWin += lineWin
                    winningLineIndices.add(index)

                    Toast.makeText(
                        this,
                        "Wygrana linia ${index + 1}! +$lineWin",
                        Toast.LENGTH_SHORT
                    ).show()

                    // PodĹwietl wygranÄ liniÄ
                    highlightWinningLine(line)
                }
            }
        }

        if (totalWin > 0) {
            balance += totalWin
            saveGameState()
            Toast.makeText(
                this,
                "Wygrana: $totalWin punktĂłw!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun highlightWinningLine(lineIndices: List<Int>) {
        val slots = getSlotsList()
        val handler = Handler(Looper.getMainLooper())

        lineIndices.forEach { index ->
            val slot = slots[index]

            // Zachowaj oryginalne tĹo z borderem
            val originalBackground = slot.background

            // Animacja powiÄkszenia
            slot.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(200L)
                .withEndAction {
                    slot.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200L)
                        .start()
                }
                .start()

            // Efekt migania - UĹťYJ PRZEZROCZYSTEGO KOLORU zamiast zmieniaÄ tĹo
            ObjectAnimator.ofArgb(
                slot,
                "backgroundColor",
                ContextCompat.getColor(this, R.color.neon_green_transparent), // UĹźyj przezroczystego koloru
                ContextCompat.getColor(this, android.R.color.transparent)
            ).apply {
                duration = 500
                repeatCount = 2
                start()
            }

            // PrzywrĂłÄ oryginalne tĹo po animacji
            handler.postDelayed({
                slot.background = originalBackground
            }, 1500) // Po zakoĹczeniu wszystkich animacji
        }
    }

    private fun resetGame() {
        balance = 1000
        targetLocations.forEach { it.visited = false }

        selectedLines = 1
        getLineCheckboxesList().forEachIndexed { index, checkbox ->
            checkbox.isChecked = index == 0
        }

        saveGameState()
        updateUI()
        Toast.makeText(this, "Gra zresetowana!", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        binding.tvBalance.text = "Saldo: $balance"
        updateBetInfo()

        getSlotsList().forEach { slot ->
            if (slot.drawable == null) slot.setImageResource(R.drawable.cherry)
        }

        val visitedCount = targetLocations.count { it.visited }
        binding.tvLocationInfo.text = "Odwiedzone lokacje: $visitedCount/${targetLocations.size}"
    }

    private fun saveGameState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("balance", balance)
            putLong("lastShakeTime", lastShakeTime)
            putBoolean("location1_visited", targetLocations[0].visited)
            putBoolean("location2_visited", targetLocations[1].visited)
            putBoolean("location3_visited", targetLocations[2].visited)
            putInt("selectedLines", selectedLines)
            getLineCheckboxesList().forEachIndexed { index, checkbox ->
                putBoolean("line${index+1}_checked", checkbox.isChecked)
            }
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

        selectedLines = prefs.getInt("selectedLines", 1)
        getLineCheckboxesList().forEachIndexed { index, checkbox ->
            checkbox.isChecked = prefs.getBoolean("line${index+1}_checked", index == 0)
        }
    }

    private fun updateDailyResultInDatabase() {
        if (gameHistoryManager.isTodaySaved()) {
            // Jeśli już zapisano dzisiaj - USUŃ stary wpis
            gameHistoryManager.deleteTodaysRecord()
        }
        // ZAPISZ nowy wpis z aktualnymi danymi
        gameHistoryManager.saveDailyResult(
            finalBalance = balance,
            spinsCount = spinsCount,
            biggestWin = biggestWin
        )
    }

    private fun saveDailyResultIfNeeded() {
        if (!gameHistoryManager.isTodaySaved()) {
            val success = gameHistoryManager.saveDailyResult(
                finalBalance = balance,
                spinsCount = spinsCount,
                biggestWin = biggestWin
            )

            if (success) {
                Toast.makeText(this, "Wynik dnia zapisany!", Toast.LENGTH_SHORT).show()
                // Resetuj liczniki na nowy dzień
                spinsCount = 0
                biggestWin = 0
            }
        }
    }

    // Sprawdza czy trzeba zapisać poprzedni dzień (przy starcie aplikacji)
    private fun checkAndSavePreviousDay() {
        // Możesz dodać logikę sprawdzania czy minął dzień
        // Na razie po prostu sprawdza czy dzisiaj już zapisano
        saveDailyResultIfNeeded()
    }

    // Pokazuje historię wyników (możesz podpiąć pod nowy przycisk)
    private fun showHistory() {
        val history = gameHistoryManager.getRecentHistory(7)

        if (history.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("📊 Historia Gier")
                .setMessage("Brak zapisanych wyników z ostatnich 7 dni.\n\nGraj dalej, a wyniki pojawią się tutaj! 🎰")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val historyText = history.joinToString("\n\n") { record ->
            "📅 ${formatDisplayDate(record.gameDate)}\n" +
                    "💰 Saldo: ${record.finalBalance} punktów\n" +
                    "🎰 Spiny: ${record.spinsCount}\n" +
                    "🏆 Największa wygrana: ${record.biggestWin}\n" +
                    "⏰ Godzina: ${formatTime(record.createdAt)}"
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("📊 Historia 7 dni")
            .setMessage(historyText)
            .setPositiveButton("OK", null)
            .setNeutralButton("Wyczyść historię") { dialog, _ ->
                showClearHistoryConfirmation()
                dialog.dismiss()
            }
            .show()
    }


    private fun formatDisplayDate(dbDate: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val date = inputFormat.parse(dbDate)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dbDate
        }
    }

    private fun formatTime(dateTime: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateTime)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            ""
        }
    }

    private fun showClearHistoryConfirmation() {
        android.app.AlertDialog.Builder(this)
            .setTitle("🧹 Wyczyść historię")
            .setMessage("Czy na pewno chcesz usunąć całą historię gier? Tej operacji nie można cofnąć.")
            .setPositiveButton("TAK, wyczyść") { dialog, _ ->
                clearAllHistory()
                dialog.dismiss()
            }
            .setNegativeButton("NIE, zachowaj", null)
            .show()
    }

    private fun clearAllHistory() {

        val success = gameHistoryManager.clearAllHistory()

        if (success) {
            Toast.makeText(this, "Historia wyczyszczona!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Błąd podczas czyszczenia historii", Toast.LENGTH_SHORT).show()
        }
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
