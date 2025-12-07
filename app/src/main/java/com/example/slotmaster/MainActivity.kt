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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import android.content.Intent


class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lightSensor: Sensor? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private var balance: Int = 5000
    private var lastShakeTime: Long = 0
    private val SHAKE_THRESHOLD = 15f
    private val SHAKE_TIMEOUT = 1000
    private val KEY_DARK_MODE = "dark_mode"

    // Zmienne do bazy danych - TERAZ Firebird API
    private lateinit var firebirdApiManager: FirebirdApiManager
    private var spinsCount = 0
    private var biggestWin = 0

    // Coroutine scope dla operacji sieciowych
    private val scope = CoroutineScope(Dispatchers.Main)

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

    // Zmienne dla systemu linii
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

    // Linie wygrywające (indeksy slotów)
    private val winningLines = listOf(
        listOf(0, 1, 2),  // Linia 1 - górny wiersz
        listOf(3, 4, 5),  // Linia 2 - środkowy wiersz
        listOf(6, 7, 8),  // Linia 3 - dolny wiersz
        listOf(0, 4, 8),  // Linia 4 - przekątna \
        listOf(2, 4, 6)   // Linia 5 - przekątna /
    )

    private val targetLocations = listOf(
        TargetLocation(49.6092, 20.7045, 100.0, false), // ANS
        TargetLocation(49.6251, 20.6912, 150.0, false), // Rynek
        TargetLocation(49.6092, 20.7134, 100.0, false) // Lidl lukasinskiego
    )

    private val PREFS_NAME = "SlotMasterPrefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // 1. Theme
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val darkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        // 2. Binding INICJALIZACJA - TO MUSI BYĆ NAJPIERW!
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 3. Inicjalizacja UI elementów
        slot1 = binding.slot1
        slot2 = binding.slot2
        slot3 = binding.slot3
        slot4 = binding.slot4
        slot5 = binding.slot5
        slot6 = binding.slot6
        slot7 = binding.slot7
        slot8 = binding.slot8
        slot9 = binding.slot9

        // 4. Inicjalizacja managerów
        firebirdApiManager = FirebirdApiManager(this)

        // 5. Ładowanie danych
        scope.launch {
            firebirdApiManager.getSharedUserId()
            loadGameState()
            checkAndSavePreviousDay()
        }

        // 6. Inicjalizacja systemów
        initializeSensors()
        initializeLocation()
        setupClickListeners()
        setupLineCheckboxes()
        setupBottomNavigation()

        // 7. Update UI
        updateUI()
    }

    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Akcelerometr
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

        // Czujnik światła
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            binding.tvLightInfo.text = "Czujnik światła: AKTYWNY"
        } else {
            binding.tvLightInfo.text = "Czujnik światła: NIE DOSTĘPNY"
        }
    }
    private fun testApiConnection() {
        scope.launch {
            Log.d("MainActivity", "🧪 Rozpoczynam test API...")

            // Test 1: Połączenie
            val connectionOk = firebirdApiManager.testConnection()

            if (connectionOk) {
                // Test 2: Zapis
                val saveOk = firebirdApiManager.saveDailyResult(1000, 5, 100)

                runOnUiThread {
                    if (saveOk) {
                        Toast.makeText(this@MainActivity, "✅ API DZIAŁA POPRAWNIE!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "❌ API ODPOWIADA, ALE ZAPIS NIE DZIAŁA", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "❌ BRAK POŁĄCZENIA Z API", Toast.LENGTH_LONG).show()
                }
            }
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


    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_history -> {
                    showHistory()
                    true
                }
                R.id.nav_test -> {
                    testApiConnection()
                    true
                }
                R.id.nav_users -> {
                    showUserManagementDialog()
                    true
                }
                R.id.nav_settings -> {
                    showMoreOptionsDialog()
                    true
                }
                else -> false
            }
        }
    }

    // Nowa metoda do zarządzania userami
    private fun showUserManagementDialog() {
        val options = arrayOf("👥 Wybierz Usera", "🆕 Nowy User", "👤 Aktualny User")

        AlertDialog.Builder(this)
            .setTitle("👨‍💼 Zarządzanie Userami")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showUserSelection()
                    1 -> showCreateUserDialog()
                    2 -> showCurrentUserInfo()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    // Nowa metoda z dodatkowymi opcjami
    private fun showMoreOptionsDialog() {
        val options = arrayOf("🔧 Test API", "📊 Historia", "🔄 Reset Gry", "ℹ️ Informacje")

        AlertDialog.Builder(this)
            .setTitle("⚙️ Więcej Opcji")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> testApiConnection()
                    1 -> showHistory()
                    2 -> resetGame()
                    3 -> showGameInfoDialog()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
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
                        "Zdobyto 100 punktów za odwiedzenie lokalizacji nr${index + 1}!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showGameInfoDialog() {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.game_info_title))
            .setMessage(getString(R.string.game_info_message))
            .setPositiveButton(getString(R.string.ok_button)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun setupClickListeners() {


        binding.btnSpin.setOnClickListener {
            spinSlots()
        }

//        binding.btnReset.setOnClickListener {
//            resetGame()
//        }

        binding.btnInfo.setOnClickListener {
            showGameInfoDialog()
        }

        binding.btnShowMap.setOnClickListener {
            showMap()
        }

//        binding.btnHistory.setOnClickListener {
//            showHistory()
//        }
//
//        binding.btnSelectUser.setOnClickListener {
//            showUserSelection()
//        }
//
//        binding.btnCreateUser.setOnClickListener {
//            showCreateUserDialog()
//        }
//
//        binding.btnCurrentUser.setOnClickListener {
//            showCurrentUserInfo()
//        }
//
//        binding.btnTestConnection.setOnClickListener {
//            testApiConnection()
//        }

        // Listenery dla checkboxów linii
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
        binding.tvBetInfo.text = "Stawka: $totalBet punktów ($selectedLines linii)"
    }


    private fun showCurrentUserInfo() {
        scope.launch {
            val userId = firebirdApiManager.getCurrentUserId() // 🔽 ZMIANA
            val userName = extractUserNameFromId(userId)

            runOnUiThread {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("👤 Aktualny User")
                    .setMessage("User ID: $userId\nNazwa: $userName")
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Zmień usera") { dialog, _ ->
                        showUserSelection()
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }
    private fun showMap() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Brak uprawnień do lokalizacji", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, MapActivity::class.java)

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                intent.putExtra("CURRENT_LOCATION", location)
                // 🔽 PRZEKAŻ STAN ODWIEDZONYCH LOKALIZACJI
                intent.putExtra("VISITED_LOCATIONS", booleanArrayOf(
                    targetLocations[0].visited,
                    targetLocations[1].visited,
                    targetLocations[2].visited
                ))
                startActivity(intent)
            } ?: run {
                Toast.makeText(this, "Nie można pobrać lokalizacji", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Metoda: Wybór usera z listy
    private fun showUserSelection() {
        scope.launch {
            val users = firebirdApiManager.getUsers()

            runOnUiThread {
                if (users.isEmpty()) {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("👥 Wybierz Usera")
                        .setMessage("Brak zapisanych userów.\nUtwórz nowego usera!")
                        .setPositiveButton("Utwórz nowego") { dialog, _ ->
                            showCreateUserDialog()
                            dialog.dismiss()
                        }
                        .setNegativeButton("Anuluj", null)
                        .show()
                    return@runOnUiThread
                }

                val userNames = users.map {
                    "${it.userName} (${it.balance}💰)"
                }.toTypedArray()

                val currentUserId = firebirdApiManager.getCurrentUserId() // 🔽 ZMIANA
                var selectedIndex = -1

                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("👥 Wybierz Usera")
                    .setSingleChoiceItems(userNames, -1) { dialog, which ->
                        selectedIndex = which
                    }
                    .setPositiveButton("Wybierz") { dialog, _ ->
                        if (selectedIndex != -1) {
                            val selectedUser = users[selectedIndex]
                            if (selectedUser.userId != currentUserId) {
                                switchUser(selectedUser.userId)
                            } else {
                                Toast.makeText(this@MainActivity, "To już jest aktualny user!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("Anuluj", null)
                    .setNeutralButton("Nowy User") { dialog, _ ->
                        showCreateUserDialog()
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    // Metoda: Tworzenie nowego usera
    private fun showCreateUserDialog() {
        val input = EditText(this)
        input.hint = "Wprowadź nazwę usera"

        android.app.AlertDialog.Builder(this)
            .setTitle("🆕 Nowy User")
            .setMessage("Utwórz nowego usera:\n\n• Różne usery = różne postępy\n• Możesz przełączać się między userami\n• Wszystko synchronizuje się online")
            .setView(input)
            .setPositiveButton("Utwórz") { dialog, _ ->
                val userName = input.text.toString().trim()
                if (userName.isNotEmpty()) {
                    createNewUser(userName)
                } else {
                    Toast.makeText(this, "Wprowadź nazwę usera!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    // Metoda: Przełącz na innego usera
    private fun switchUser(newUserId: String) {
        scope.launch {
            try {
                Log.d("MainActivity", "🔄 Rozpoczynam przełączanie usera na: $newUserId")

                // 1. Przełącz usera
                firebirdApiManager.setUserId(newUserId)

                // 2. NAJPIERW spróbuj załadować z serwera
                val serverGameState = firebirdApiManager.loadGameStateFromServer()

                runOnUiThread {
                    if (serverGameState != null) {
                        // 3. Użyj danych Z SERWERA
                        applyServerGameState(serverGameState)
                        Log.d("MainActivity", "✅ Załadowano stan z SERWERA: balance=${serverGameState.balance}")
                        Toast.makeText(this@MainActivity, "✅ Przełączono usera! Saldo: ${serverGameState.balance}", Toast.LENGTH_LONG).show()
                    } else {
                        // 4. SERWER NIE ODPOWIEDZIAŁ - dopiero TERAZ wyczyść i załaduj lokalne
                        clearLocalGameState()
                        loadLocalGameState()
                        Log.d("MainActivity", "⚠️ Brak danych online, użyto lokalnych: balance=$balance")
                        Toast.makeText(this@MainActivity, "⚠️ Przełączono usera (brak danych online)", Toast.LENGTH_LONG).show()
                    }

                    updateUI()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "💥 BŁĄD podczas przełączania usera: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "❌ Błąd podczas przełączania usera", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun applyServerGameState(gameState: GameState) {
        balance = gameState.balance
        spinsCount = gameState.spinsCount
        biggestWin = gameState.biggestWin
        selectedLines = gameState.selectedLines
        lastShakeTime = gameState.lastShakeTime

        // Ustaw odwiedzone lokacje
        gameState.visitedLocations.forEachIndexed { index, visited ->
            if (index < targetLocations.size) {
                targetLocations[index].visited = visited
            }
        }

        // Ustaw checkboxy linii
        getLineCheckboxesList().forEachIndexed { index, checkbox ->
            checkbox.isChecked = selectedLines > index
        }

        Log.d("MainActivity", "🎮 Zastosowano stan gry: balance=$balance, lines=$selectedLines")
    }

    private fun loadLocalGameState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        balance = prefs.getInt("balance", 5000)
        spinsCount = prefs.getInt("spinsCount", 0)
        biggestWin = prefs.getInt("biggestWin", 0)
        selectedLines = prefs.getInt("selectedLines", 1)
        lastShakeTime = prefs.getLong("lastShakeTime", 0)

        // Odwiedzone lokacje
        targetLocations[0].visited = prefs.getBoolean("location1_visited", false)
        targetLocations[1].visited = prefs.getBoolean("location2_visited", false)
        targetLocations[2].visited = prefs.getBoolean("location3_visited", false)

        // Checkboxy linii
        getLineCheckboxesList().forEachIndexed { index, checkbox ->
            checkbox.isChecked = prefs.getBoolean("line${index+1}_checked", index == 0)
        }

        Log.d("MainActivity", "📱 Załadowano stan LOKALNY: balance=$balance")
    }

    private fun clearLocalGameState() {
        // Tylko resetuj zmienne, NIE zapisuj do SharedPreferences!
        balance = 5000
        spinsCount = 0
        biggestWin = 0
        selectedLines = 1
        lastShakeTime = 0

        // Resetuj lokacje
        targetLocations.forEach { it.visited = false }

        // Resetuj checkboxy (tylko w UI)
        getLineCheckboxesList().forEachIndexed { index, checkbox ->
            checkbox.isChecked = index == 0
        }

        Log.d("MainActivity", "🧹 Wyczyszczono TYMCZASOWO lokalny stan gry")
    }

    // Metoda: Utwórz nowego usera
    private fun createNewUser(userName: String) {
        scope.launch {
            //  ZAPISZ aktualny stan przed przełączeniem
            saveGameState()

            val newUserId = firebirdApiManager.createUser(userName)

            // WYCZYŚĆ lokalny stan
            clearLocalGameState()

            //  Załaduj nowy stan (zresetowany dla nowego usera)
            loadGameState()

            runOnUiThread {
                Toast.makeText(this@MainActivity, "✅ Utworzono nowego usera: $userName", Toast.LENGTH_LONG).show()
                updateUI()
            }
        }
    }

    // Pomocnicza funkcja do wyodrębniania nazwy z ID
    private fun extractUserNameFromId(userId: String): String {
        return if (userId.startsWith("user_") && userId.contains("_")) {
            val parts = userId.split("_")
            if (parts.size >= 2) {
                parts[1].replace("_", " ").split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
            } else {
                userId
            }
        } else {
            userId
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
            val turnDarkThreshold = 15f
            val turnLightThreshold = 25f

            val currentMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES

            when {
                lightValue < turnDarkThreshold && !currentMode -> {
                    // Zmiana na ciemny
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_DARK_MODE, true).apply()
                    binding.tvLightInfo.text = "🌙 Tryb: CIEMNY (${"%.1f".format(lightValue)} lux)"
                }
                lightValue > turnLightThreshold && currentMode -> {
                    // Zmiana na jasny
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putBoolean(KEY_DARK_MODE, false).apply()
                    binding.tvLightInfo.text = "☀️ Tryb: JASNY (${"%.1f".format(lightValue)} lux)"
                }
                else -> {
                    binding.tvLightInfo.text =
                        if (currentMode) "🌙 Tryb: CIEMNY (${"%.1f".format(lightValue)} lux)"
                        else "☀️ Tryb: JASNY (${"%.1f".format(lightValue)} lux)"
                }
            }
        }
    }

    private fun spinSlots() {
        val totalBet = baseBet * selectedLines

        if (balance < totalBet) {
            Toast.makeText(this, "Za mało punktów!", Toast.LENGTH_SHORT).show()
            return
        }

        //  Tylko 1 spin na raz!
        val newSpinCount = 1
        spinsCount += newSpinCount  // lokalne zliczanie
        balance -= totalBet
        saveGameState()

        //  Przekaż tylko 1 spin do zapisu w bazie
        updateDailyResultInDatabase(newSpinCount)

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

        // Faza 1: Szybkie kręcenie z efektem rozmycia
        applySpinEffects(slots)

        var fastSpinCount = 0
        val maxFastSpins = 20

        val fastSpinRunnable = object : Runnable {
            override fun run() {
                slots.forEach { slot ->
                    val randomSymbol = symbols.random()
                    slot.setImageResource(randomSymbol)
                    slot.rotationY = (slot.rotationY + 45f) % 360f

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
                    if (slowSpinCount >= index + 5) {
                        slot.setImageResource(finalResults[index])
                        slot.tag = finalResults[index]
                        slot.alpha = 1.0f
                        slot.scaleX = 1.0f
                        slot.scaleY = 1.0f
                        slot.rotationY = 0f
                    } else {
                        val randomSymbol = symbols.random()
                        slot.setImageResource(randomSymbol)
                        slot.rotationY = slot.rotationY + 20f
                    }
                }

                slowSpinCount++

                if (slowSpinCount < maxSlowSpins) {
                    handler.postDelayed(this, 80L)
                } else {
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

                    highlightWinningLine(line)
                }
            }
        }

        if (totalWin > 0) {
            balance += totalWin
            saveGameState()

            if (totalWin > biggestWin) {
                biggestWin = totalWin
            }

            // ZAPISZ DO FIREBIRD API
            updateDailyResultInDatabase()

            Toast.makeText(
                this,
                "Wygrana: $totalWin punktów!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun highlightWinningLine(lineIndices: List<Int>) {
        val slots = getSlotsList()

        lineIndices.forEach { index ->
            val slot = slots[index]

            slot.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(500L)
                .withEndAction {
                    slot.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200L)
                        .start()
                }
                .start()

            slot.animate()
                .alpha(0.5f)
                .setDuration(250L)
                .withEndAction {
                    slot.animate()
                        .alpha(1.0f)
                        .setDuration(250L)
                        .withEndAction {
                            slot.animate()
                                .alpha(0.5f)
                                .setDuration(150L)
                                .withEndAction {
                                    slot.animate()
                                        .alpha(1.0f)
                                        .setDuration(150L)
                                        .start()
                                }
                                .start()
                        }
                        .start()
                }
                .start()

            Handler(Looper.getMainLooper()).postDelayed({
                slot.setBackgroundResource(R.drawable.slot_border_dark)
            }, 1000)
        }
    }

    private fun resetGame() {
        balance = 5000
        targetLocations.forEach { it.visited = false }

        // ✅ Zresetuj liczniki
        spinsCount = 0
        biggestWin = 0

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

        scope.launch {
            val userId = firebirdApiManager.getCurrentUserId()
            val userName = extractUserNameFromId(userId)

        }
    }

    // NOWA METODA: Aktualizuj wynik w Firebird API
    private fun updateDailyResultInDatabase(newSpins: Int = 1) {
        if (newSpins <= 0) return

        scope.launch {
            val success = firebirdApiManager.saveDailyResult(
                finalBalance = balance,
                newSpinsCount = newSpins,  //  Tylko nowe spiny
                biggestWin = biggestWin
            )

            if (!success) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Błąd zapisu w chmurze", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            putInt("spinsCount", spinsCount)
            putInt("biggestWin", biggestWin)
            getLineCheckboxesList().forEachIndexed { index, checkbox ->
                putBoolean("line${index+1}_checked", checkbox.isChecked)
            }
            apply()
        }

        // 🔽 SYNCHRONIZUJ Z SERWEREM
        scope.launch {
            val visitedLocations = targetLocations.map { it.visited }
            firebirdApiManager.saveGameStateToServer(
                balance = balance,
                spinsCount = spinsCount,
                biggestWin = biggestWin,
                visitedLocations = visitedLocations,
                selectedLines = selectedLines,
                lastShakeTime = lastShakeTime
            )
        }
    }

    private fun loadGameState(forceFromServer: Boolean = true) {
        scope.launch {
            val serverGameState = firebirdApiManager.loadGameStateFromServer()

            runOnUiThread {
                if (serverGameState != null) {
                    // Użyj danych z serwera
                    balance = serverGameState.balance
                    spinsCount = serverGameState.spinsCount
                    biggestWin = serverGameState.biggestWin
                    selectedLines = serverGameState.selectedLines
                    lastShakeTime = serverGameState.lastShakeTime

                    serverGameState.visitedLocations.forEachIndexed { index, visited ->
                        if (index < targetLocations.size) {
                            targetLocations[index].visited = visited
                        }
                    }

                    Log.d("MainActivity", "✅ Załadowano stan z SERWERA: balance=$balance")
                } else {

                    loadLocalGameState()
                }

                updateUI()
            }
        }
    }
    private fun resetSpinsCounter() {
        spinsCount = 0
        saveGameState()

        scope.launch {
            // Zresetuj również na serwerze
            firebirdApiManager.saveDailyResult(balance, 0, biggestWin)
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

        //  ZABEZPIECZENIE: Tylko zapisuj jeśli spinsCount się nie zmniejszyło
        if (spinsCount >= getLastSavedSpinsCount()) {
            saveGameState()
            saveDailyResultIfNeeded()
        }
    }

    private fun getLastSavedSpinsCount(): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("spinsCount", 0)
    }

    // METODY DLA FIREBIRD API

    private fun saveDailyResultIfNeeded() {
        scope.launch {
            val isSaved = firebirdApiManager.isTodaySaved()
            if (!isSaved) {
                val success = firebirdApiManager.saveDailyResult(
                    finalBalance = balance,
                    newSpinsCount = spinsCount,
                    biggestWin = biggestWin
                )

                if (success) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Wynik dnia zapisany w chmurze!", Toast.LENGTH_SHORT).show()
                    }

                }
            }
        }
    }

    private fun checkNewDay() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSaveDate = prefs.getString("lastSaveDate", "")
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        if (lastSaveDate != currentDate) {
            // Nowy dzień - resetuj liczniki
            spinsCount = 0
            biggestWin = 0
            prefs.edit().putString("lastSaveDate", currentDate).apply()
            Log.d("MainActivity", "🆕 NOWY DZIEŃ - resetuję liczniki")
        }
    }

    private fun checkAndSavePreviousDay() {
        saveDailyResultIfNeeded()
    }

    private fun showHistory() {
        scope.launch {
            val history = firebirdApiManager.getRecentHistory(7)

            runOnUiThread {
                if (history.isEmpty()) {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("📊 Historia Gier")
                        .setMessage("Brak zapisanych wyników z ostatnich 7 dni.\n\nGraj dalej, a wyniki pojawią się tutaj! 🎰")
                        .setPositiveButton("OK", null)
                        .show()
                    return@runOnUiThread
                }

                val historyText = history.joinToString("\n\n") { record ->
                    "📅 ${formatDisplayDate(record.gameDate)}\n" +
                            "💰 Saldo: ${record.finalBalance} punktów\n" +
                            "🎰 Spiny: ${record.spinsCount}\n" +
                            "🏆 Największa wygrana: ${record.biggestWin}\n" +
                            "⏰ Godzina: ${formatTime(record.createdAt)}"
                }

                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("📊 Historia 7 dni")
                    .setMessage(historyText)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Wyczyść historię") { dialog, _ ->
                        showClearHistoryConfirmation()
                        dialog.dismiss()
                    }
                    .show()
            }
        }

        handler.post(slowSpinRunnable)
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
        scope.launch {
            val success = firebirdApiManager.clearAllHistory()

            runOnUiThread {
                if (success) {
                    Toast.makeText(this@MainActivity, "Historia wyczyszczona!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Błąd podczas czyszczenia historii", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }
}
override fun onDestroy() {
    super.onDestroy()
    if (::spinSound.isInitialized) spinSound.release()
    if (::winSound.isInitialized) winSound.release()
}
data class TargetLocation(
    val latitude: Double,
    val longitude: Double,
    val radius: Double,
    var visited: Boolean,
    val name: String = "Lokalizacja"
)
