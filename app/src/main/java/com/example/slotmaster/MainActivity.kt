/**
 * @file MainActivity.kt
 * @brief Główna aktywność aplikacji SlotMaster
 * @details Zarządza głównym ekranem gry, slotami, interfejsem użytkownika,
 *          sensorami, lokalizacją, systemem użytkowników i integracją z API.
 * @author Twórca aplikacji
 * @date 2024
 * @version 1.0
 * 
 * @section features Funkcje
 * - System automatycznego zapisu/odczytu stanu gry
 * - Integracja z Firebird API
 * - System linii wygrywających
 * - Mystery Box z timerem
 * - Zarządzanie wieloma użytkownikami
 * - Wykrywanie potrząśnięcia do kręcenia
 * - Automatyczna zmiana motywu w zależności od światła
 * - System lokalizacji z nagrodami
 */
package com.example.slotmaster

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Dialog
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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.widget.TextView
import android.view.View



/**
 * @class MainActivity
 * @extends AppCompatActivity
 * @implements SensorEventListener
 * @brief Główna aktywność aplikacji, zarządza całą logiką gry
 * 
 * @property binding Powiązanie widoku (ViewBinding)
 * @property sensorManager Manager sensorów Android
 * @property accelerometer Akcelerometr do wykrywania potrząśnięć
 * @property lightSensor Czujnik światła do automatycznego motywu
 * @property fusedLocationClient Klient lokalizacji Google
 * @property locationCallback Callback dla aktualizacji lokalizacji
 * @property spinSound Dźwięk kręcenia slotów
 * @property winSound Dźwięk wygranej
 * @property balance Aktualne saldo gracza
 * @property lastShakeTime Ostatni czas potrząśnięcia
 * @property SHAKE_THRESHOLD Próg wykrywania potrząśnięcia
 * @property SHAKE_TIMEOUT Minimalny czas między potrząśnięciami
 * @property KEY_DARK_MODE Klucz do zapisu trybu ciemnego
 * @property firebirdApiManager Manager połączenia z Firebird API
 * @property spinsCount Liczba wykonanych spinów
 * @property biggestWin Największa wygrana w historii
 * @property scope Coroutine scope dla operacji asynchronicznych
 * @property slot1-slot9 Referencje do ImageView slotów
 * @property MYSTERY_BOX_INTERVAL Interwał między dostępnością Mystery Box
 * @property mysteryBoxHandler Handler dla timera Mystery Box
 * @property mysteryBoxRunnable Runnable dla timera Mystery Box
 * @property mysteryBoxAvailable Czy Mystery Box jest dostępny
 * @property baseBet Bazowy zakład na linię
 * @property selectedLines Liczba wybranych linii
 * @property symbols Lista dostępnych symboli
 * @property symbolValues Wartości symboli
 * @property winningLines Definicje linii wygrywających
 * @property targetLocations Lista lokalizacji do odwiedzenia
 * @property PREFS_NAME Nazwa pliku preferencji
 */
class MainActivity : AppCompatActivity(), SensorEventListener {

    /** @brief Powiązanie widoku aktywności */
    private lateinit var binding: ActivityMainBinding
    
    /** @brief Manager sensorów Android */
    private lateinit var sensorManager: SensorManager
    
    /** @brief Sensor akcelerometru do wykrywania potrząśnięć */
    private var accelerometer: Sensor? = null
    
    /** @brief Sensor światła do automatycznej zmiany motywu */
    private var lightSensor: Sensor? = null
    
    /** @brief Klient lokalizacji Google */
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    /** @brief Callback dla aktualizacji lokalizacji */
    private lateinit var locationCallback: LocationCallback

    /** @brief Dźwięk odtwarzany podczas kręcenia slotów */
    private lateinit var spinSound: MediaPlayer

    /** @brief Dźwięk odtwarzany przy wygranej */
    private lateinit var winSound: MediaPlayer


    /** @brief Aktualne saldo punktów gracza */
    private var balance: Int = 5000
    
    /** @brief Ostatni czas zarejestrowanego potrząśnięcia */
    private var lastShakeTime: Long = 0
    
    /** @brief Próg przyspieszenia dla wykrycia potrząśnięcia */
    private val SHAKE_THRESHOLD = 20f
    
    /** @brief Minimalny czas między potrząśnięciami (ms) */
    private val SHAKE_TIMEOUT = 1000
    
    /** @brief Klucz do zapisu stanu trybu ciemnego w SharedPreferences */
    private val KEY_DARK_MODE = "dark_mode"

    // Zmienne do bazy danych - TYLKO Firebird API
    
    /** @brief Manager do komunikacji z Firebird API */
    private lateinit var firebirdApiManager: FirebirdApiManager
    
    /** @brief Całkowita liczba wykonanych spinów */
    private var spinsCount = 0
    
    /** @brief Największa wygrana w historii gry */
    private var biggestWin = 0

    // Coroutine scope dla operacji sieciowych
    
    /** @brief Scope dla coroutines na głównym wątku */
    private val scope = CoroutineScope(Dispatchers.Main)

    // Sloty jako ImageView
    
    /** @brief ImageView dla slotu 1 (górny lewy) */
    private lateinit var slot1: ImageView
    
    /** @brief ImageView dla slotu 2 (górny środkowy) */
    private lateinit var slot2: ImageView
    
    /** @brief ImageView dla slotu 3 (górny prawy) */
    private lateinit var slot3: ImageView
    
    /** @brief ImageView dla slotu 4 (środkowy lewy) */
    private lateinit var slot4: ImageView
    
    /** @brief ImageView dla slotu 5 (środkowy środkowy) */
    private lateinit var slot5: ImageView
    
