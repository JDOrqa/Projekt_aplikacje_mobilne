package com.example.slotmaster
import android.content.Context
import android.util.Log
import com.example.slotmaster.models.GameHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
class FirebirdApiManager(private val context: Context) {

    private val client = OkHttpClient()
    private val baseUrl = "https://tangy-ducks-judge.loca.lt/api"

    companion object {
        private const val TAG = "FirebirdApiManager"
    }
    fun getCurrentUserId(): String {
        return getUserId()
    }

    // 🔽 PUBLICZNA METODA DO USTAWIANIA USER_ID
    fun setUserId(userId: String) {
        val prefs = context.getSharedPreferences("FirebirdPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("user_id", userId).apply()
        Log.d(TAG, "💾 Zapisano nowe userId: $userId")
    }
    private fun getUserId(): String {
        val prefs = context.getSharedPreferences("FirebirdPrefs", Context.MODE_PRIVATE)
        var userId = prefs.getString("user_id", null)

        if (userId == null) {
            userId = "user_${UUID.randomUUID()}"
            prefs.edit().putString("user_id", userId).apply()
            Log.d(TAG, "Nowy user ID: $userId")
        }

        return userId
    }
    // 🔽 DODAJĘ TEST CONNECTION
    suspend fun testConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🧪 TESTUJĘ POŁĄCZENIE: $baseUrl/status")

                val request = Request.Builder()
                    .url("$baseUrl/status")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d(TAG, "📡 KOD ODPOWIEDZI: ${response.code}")
                Log.d(TAG, "📨 ODPOWIEDŹ: $responseBody")

                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "💥 BŁĄD POŁĄCZENIA: ${e.message}")
                false
            }
        }
    }
    suspend fun getSharedUserId(): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔗 Pobieram wspólne userId z serwera")

                val request = Request.Builder()
                    .url("$baseUrl/shared-user-id")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d(TAG, "Kod odpowiedzi userId: ${response.code}")
                Log.d(TAG, "Odpowiedź userId: $responseBody")

                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody ?: "{}")
                    val sharedUserId = jsonResponse.optString("userId", "")
                    if (sharedUserId.isNotEmpty()) {
                        Log.d(TAG, "✅ Ustawiam wspólne userId: $sharedUserId")
                        setUserId(sharedUserId)
                        return@withContext sharedUserId
                    }
                }

                // Fallback: użyj lokalnego userId
                getUserId()
            } catch (e: Exception) {
                Log.e(TAG, "Błąd pobierania userId: ${e.message}")
                getUserId()
            }
        }
    }
    suspend fun getUsers(): List<User> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "👥 Pobieram listę userów")

                val request = Request.Builder()
                    .url("$baseUrl/users")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d(TAG, "Kod odpowiedzi userów: ${response.code}")

                if (!response.isSuccessful) return@withContext emptyList()

                val jsonArray = JSONArray(responseBody ?: "[]")
                val users = mutableListOf<User>()

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    users.add(User(
                        userId = jsonObject.optString("user_id", ""),
                        userName = extractUserName(jsonObject.optString("user_id", "")),
                        lastActivity = jsonObject.optString("last_activity", ""),
                        balance = jsonObject.optInt("balance", 0)
                    ))
                }

                Log.d(TAG, "Pobrano ${users.size} userów")
                users
            } catch (e: Exception) {
                Log.e(TAG, "Błąd pobierania userów: ${e.message}")
                emptyList()
            }
        }
    }
