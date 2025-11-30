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
    
