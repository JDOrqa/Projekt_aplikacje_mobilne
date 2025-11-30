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
    
