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
    private val baseUrl = "https://projekt-mobilne-kraj.loca.lt/api"

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

                    Log.d(TAG, "✅ ZAŁADOWANO STAN Z SERWERA: balance=${gameState.balance}, " +
                            "spins=${gameState.spinsCount}, win=${gameState.biggestWin}")

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




    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun getCurrentDateTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    suspend fun saveDailyResult(finalBalance: Int, newSpinsCount: Int, biggestWin: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getUserId()
                val currentDate = getCurrentDate()

                Log.d(TAG, "💾 Zapis wyniku: data=$currentDate, saldo=$finalBalance, spiny=$newSpinsCount, wygrana=$biggestWin")

                // 1. Pobierz istniejący wpis na dzisiaj
                val existingRecord = getTodaysRecord()

                if (existingRecord != null) {
                    // 🔽 WALIDACJA: UŻYJ WIĘKSZEJ WARTOŚCI ZAMIEST SUMOWANIA
                    val currentServerSpins = existingRecord.spinsCount

                    // Jeśli nowa wartość jest większa, użyj jej (ochrona przed duplikacją)
                    val updatedSpinsCount = if (newSpinsCount > currentServerSpins) {
                        Log.d(TAG, "🔄 Aktualizuję spiny: $currentServerSpins -> $newSpinsCount")
                        newSpinsCount
                    } else {
                        Log.d(TAG, "⚠️ Zachowam istniejące spiny: $currentServerSpins (nowe: $newSpinsCount)")
                        currentServerSpins
                    }

                    val updatedBiggestWin = maxOf(existingRecord.biggestWin, biggestWin)

                    Log.d(TAG, "📊 Finał: spiny=$updatedSpinsCount, wygrana=$updatedBiggestWin")

                    return@withContext updateDailyResult(
                        finalBalance = finalBalance,
                        spinsCount = updatedSpinsCount,
                        biggestWin = updatedBiggestWin
                    )
                } else {
                    // Nowy wpis - użyj podanej liczby spinów
                    Log.d(TAG, "🆕 Nowy wpis z spinami: $newSpinsCount")
                    return@withContext createDailyResult(
                        finalBalance = finalBalance,
                        spinsCount = newSpinsCount,
                        biggestWin = biggestWin
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "💥 Błąd zapisu: ${e.message}")
                false
            }
        }
    }

    private suspend fun getTodaysRecord(): GameHistory? {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getUserId()
                val currentDate = getCurrentDate()

                val request = Request.Builder()
                    .url("$baseUrl/game-history/$userId/today")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (!response.isSuccessful) return@withContext null

                val jsonArray = JSONArray(responseBody ?: "[]")
                if (jsonArray.length() > 0) {
                    val jsonObject = jsonArray.getJSONObject(0)
                    return@withContext GameHistory(
                        id = jsonObject.optInt("id", 0),
                        gameDate = getField(jsonObject, "gameDate", "game_date"),
                        finalBalance = jsonObject.optInt("finalBalance", jsonObject.optInt("final_balance", 0)),
                        spinsCount = jsonObject.optInt("spinsCount", jsonObject.optInt("spins_count", 0)),
                        biggestWin = jsonObject.optInt("biggestWin", jsonObject.optInt("biggest_win", 0)),
                        createdAt = getField(jsonObject, "createdAt", "created_at"),
                        userId = getField(jsonObject, "userId", "user_id")
                    )
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Błąd pobierania dzisiejszego wpisu: ${e.message}")
                null
            }
        }
    }

    private suspend fun updateDailyResult(finalBalance: Int, spinsCount: Int, biggestWin: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getUserId()
                val currentDate = getCurrentDate()

                val json = JSONObject().apply {
                    put("userId", userId)
                    put("gameDate", currentDate)
                    put("finalBalance", finalBalance)
                    put("spinsCount", spinsCount)
                    put("biggestWin", biggestWin)
                    put("createdAt", getCurrentDateTime())
                }

                Log.d(TAG, "🔄 Wysyłam aktualizację: $json")

                val request = Request.Builder()
                    .url("$baseUrl/game-history")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d(TAG, "Kod odpowiedzi aktualizacji: ${response.code}")
                Log.d(TAG, "Odpowiedź aktualizacji: $responseBody")

                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Błąd aktualizacji: ${e.message}")
                false
            }
        }
    }

    // Nowa metoda: Utwórz nowy wpis
    private suspend fun createDailyResult(finalBalance: Int, spinsCount: Int, biggestWin: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getUserId()

                val json = JSONObject().apply {
                    put("userId", userId)
                    put("gameDate", getCurrentDate())
                    put("finalBalance", finalBalance)
                    put("spinsCount", spinsCount)
                    put("biggestWin", biggestWin)
                    put("createdAt", getCurrentDateTime())
                }

                Log.d(TAG, "🆕 Wysyłam nowy wpis: $json")

                val request = Request.Builder()
                    .url("$baseUrl/game-history")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d(TAG, "Kod odpowiedzi nowego wpisu: ${response.code}")
                Log.d(TAG, "Odpowiedź nowego wpisu: $responseBody")

                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Błąd tworzenia nowego wpisu: ${e.message}")
                false
            }
        }
    }

    suspend fun getRecentHistory(days: Int = 7): List<GameHistory> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getUserId()
                Log.d(TAG, "Pobieram historię dla: $userId")

                val request = Request.Builder()
                    .url("$baseUrl/game-history/$userId")
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                Log.d(TAG, "Kod odpowiedzi: ${response.code}")
                Log.d(TAG, "Odpowiedź: $responseBody")

                if (!response.isSuccessful) return@withContext emptyList()

                val jsonArray = JSONArray(responseBody ?: "[]")
                val history = mutableListOf<GameHistory>()

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)

                    // Loguj wszystkie pola dla debugu
                    Log.d(TAG, "Otrzymane pola:")
                    val keys = jsonObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        Log.d(TAG, "  $key: ${jsonObject.get(key)}")
                    }

                    val gameHistory = GameHistory(
                        id = jsonObject.optInt("id", 0),
                        gameDate = getField(jsonObject, "gameDate", "game_date"),
                        finalBalance = jsonObject.optInt("finalBalance", jsonObject.optInt("final_balance", 0)),
                        spinsCount = jsonObject.optInt("spinsCount", jsonObject.optInt("spins_count", 0)),
                        biggestWin = jsonObject.optInt("biggestWin", jsonObject.optInt("biggest_win", 0)),
                        createdAt = getField(jsonObject, "createdAt", "created_at"),
                        userId = getField(jsonObject, "userId", "user_id")
                    )
                    history.add(gameHistory)
                    Log.d(TAG, "Utworzono GameHistory: $gameHistory")
                }

                Log.d(TAG, "Pobrano ${history.size} wpisów historii")
                history
            } catch (e: Exception) {
                Log.e(TAG, "Błąd pobierania historii: ${e.message}")
                emptyList()
            }
        }
    }

    private fun getField(jsonObject: JSONObject, primaryField: String, fallbackField: String): String {
        return if (jsonObject.has(primaryField)) {
            jsonObject.optString(primaryField, "")
        } else {
            jsonObject.optString(fallbackField, "")
        }
    }

    suspend fun isTodaySaved(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val history = getRecentHistory(1)
                val today = getCurrentDate()
                val exists = history.any { it.gameDate == today }
                Log.d(TAG, "Dzisiejszy wpis istnieje: $exists")
                exists
            } catch (e: Exception) {
                Log.e(TAG, "Błąd sprawdzania dzisiejszego zapisu: ${e.message}")
                false
            }
        }
    }

    suspend fun deleteTodaysRecord(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                saveDailyResult(0, 0, 0)
            } catch (e: Exception) {
                Log.e(TAG, "Błąd usuwania: ${e.message}")
                false
            }
        }
    }

    suspend fun clearAllHistory(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getUserId()
                Log.d(TAG, "Usuwam historię dla: $userId")

                val request = Request.Builder()
                    .url("$baseUrl/game-history/$userId")
                    .delete()
                    .build()

                val response = client.newCall(request).execute()
                Log.d(TAG, "Kod odpowiedzi usuwania: ${response.code}")

                response.isSuccessful
            } catch (e: Exception) {
                Log.e(TAG, "Błąd czyszczenia historii: ${e.message}")
                false
            }
        }
    }
}