    /** @brief ImageView dla slotu 6 (środkowy prawy) */
    private lateinit var slot6: ImageView
    
    /** @brief ImageView dla slotu 7 (dolny lewy) */
    private lateinit var slot7: ImageView
    
    /** @brief ImageView dla slotu 8 (dolny środkowy) */
    private lateinit var slot8: ImageView
    
    /** @brief ImageView dla slotu 9 (dolny prawy) */
    private lateinit var slot9: ImageView


    /** @brief Interwał czasowy między dostępnością Mystery Box (5 minut) */
    private val MYSTERY_BOX_INTERVAL = 5 * 60 * 1000L // 5 minut w milisekundach
    
    /** @brief Handler do zarządzania timerem Mystery Box */
    private lateinit var mysteryBoxHandler: Handler
    
    /** @brief Runnable dla cyklicznego odświeżania timera Mystery Box */
    private var mysteryBoxRunnable: Runnable? = null
    
    /** @brief Flaga dostępności Mystery Box */
    private var mysteryBoxAvailable = false

    // Zmienne dla systemu linii
    
    /** @brief Bazowa stawka zakładu na jedną linię */
    private var baseBet = 10
    
    /** @brief Liczba wybranych linii do obstawienia */
    private var selectedLines = 1
    
    /** @brief Lista identyfikatorów zasobów symboli */
    private val symbols = listOf(
        R.drawable.cherry,
        R.drawable.lemon,
        R.drawable.orange,
        R.drawable.star,
        R.drawable.seven,
        R.drawable.bell
    )
    
    /** @brief Mapa wartości punktowych symboli */
    private val symbolValues = mapOf(
        R.drawable.cherry to 10,
        R.drawable.lemon to 15,
        R.drawable.orange to 20,
        R.drawable.star to 50,
        R.drawable.seven to 200,
        R.drawable.bell to 30
    )

    // Linie wygrywające (indeksy slotów)
    
    /** @brief Lista definicji linii wygrywających jako indeksy slotów */
    private val winningLines = listOf(
        listOf(0, 1, 2),  // Linia 1 - górny wiersz
        listOf(3, 4, 5),  // Linia 2 - środkowy wiersz
        listOf(6, 7, 8),  // Linia 3 - dolny wiersz
        listOf(0, 4, 8),  // Linia 4 - przekątna \
        listOf(2, 4, 6)   // Linia 5 - przekątna /
    )

    /** @brief Lista lokalizacji docelowych do odwiedzenia */
    private val targetLocations = listOf(
        TargetLocation(49.6092, 20.7045, 100.0, false), // ANS
        TargetLocation(49.6251, 20.6912, 150.0, false), // Rynek
        TargetLocation(49.6092, 20.7134, 100.0, false) // Lidl lukasinskiego
    )

    /** @brief Nazwa pliku SharedPreferences */
    private val PREFS_NAME = "SlotMasterPrefs"

    /**
     * @brief Metoda cyklu życia onCreate - inicjalizacja aktywności
     * @param savedInstanceState Zapisany stan instancji
     * @details Inicjalizuje UI, sensory, lokalizację, API i ładuje stan gry
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        // 1. Theme
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val darkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        // 2. SPRAWDŹ CZY ZALOGOWANY
        val isGuest = prefs.getBoolean("guest", false)
        val username = prefs.getString("username", null)

        if (!isGuest && username == null) {
            startActivity(Intent(this, SimpleLoginActivity::class.java))
            finish()
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)




        // 2. Binding INICJALIZACJA - TO MUSI BYĆ NAJPIERW!


        // dźwięki
        spinSound = MediaPlayer.create(this, R.raw.spin)
        winSound = MediaPlayer.create(this, R.raw.wygrana)

// Głośność
        spinSound.setVolume(1f, 1f)
        winSound.setVolume(1f, 1f)


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

        // 4. Inicjalizacja managerów - TYLKO Firebird API
        firebirdApiManager = FirebirdApiManager(this)

        // 3. USTAW USER_ID W FirebirdApiManager PRZED INICJALIZACJĄ
        val user_id = prefs.getString("user_id", null)
        if (user_id != null) {
            firebirdApiManager.setUserId(user_id) // 🔽 KLUCZOWE!
        }

        // 5. Ładowanie danych - NAJPIERW LOKALNIE, POTEM SERWER
        loadFromSharedPreferences()  // 🔽 NAJPIERW ZAWSZE Z SHAREDPREFERENCES

        scope.launch {
            try {
                firebirdApiManager.getSharedUserId()
                // 🔽 POTEM SPRÓBUJ Z SERWERA
                val serverGameState = firebirdApiManager.loadGameStateFromServer()
                if (serverGameState != null) {
                    // SERWER MA DANE - ZASTOSUJ JE
                    applyGameStateFromServer(serverGameState)
                    Log.d("MainActivity", "✅ Załadowano stan z SERWERA")
                } else {
                    Log.d("MainActivity", "⚠️ Serwer nie ma danych, używam lokalnych")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Błąd ładowania z serwera: ${e.message}")
                // ZOSTAW LOKALNE DANE
            }

            checkAndSavePreviousDay()
        }

        // 6. Inicjalizacja systemów
        initializeSensors()
        initializeLocation()
        setupClickListeners()
        setupLineCheckboxes()
        setupBottomNavigation()
        initializeMysteryBox()

        // 7. Update UI
        updateUI()
        Log.d("MainActivity", "🎮 Stan po onCreate: balance=$balance, spiny=$spinsCount")
    }

    /**
     * @brief Inicjalizuje sensory (akcelerometr i czujnik światła)
     * @details Rejestruje listenery dla dostępnych sensorów
     * @post sensory są aktywne i nasłuchują zmian
     */
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

