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