// Utwórz nowego usera
    suspend fun createUser(userName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🆕 Tworzę nowego usera: $userName")

                val json = JSONObject().apply {
                    put("userName", userName)
                }

                val request = Request.Builder()
                    .url("$baseUrl/users")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d(TAG, "Kod odpowiedzi tworzenia usera: ${response.code}")
                Log.d(TAG, "Odpowiedź tworzenia usera: $responseBody")

                if (response.isSuccessful) {
                    val jsonResponse = JSONObject(responseBody ?: "{}")
                    val newUserId = jsonResponse.optString("userId", "")
                    if (newUserId.isNotEmpty()) {
                        setUserId(newUserId)
                        return@withContext newUserId
                    }
                }

                // Fallback
                getUserId()
            } catch (e: Exception) {
                Log.e(TAG, "Błąd tworzenia usera: ${e.message}")
                getUserId()
            }
        }
    }
     // Pomocnicza funkcja do wyodrębniania nazwy z userId
    private fun extractUserName(userId: String): String {
        return if (userId.startsWith("user_") && userId.contains("_")) {
            val parts = userId.split("_")
            if (parts.size >= 2) {
                parts[1].replace("_", " ").capitalizeWords()
            } else {
                userId
            }
        } else {
            userId
        }
    }

    // Rozszerzenie String do capitalizacji
    private fun String.capitalizeWords(): String =
        split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

    suspend fun saveGameStateToServer(
        balance: Int,
        spinsCount: Int,
        biggestWin: Int,
        visitedLocations: List<Boolean>,
        selectedLines: Int,
        lastShakeTime: Long
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getUserId()
                val json = JSONObject().apply {
                    put("userId", userId)
                    put("balance", balance)
                    put("spinsCount", spinsCount)
                    put("biggestWin", biggestWin)
                    put("visitedLocations", JSONArray(visitedLocations))
                    put("selectedLines", selectedLines)
                    put("lastShakeTime", lastShakeTime)
                }

                Log.d(TAG, "💾 Synchronizuję stan gry: $json")

                val request = Request.Builder()
                    .url("$baseUrl/game-state")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d(TAG, "Kod odpowiedzi synchronizacji: ${response.code}")

                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Błąd synchronizacji stanu gry: ${e.message}")
                false
            }
        }
    }

    // Pobierz stan gry z serwera
    suspend fun loadGameStateFromServer(): GameState? {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getUserId()
                Log.d(TAG, "🔍 Ładuję stan gry z serwera dla: $userId")

                val request = Request.Builder()
                    .url("$baseUrl/game-state/$userId")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d(TAG, "Kod odpowiedzi ładowania stanu: ${response.code}")
                Log.d(TAG, "Odpowiedź ładowania stanu: $responseBody")

                if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                    val json = JSONObject(responseBody)

                    // 🔽 WALIDACJA - sprawdź czy serwer zwrócił prawidłowe dane
                    if (!json.has("balance")) {
                        Log.e(TAG, "❌ SERWER NIE ZWRÓCIŁ STANU GRY - brak kluczowych pól")
                        Log.e(TAG, "❌ Dostępne pola: ${json.keys().asSequence().toList()}")
                        return@withContext null // NIE zwracaj domyślnych wartości!
                    }

                    val visitedLocationsArray = json.optJSONArray("visitedLocations")
                    val visitedLocations = mutableListOf<Boolean>()
                    if (visitedLocationsArray != null) {
                        for (i in 0 until visitedLocationsArray.length()) {
                            visitedLocations.add(visitedLocationsArray.getBoolean(i))
                        }
                    } else {
                        // Domyślne wartości TYLKO gdy serwer zwrócił stan
                        visitedLocations.addAll(listOf(false, false, false))
                    }

                    // 🔽 UŻYJ getInt() zamiast optInt() dla pól obowiązkowych
                    val gameState = GameState(
                        balance = json.getInt("balance"), // 🔽 BRAK fallback value!
                        spinsCount = json.optInt("spinsCount", 0),
                        biggestWin = json.optInt("biggestWin", 0),
                        visitedLocations = visitedLocations,
                        selectedLines = json.optInt("selectedLines", 1),
                        lastShakeTime = json.optLong("lastShakeTime", 0)
                    )

                    Log.d(
                        TAG,
                        "✅ ZAŁADOWANO STAN Z SERWERA: balance=${gameState.balance}, " +
                                "spins=${gameState.spinsCount}, win=${gameState.biggestWin}"
                    )

                    return@withContext gameState
                } else {
                    Log.e(TAG, "❌ BŁĄD ODPOWIEDZI SERWERA: ${response.code} - $responseBody")
                    return@withContext null // Nie zwracaj nic przy błędzie HTTP
                }
            } catch (e: Exception) {
                Log.e(TAG, "💥 BŁĄD ŁADOWANIA STANU GRY: ${e.message}")
                null
            }
        }
    }