    /**
     * @brief Testuje połączenie z API Firebird
     * @details Wykonuje testy połączenia i zapisu danych
     * @post Wyświetla Toast z wynikiem testu
     */
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


    // Inicjalizacja Mystery Box
    
    /**
     * @brief Inicjalizuje system Mystery Box
     * @details Ustawia timer, handler i listenery dla Mystery Box
     * @post Mystery Box jest gotowy do użycia
     */
    private fun initializeMysteryBox() {
        mysteryBoxHandler = Handler(Looper.getMainLooper())

        // Sprawdź czy box jest dostępny przy starcie
        checkMysteryBoxAvailability()

        // Uruchom timer
        startMysteryBoxTimer()

        // Ustaw listener dla przycisku
        binding.btnMysteryBox.setOnClickListener {
            if (mysteryBoxAvailable) {
                openMysteryBox()
            } else {
                showTimeUntilNextBox()
            }
        }

        // Kliknięcie w timer też otwiera box
        binding.tvMysteryBoxTimer.setOnClickListener {
            if (mysteryBoxAvailable) {
                openMysteryBox()
            }
        }
    }

    /**
     * @brief Sprawdza dostępność Mystery Box
     * @details Porównuje czas od ostatniego otwarcia z interwałem
     * @post Ustawia flagę mysteryBoxAvailable i aktualizuje UI
     */
    private fun checkMysteryBoxAvailability() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastOpenTime = prefs.getLong("last_mystery_box_time", 0)
        val currentTime = System.currentTimeMillis()

        mysteryBoxAvailable = (currentTime - lastOpenTime) >= MYSTERY_BOX_INTERVAL

        updateMysteryBoxUI()
    }

    /**
     * @brief Uruchamia timer odliczający do następnego Mystery Box
     * @post Timer jest aktywny i odświeża UI co sekundę
     */
    private fun startMysteryBoxTimer() {
        mysteryBoxRunnable = object : Runnable {
            override fun run() {
                updateMysteryBoxTimer()
                mysteryBoxHandler.postDelayed(this, 1000) // Odświeżaj co sekundę
            }
        }
        mysteryBoxHandler.post(mysteryBoxRunnable!!)
    }

    /**
     * @brief Aktualizuje wyświetlacz timera Mystery Box
     * @details Oblicza pozostały czas i formatuje go do wyświetlenia
     * @post UI timera jest zaktualizowany
     */
    private fun updateMysteryBoxTimer() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastOpenTime = prefs.getLong("last_mystery_box_time", 0)
        val currentTime = System.currentTimeMillis()
        val timePassed = currentTime - lastOpenTime
        val timeLeft = MYSTERY_BOX_INTERVAL - timePassed

        runOnUiThread {
            if (timeLeft <= 0) {
                mysteryBoxAvailable = true
                binding.tvMysteryBoxTimer.text = "🎁 DOSTĘPNE!"
                binding.tvMysteryBoxTimer.setTextColor(ContextCompat.getColor(this, R.color.neon_green))
                binding.btnMysteryBox.visibility = View.VISIBLE
            } else {
                mysteryBoxAvailable = false
                val minutes = (timeLeft / 60000).toInt()
                val seconds = ((timeLeft % 60000) / 1000).toInt()
                binding.tvMysteryBoxTimer.text = String.format("🎁 Za: %02d:%02d", minutes, seconds)
                binding.tvMysteryBoxTimer.setTextColor(ContextCompat.getColor(this, R.color.neon_blue))
                binding.btnMysteryBox.visibility = View.GONE
            }
        }
    }

    /**
     * @brief Aktualizuje interfejs użytkownika Mystery Box
     * @post Przycisk i timer są odpowiednio pokazywane/ukrywane
     */
    private fun updateMysteryBoxUI() {
        runOnUiThread {
            if (mysteryBoxAvailable) {
                binding.tvMysteryBoxTimer.text = "🎁 DOSTĘPNE!"
                binding.tvMysteryBoxTimer.setTextColor(Color.GREEN)
                binding.btnMysteryBox.visibility = View.VISIBLE
            } else {
                binding.btnMysteryBox.visibility = View.GONE
            }
        }
    }

    /**
     * @brief GŁÓWNA METODA: Otwiera Mystery Box
     * @details Losuje nagrodę, odtwarza animację i dodaje punkty
     * @pre mysteryBoxAvailable == true
     * @post Nagroda dodana do salda, czas otwarcia zapisany, box niedostępny
     * @throws Toast jeśli box nie jest dostępny
     */
    private fun openMysteryBox() {
        if (!mysteryBoxAvailable) {
            Toast.makeText(this, "Mystery Box nie jest jeszcze dostępny!", Toast.LENGTH_SHORT).show()
            return
        }



        // Lista nagród
        val prizes = listOf(50, 100, 200, 300, 400, 500)
        val selectedPrize = prizes.random()

        // Animacja otwierania
        showMysteryBoxAnimation(selectedPrize)

        // Zapisz czas otwarcia
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("last_mystery_box_time", System.currentTimeMillis()).apply()

        // Zaktualizuj stan
        mysteryBoxAvailable = false
        balance += selectedPrize

        // Zapisz stan gry
        saveGameState()
        updateUI()
        updateMysteryBoxUI()

        // Pokaż Toast z wygraną
        Handler(Looper.getMainLooper()).postDelayed({
            Toast.makeText(this, "🎉 WYGRANA: $selectedPrize💰!", Toast.LENGTH_LONG).show()
        }, 1500)
    }

    /**
     * @brief Wyświetla animację otwierania Mystery Box
     * @param prize Wartość wylosowanej nagrody
     * @details Tworzy dialog z animacją pudełka i wyświetla nagrodę
     * @post Dialog z animacją jest pokazany na 3 sekundy
     */
    private fun showMysteryBoxAnimation(prize: Int) {
        // Stwórz custom dialog
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_mystery_box)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCancelable(false)

        val tvPrize = dialog.findViewById<TextView>(R.id.tvPrize)
        val ivBox = dialog.findViewById<ImageView>(R.id.ivBox)

        // Animacja pudełka
        ivBox.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(500)
            .withEndAction {
                ivBox.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .start()
            }
            .start()

        // Animacja tekstu
        tvPrize.text = "?"
        tvPrize.alpha = 0f

        Handler(Looper.getMainLooper()).postDelayed({
            // Pokaż nagrodę
            tvPrize.text = "$prize💰"
            tvPrize.setTextColor(Color.parseColor("#FFD700")) // Złoty kolor
            tvPrize.animate()
                .alpha(1f)
                .scaleX(1.5f)
                .scaleY(1.5f)
                .setDuration(1000)
                .start()



        }, 1000)

        // Zamknij dialog po 3 sekundach
        Handler(Looper.getMainLooper()).postDelayed({
            dialog.dismiss()
        }, 3000)

        dialog.show()
    }



    /**
     * @brief Pokazuje czas do następnego dostępnego Mystery Box
     * @details Oblicza pozostały czas i wyświetla go w Toast
     * @post Toast z informacją o czasie oczekiwania
     */
    private fun showTimeUntilNextBox() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastOpenTime = prefs.getLong("last_mystery_box_time", 0)
        val currentTime = System.currentTimeMillis()
        val timePassed = currentTime - lastOpenTime
        val timeLeft = MYSTERY_BOX_INTERVAL - timePassed

        if (timeLeft > 0) {
            val minutes = (timeLeft / 60000).toInt()
            val seconds = ((timeLeft % 60000) / 1000).toInt()
            Toast.makeText(this, "Następny box za $minutes min $seconds sek", Toast.LENGTH_LONG).show()
        }
    }



    /**
     * @brief Inicjalizuje system lokalizacji
     * @details Konfiguruje FusedLocationProvider i callback
     * @post System lokalizacji jest gotowy do użycia
     */
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

    /**
     * @brief Konfiguruje dolne menu nawigacyjne
     * @details Ustawia listenery dla poszczególnych ikon menu
     * @post Menu reaguje na kliknięcia
     */
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

    /**
     * @brief Wyświetla dialog zarządzania użytkownikami
     * @details Pokazuje opcje: wybór użytkownika, nowy użytkownik, aktualny użytkownik
     * @post Dialog z opcjami zarządzania użytkownikami
     */
    private fun showUserManagementDialog() {
        val options = arrayOf("👤 Aktualny User")

        AlertDialog.Builder(this)
            .setTitle("👨‍💼 Zarządzanie Userami")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showCurrentUserInfo()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    /**
     * @brief Wyświetla dialog z dodatkowymi opcjami
     * @details Pokazuje opcje: test API, historia, reset gry, informacje, ranking
     * @post Dialog z rozszerzonymi opcjami aplikacji
     */
    private fun showMoreOptionsDialog() {
        val options = arrayOf("🔧 Test API", "📊 Historia", "🔄 Reset Gry", "ℹ️ Informacje", "🏆 Ranking graczy", "Wyloguj się")

        AlertDialog.Builder(this)
            .setTitle("⚙️ Więcej Opcji")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> testApiConnection()
                    1 -> showHistory()
                    2 -> resetGame()
                    3 -> showGameInfoDialog()
                    4 -> showRanking()
                    5 -> showLogoutDialog()
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }
    
    /**
     * @brief Otwiera aktywność rankingu graczy
     * @post Przejście do RankingActivity
     */



    private fun showRanking() {

        val intent = Intent(this, RankingActivity::class.java)
        startActivity(intent)
    }
    fun logout(view: View) { // 🔽 DLA onClick Z XML
        showLogoutDialog()
    }

    private fun showLogoutDialog() { // 🔽 DLA WYWOŁANIA Z KODU
        AlertDialog.Builder(this)
            .setTitle("Wylogowanie")
            .setMessage("Czy na pewno chcesz się wylogować?")
            .setPositiveButton("Tak") { dialog, _ ->
                // Usuń dane logowania
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().remove("username").remove("guest").apply()

                // Wróć do ekranu logowania
                startActivity(Intent(this, SimpleLoginActivity::class.java))
                finish()
                dialog.dismiss()
            }
            .setNegativeButton("Nie", null)
            .show()
    }
    /**
     * @brief Uruchamia aktualizacje lokalizacji
     * @details Sprawdza uprawnienia i konfiguruje requesty lokalizacji
     * @post Lokalizacja jest regularnie aktualizowana
     * @throws Request uprawnień jeśli nie są nadane
     */
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

    /**
     * @brief Sprawdza czy gracz jest w pobliżu lokalizacji docelowych
     * @param currentLocation Aktualna lokalizacja gracza
     * @details Jeśli gracz jest w zasięgu nieodwiedzonej lokalizacji,
     *          dodaje 100 punktów do salda i oznacza jako odwiedzoną
     * @see TargetLocation
     * @see balance
     * @post Punkty dodane, lokalizacja oznaczona jako odwiedzona
     */
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

    /**
     * @brief Wyświetla dialog z informacjami o grze
     * @post Dialog z opisem gry jest pokazany
     */
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

    /**
     * @brief Konfiguruje listenery dla przycisków
     * @details Ustawia kliknięcia dla spin, info, mapa i checkboxów linii
     * @post Wszystkie przyciski reagują na kliknięcia
     */
    private fun setupClickListeners() {
        binding.btnSpin.setOnClickListener {
            spinSlots()
        }

        binding.btnInfo.setOnClickListener {
            showGameInfoDialog()
        }

        binding.btnShowMap.setOnClickListener {
            showMap()
        }

        // Listenery dla checkboxów linii
        getLineCheckboxesList().forEach { checkbox ->
            checkbox.setOnCheckedChangeListener { _, _ ->
                updateSelectedLines()
            }
        }
    }

    /**
     * @brief Konfiguruje checkboxy linii
     * @details Ustawia tekst i stan początkowy dla checkboxów
     * @post Checkboxy są gotowe do użycia
     */
    private fun setupLineCheckboxes() {
        getLineCheckboxesList().forEachIndexed { index, checkbox ->
            checkbox.text = "Linia ${index + 1}"
        }
        updateSelectedLines()
    }

    /**
     * @brief Zwraca listę checkboxów linii
     * @return Lista CheckBox dla linii 1-5
     */
    private fun getLineCheckboxesList() = listOf(
        binding.cbLine1, binding.cbLine2, binding.cbLine3,
        binding.cbLine4, binding.cbLine5
    )

    /**
     * @brief Zwraca listę slotów ImageView
     * @return Lista ImageView w kolejności od slot1 do slot9
     */
    private fun getSlotsList() = listOf(
        slot1, slot2, slot3,
        slot4, slot5, slot6,
        slot7, slot8, slot9
    )

    /**
     * @brief Aktualizuje liczbę wybranych linii
     * @details Zlicza zaznaczone checkboxy, minimum 1 linia
     * @post selectedLines jest zaktualizowane, UI odświeżone
     */
    private fun updateSelectedLines() {
        val lineCheckboxes = getLineCheckboxesList()
        selectedLines = lineCheckboxes.count { it.isChecked }
        if (selectedLines == 0) {
            binding.cbLine1.isChecked = true
            selectedLines = 1
        }
        updateBetInfo()
    }

    /**
     * @brief Aktualizuje informację o zakładzie
     * @post Tekst z stawką i liczbą linii jest zaktualizowany
     */
    private fun updateBetInfo() {
        val totalBet = baseBet * selectedLines
        binding.tvBetInfo.text = "Stawka: $totalBet punktów ($selectedLines linii)"
    }

    /**
     * @brief Wyświetla informacje o aktualnym użytkowniku
     * @details Pokazuje ID i nazwę aktualnie zalogowanego użytkownika
     * @post Dialog z informacjami użytkownika
     */
    private fun showCurrentUserInfo() {
        scope.launch {
            val userId = firebirdApiManager.getCurrentUserId()
            val userName = extractUserNameFromId(userId)

            runOnUiThread {
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("👤 Aktualny User")
                    .setMessage("User ID: $userId\nNazwa: $userName")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    /**
     * @brief Otwiera aktywność mapy
     * @details Przekazuje aktualną lokalizację i stan odwiedzonych miejsc
     * @post Przejście do MapActivity z danymi
     * @throws Toast jeśli brak uprawnień lokalizacji
     */
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

    /**
     * @brief Wyświetla dialog wyboru użytkownika z listy
     * @details Pobiera listę użytkowników z serwera i pokazuje w dialogu
     * @post Dialog z listą użytkowników do wyboru
     */
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

                val currentUserId = firebirdApiManager.getCurrentUserId()
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

    /**
     * @brief Wyświetla dialog tworzenia nowego użytkownika
     * @post Dialog z polem tekstowym do wprowadzenia nazwy użytkownika
     */
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

    /**
     * @brief Przełącza na innego użytkownika
     * @param newUserId ID użytkownika do przełączenia
     * @details Zapisuje aktualny stan, zmienia użytkownika i ładuje jego stan
     * @post Użytkownik zmieniony, stan załadowany z serwera
     */
    private fun switchUser(newUserId: String) {
        scope.launch {
            try {
                Log.d("MainActivity", "🔄 Przełączam usera na: $newUserId")

                // 1. ZAPISZ AKTUALNY STAN PRZED PRZEŁĄCZENIEM
                saveGameState()

                // 2. Przełącz usera
                firebirdApiManager.setUserId(newUserId)

                // 3. Załaduj stan z serwera
                val serverGameState = firebirdApiManager.loadGameStateFromServer()

                runOnUiThread {
                    if (serverGameState != null) {
                        // SERWER MA DANE - ZASTOSUJ JE
                        applyGameStateFromServer(serverGameState)
                        Toast.makeText(this@MainActivity, "✅ Przełączono usera! Saldo: ${serverGameState.balance}", Toast.LENGTH_LONG).show()
                    } else {
                        // SERWER NIE MA DANYCH - ZRESETUJ DO DEFAULTOWYCH
                        resetToDefaultState()
                        Toast.makeText(this@MainActivity, "🆕 Nowy user - domyślny stan", Toast.LENGTH_LONG).show()
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

    /**
     * @brief Załaduj stan gry z serwera i zastosuj lokalnie
     * @param gameState Stan gry pobrany z serwera
     * @details Aktualizuje wszystkie zmienne gry na podstawie danych z serwera
     * @post Lokalny stan gry jest identyczny z serwerowym
     */
    private fun applyGameStateFromServer(gameState: GameState) {
        // Aktualizuj dane z serwera
        balance = gameState.balance
        spinsCount = gameState.spinsCount
        biggestWin = gameState.biggestWin
        selectedLines = gameState.selectedLines
        lastShakeTime = gameState.lastShakeTime

        gameState.visitedLocations.forEachIndexed { index, visited ->
            if (index < targetLocations.size) {
                targetLocations[index].visited = visited
            }
        }

        // Ustaw checkboxy linii
        getLineCheckboxesList().forEachIndexed { index, checkbox ->
            checkbox.isChecked = index < selectedLines
        }

        // 🔽 ZAPISZ DO SHAREDPREFERENCES
        saveToSharedPreferences()

        Log.d("MainActivity", "🎮 Załadowano stan z serwera: balance=$balance, lines=$selectedLines")
    }

    /**
     * @brief Załaduj stan gry z SharedPreferences
     * @details Ładuje wszystkie zapisane wartości z lokalnego storage
     * @post Zmienne gry są zainicjalizowane wartościami z SharedPreferences
     */
    private fun loadFromSharedPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 🔽 NIE resetuj jeśli są zapisane dane!
        balance = prefs.getInt("balance", 5000)  // Domyślnie 5000 tylko przy pierwszym uruchomieniu
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

        Log.d("MainActivity", "📱 Załadowano z SharedPreferences: balance=$balance, spiny=$spinsCount")
    }

    /**
     * @brief Zapisz stan gry do SharedPreferences
     * @details Zapisuje wszystkie aktualne wartości zmiennych gry
     * @post Stan gry jest zapisany lokalnie
     */
    private fun saveToSharedPreferences() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("balance", balance)
            putInt("spinsCount", spinsCount)
            putInt("biggestWin", biggestWin)
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
        Log.d("MainActivity", "💾 Zapisano do SharedPreferences: balance=$balance")
    }

    /**
     * @brief Resetuje stan gry do wartości domyślnych
     * @details Ustawia początkowe wartości dla nowego użytkownika
     * @post Wszystkie liczniki zresetowane, saldo ustawione na 5000
     */
    private fun resetToDefaultState() {
        // 🔽 UŻYWAJ TYLKO DO RĘCZNEGO RESETU GRY LUB NOWEGO USERA!
        balance = 5000
        spinsCount = 0
        biggestWin = 0
        selectedLines = 1
        lastShakeTime = 0

        targetLocations.forEach { it.visited = false }
        getLineCheckboxesList().forEachIndexed { index, checkbox ->
            checkbox.isChecked = index == 0
        }

        // 🔽 ZAPISZ ZRESETOWANY STAN
        saveGameState()

        Log.d("MainActivity", "🔄 Ręczny reset gry do wartości domyślnych")
    }

    /**
     * @brief Tworzy nowego użytkownika
     * @param userName Nazwa nowego użytkownika
     * @details Zapisuje aktualny stan, tworzy użytkownika na serwerze, resetuje stan
     * @post Nowy użytkownik utworzony, stan zresetowany
     */
    private fun createNewUser(userName: String) {
        scope.launch {
            // ZAPISZ aktualny stan przed przełączeniem
            saveGameState()

            val newUserId = firebirdApiManager.createUser(userName)

            // Ustaw nowego usera
            firebirdApiManager.setUserId(newUserId)

            // Zresetuj stan dla nowego usera
            resetToDefaultState()

            // 🔽 ZAPISZ ZRESETOWANY STAN NA SERWERZE
            saveGameState()

            runOnUiThread {
                Toast.makeText(this@MainActivity, "✅ Utworzono nowego usera: $userName", Toast.LENGTH_LONG).show()
                updateUI()
            }
        }
    }

    /**
     * @brief Wyodrębnia nazwę użytkownika z ID
     * @param userId ID użytkownika w formacie "user_[nazwa]"
     * @return Sformatowana nazwa użytkownika
     */
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

    /**
     * @brief Callback zmiany wartości sensora
     * @param event Zdarzenie sensora z danymi
     * @details Rozdziela obsługę na akcelerometr i czujnik światła
     * @see handleAccelerometer
     * @see handleLightSensor
     */
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

    /**
     * @brief Obsługuje dane z akcelerometru
     * @param values Tablica wartości przyspieszenia [x, y, z]
     * @details Wykrywa potrząśnięcia i uruchamia spinSlots() przy przekroczeniu progu
     */
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

    /**
     * @brief Obsługuje dane z czujnika światła
     * @param lightValue Wartość natężenia światła w lux
     * @details Automatycznie zmienia motyw aplikacji w zależności od światła
     * @post Motyw zmieniony na jasny/ciemny, UI zaktualizowany
     */
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

    /**
     * @brief Główna metoda kręcenia slotami
     * @details Sprawdza saldo, odtwarza dźwięk, wykonuje animację i sprawdza wygraną
     * @pre balance >= totalBet
     * @post Sloty zakręcone, saldo pomniejszone, wygrana sprawdzona
     * @throws Toast jeśli za mało punktów
     */
    private fun spinSlots() {

        // 🔽 SPRAWDŹ NOWY DZIEŃ PRZED KAŻDYM SPINEM
        checkAndHandleNewDay()
        if (spinSound.isPlaying) {
            spinSound.seekTo(0)
        }
        spinSound.start()


        val totalBet = baseBet * selectedLines

        if (balance < totalBet) {
            Toast.makeText(this, "Za mało punktów!", Toast.LENGTH_SHORT).show()
            return
        }

        // TYLKO LOKALNE ZLICZANIE
        spinsCount += 1
        balance -= totalBet

        // 🔽 ZAWSZE ZAPISZ DO SHAREDPREFERENCES
        saveGameState()

        // PRZEKAŻ AKTUALNY STAN DO SERWERA
        updateDailyResultInDatabase()

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

    /**
     * @brief Stosuje efekty wizualne podczas kręcenia
     * @param slots Lista slotów do animacji
     * @post Sloty mają zastosowane efekty przezroczystości i skali
     */
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

    /**
     * @brief Rozpoczyna fazę powolnego kręcenia slotów
     * @param Handler do zarządzania animacjami
     * @details Stopniowo zatrzymuje sloty z efektami wizualnymi
     * @post Sloty pokazują finalne wyniki
     */
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

    /**
     * @brief Resetuje wszystkie sloty po zakręceniu
     * @post Sloty mają domyślny wygląd
     */
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

    /**
     * @brief Sprawdza wygrane linie po zakręceniu
     * @details Analizuje ułożenie symboli na aktywnych liniach
     * @post Wygrane dodane do salda, największa wygrana zaktualizowana
     */
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

            if (totalWin > biggestWin) {
                biggestWin = totalWin
            }

            // 🔽 ZAPISZ ZMIANY
            saveGameState()

            // ZAPISZ DO FIREBIRD API
            updateDailyResultInDatabase()
            if (winSound.isPlaying) {
                winSound.seekTo(0)
            }
            winSound.start()
            Toast.makeText(
                this,
                "Wygrana: $totalWin punktów!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * @brief Podświetla wygrywającą linię
     * @param lineIndices Lista indeksów slotów w linii
     * @post Sloty w linii migają i są podświetlone
     */
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

    /**
     * @brief Resetuje całą grę do stanu początkowego
     * @details Zapisuje aktualny stan, resetuje liczniki, zachowuje saldo
     * @post Gra zresetowana, Toast potwierdzający
     */
    private fun resetGame() {
        scope.launch {
            // ZAPISZ STAN PRZED RESETEM
            saveGameState()

            // Zresetuj lokalne zmienne
            resetToDefaultState()

            // ZAPISZ ZRESETOWANY STAN NA SERWERZE
            saveGameState()

            runOnUiThread {
                updateUI()
                Toast.makeText(this@MainActivity, "Gra zresetowana!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * @brief Aktualizuje interfejs użytkownika
     * @details Odświeża wyświetlacz salda, informacji o zakładzie i stanu lokalizacji
     * @post Wszystkie elementy UI są aktualne
     */
    private fun updateUI() {
        binding.tvBalance.text = "Saldo: $balance"
        updateBetInfo()

        getSlotsList().forEach { slot ->
            if (slot.drawable == null) slot.setImageResource(R.drawable.cherry)
        }

        val visitedCount = targetLocations.count { it.visited }
        binding.tvLocationInfo.text = "Odwiedzone lokacje: $visitedCount/${targetLocations.size}"
    }

    /**
     * @brief Aktualizuje wynik dnia w bazie danych Firebird
     * @details Wysyła aktualne statystyki gry na serwer
     * @post Dane zsynchronizowane z serwerem (jeśli połączenie)
     */
    private fun updateDailyResultInDatabase() {
        scope.launch {
            try {
                val success = firebirdApiManager.saveDailyResult(
                    finalBalance = balance,
                    newSpinsCount = spinsCount,  // CAŁKOWITA LICZBA SPINÓW
                    biggestWin = biggestWin
                )

                if (!success) {
                    Log.e("MainActivity", "❌ Błąd zapisu spinów na serwerze")
                } else {
                    Log.d("MainActivity", "✅ Zapisano spiny na serwerze: $spinsCount")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Błąd połączenia z serwerem: ${e.message}")
                // Nie pokazuj Toast - użytkownik może być offline
            }
        }
    }

    /**
     * @brief Zapisuje stan gry lokalnie i na serwerze
     * @details Synchronizuje wszystkie dane gry
     * @post Stan zapisany w SharedPreferences i na serwerze (jeśli połączenie)
     */
    private fun saveGameState() {
        // 🔽 ZAWSZE ZAPISUJ DO SHAREDPREFERENCES
        saveToSharedPreferences()

        // 🔽 PRÓBUJ ZSYNCHRONIZOWAĆ Z SERWEREM (ale nie blokuj)
        scope.launch {
            try {
                val visitedLocations = targetLocations.map { it.visited }
                firebirdApiManager.saveGameStateToServer(
                    balance = balance,
                    spinsCount = spinsCount,
                    biggestWin = biggestWin,
                    visitedLocations = visitedLocations,
                    selectedLines = selectedLines,
                    lastShakeTime = lastShakeTime
                )
                Log.d("MainActivity", "✅ Zsynchronizowano stan gry z serwerem")
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ Błąd synchronizacji: ${e.message}")
                // Nie pokazuj Toast - użytkownik może być offline
            }
        }
    }

    /**
     * @brief Callback zmiany dokładności sensora
     * @param sensor Sensor którego dokładność się zmieniła
     * @param accuracy Nowa dokładność sensora
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * @brief Obsługuje wynik żądania uprawnień
     * @param requestCode Kod żądania
     * @param permissions Tablica żądanych uprawnień
     * @param grantResults Tablica wyników przyznania uprawnień
     * @post Jeśli przyznano uprawnienia lokalizacji, uruchamia jej aktualizacje
     */
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

    /**
     * @brief Metoda cyklu życia onResume
     * @details Wznawia nasłuchiwanie sensorów, lokalizację i sprawdza nowy dzień
     * @post Aplikacja aktywna, wszystkie systemy działają
     */
    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        startLocationUpdates()

        // SPRAWDŹ NOWY DZIEŃ PRZY KAŻDYM WZNOWIENIU APLIKACJI
        checkAndHandleNewDay()

        Log.d("MainActivity", "🔄 onResume - stan: balance=$balance")
    }

    /**
     * @brief Metoda cyklu życia onPause
     * @details Zatrzymuje sensory, lokalizację i zapisuje stan gry
     * @post Stan gry zapisany, zasoby zwolnione
     */
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // 🔽 ZAWSZE ZAPISUJ PRZED WYŁĄCZENIEM
        saveGameState()

        // 🔽 ZAPISZ WYNIK DNIA (opcjonalnie)
        saveDailyResultIfNeeded()

        Log.d("MainActivity", "⏸️ onPause - zapisano: balance=$balance")
    }

    /**
     * @brief Zapisuje wynik dnia jeśli potrzeba
     * @param forceSave Wymusza zapis nawet jeśli już zapisano dzisiaj
     * @details Sprawdza czy dzisiejszy wynik jest już zapisany
     * @post Wynik dnia zapisany na serwerze (jeśli potrzeba i połączenie)
     */
    private fun saveDailyResultIfNeeded(forceSave: Boolean = false) {
        scope.launch {
            // SPRAWDŹ CZY TO NOWY DZIEŃ PRZED ZAPISEM
            checkAndHandleNewDay()

            val isSaved = firebirdApiManager.isTodaySaved()
            if (!isSaved || forceSave) {
                try {
                    val success = firebirdApiManager.saveDailyResult(
                        finalBalance = balance,
                        newSpinsCount = spinsCount,  // CAŁKOWITA LICZBA
                        biggestWin = biggestWin
                    )

                    if (success) {
                        Log.d("MainActivity", "💾 Wynik dnia zapisany: $spinsCount spinów")
                    } else {
                        Log.e("MainActivity", "❌ Błąd zapisu wyniku dnia")
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Błąd połączenia przy zapisie dnia: ${e.message}")
                }
            }
        }
    }

    /**
     * @brief Sprawdza i obsługuje zmianę dnia
     * @return true jeśli wykryto nowy dzień, false w przeciwnym razie
     * @details Resetuje liczniki przy zmianie dnia, zachowuje saldo
     * @post Jeśli nowy dzień, liczniki zresetowane, poprzedni dzień zapisany
     */
    private fun checkAndHandleNewDay(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSaveDate = prefs.getString("lastSaveDate", "")
        val currentDate = getCurrentDate()

        if (lastSaveDate != currentDate && lastSaveDate?.isNotEmpty() == true) {
            Log.d("MainActivity", "🆕 WYKRYTO NOWY DZIEŃ: $lastSaveDate -> $currentDate")

            // ZAPISZ WYNIK POPRZEDNIEGO DNIA NA SERWERZE
            scope.launch {
                try {
                    firebirdApiManager.saveDailyResult(
                        finalBalance = balance,
                        newSpinsCount = spinsCount,
                        biggestWin = biggestWin
                    )
                } catch (e: Exception) {
                    Log.e("MainActivity", "❌ Błąd zapisu poprzedniego dnia: ${e.message}")
                }
            }

            // Resetuj tylko liczniki, zachowaj saldo
            spinsCount = 0
            biggestWin = 0

            // Zapisz zresetowany stan
            saveGameState()

            prefs.edit().putString("lastSaveDate", currentDate).apply()

            runOnUiThread {
                Toast.makeText(this, "🆕 Nowy dzień! Liczniki zresetowane", Toast.LENGTH_SHORT).show()
            }

            return true
        } else if (lastSaveDate?.isEmpty() == true) {
            // Pierwsze uruchomienie - ustaw dzisiejszą datę
            prefs.edit().putString("lastSaveDate", currentDate).apply()
        }

        return false
    }

    /**
     * @brief Pobiera bieżącą datę w formacie YYYY-MM-DD
     * @return String z datą
     */
    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    /**
     * @brief Sprawdza i zapisuje wynik poprzedniego dnia
     * @details Upewnia się że dane z wczoraj są zapisane
     */
    private fun checkAndSavePreviousDay() {
        saveDailyResultIfNeeded()
    }

    /**
     * @brief Wyświetla historię gier z ostatnich 7 dni
     * @details Pobiera dane z serwera i formatuje do czytelnej postaci
     * @post Dialog z historią gier
     */
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
    }

    /**
     * @brief Formatuje datę z bazy danych do wyświetlenia
     * @param dbDate Data w formacie YYYY-MM-DD
     * @return Data w formacie DD.MM.YYYY
     */
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

    /**
     * @brief Formatuje czas z pełnego timestampu do formatu HH:mm
     * @param dateTime Pełny timestamp w formacie YYYY-MM-DD HH:mm:ss
     * @return Czas w formacie HH:mm
     */
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

    /**
     * @brief Wyświetla potwierdzenie czyszczenia historii
     * @post Dialog z potwierdzeniem usunięcia historii
     */
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

    /**
     * @brief Czyści całą historię gier z serwera
     * @post Historia usunięta, Toast z potwierdzeniem
     */
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
    
    /**
     * @brief Metoda cyklu życia onDestroy
     * @details Zatrzymuje timer Mystery Box i zwalnia zasoby dźwiękowe
     * @post Wszystkie zasoby zwolnione
     */
    override fun onDestroy() {
        super.onDestroy()
        mysteryBoxRunnable?.let {
            mysteryBoxHandler.removeCallbacks(it)
        }
        if (::spinSound.isInitialized) spinSound.release()
        if (::winSound.isInitialized) winSound.release()

    }

    /**
     * @brief Companion object z stałymi
     */
    companion object {
        /** @brief Kod żądania uprawnień lokalizacji */
        private const val LOCATION_PERMISSION_REQUEST = 1001
    }
}

/**
 * @class TargetLocation
 * @brief Model lokalizacji docelowej do odwiedzenia
 * 
 * @property latitude Szerokość geograficzna
 * @property longitude Długość geograficzna
 * @property radius Promień w metrach do uznania za odwiedzoną
 * @property visited Czy lokalizacja została odwiedzona
 * @property name Nazwa lokalizacji (domyślnie "Lokalizacja")
 */
data class TargetLocation(
    val latitude: Double,
    val longitude: Double,
    val radius: Double,
    var visited: Boolean,
    val name: String = "Lokalizacja"
)