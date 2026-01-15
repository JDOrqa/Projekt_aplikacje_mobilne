package com.example.slotmaster

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.slotmaster.databinding.ActivitySimpleLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Log
import kotlinx.coroutines.delay


class SimpleLoginActivity : AppCompatActivity() {
    companion object {
        private const val PREFS_NAME = "SlotMasterPrefs"
    }
    private lateinit var binding: ActivitySimpleLoginBinding
    private val client = OkHttpClient()
    private val baseUrl = "https://projekt-mobilne-kraj.loca.lt/api"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Sprawdź czy już zalogowany
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val savedUser = prefs.getString("username", null)
        if (savedUser != null) {
            goToMain(savedUser)
            return
        }

        binding.btnLogin.setOnClickListener { tryLogin() }
        binding.btnRegister.setOnClickListener { tryRegister() }
        binding.btnSkip.setOnClickListener { skip() }
    }

    private fun tryLogin() {
        val user = binding.etLogin.text.toString().trim()
        val pass = binding.etPass.text.toString().trim()

        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Wpisz login i hasło", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔽 UŻYJ FirebirdApiManager DO LOGOWANIA
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("username", user)
                    put("password", pass)
                }

                val request = Request.Builder()
                    .url("$baseUrl/login")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                runOnUiThread {
                    if (response.isSuccessful && body != null) {
                        val result = JSONObject(body)
                        if (result.optBoolean("ok", false)) {
                            val username = result.optString("username", user)

                            // 🔽 ZAPISZ LOGIN I USTAW USER_ID W FirebirdApiManager
                            val apiManager = FirebirdApiManager(this@SimpleLoginActivity)
                            val userId = "user_$username"  // Taki sam format jak w createUser()
                            apiManager.setUserId(userId)  // 🔽 TO JEST KLUCZOWE!

                            saveLogin(username, userId)
                            goToMain(username)
                            Toast.makeText(this@SimpleLoginActivity, "Witaj $username!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@SimpleLoginActivity,
                                result.optString("error", "Błąd"),
                                Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this@SimpleLoginActivity, "Serwer nie odpowiada", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@SimpleLoginActivity, "Błąd połączenia", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun tryRegister() {
        val user = binding.etLogin.text.toString().trim()
        val pass = binding.etPass.text.toString().trim()

        if (user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, "Wpisz login i hasło", Toast.LENGTH_SHORT).show()
            return
        }

        if (pass.length < 3) {
            Toast.makeText(this, "Hasło min. 3 znaki", Toast.LENGTH_SHORT).show()
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                // Najpierw sprawdź czy login wolny (przez API)
                val checkRequest = Request.Builder()
                    .url("$baseUrl/check-login/$user")
                    .build()

                val checkResponse = client.newCall(checkRequest).execute()
                val checkBody = checkResponse.body?.string()

                if (checkResponse.isSuccessful && checkBody != null) {
                    val checkResult = JSONObject(checkBody)
                    if (!checkResult.optBoolean("available", true)) {
                        runOnUiThread {
                            Toast.makeText(this@SimpleLoginActivity, "Login zajęty", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                }

                runOnUiThread {
                    Toast.makeText(this@SimpleLoginActivity, "Rejestracja...", Toast.LENGTH_SHORT).show()
                }

                val registerJson = JSONObject().apply {
                    put("username", user)
                    put("password", pass)
                }

                val registerRequest = Request.Builder()
                    .url("$baseUrl/register")
                    .post(registerJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val registerResponse = client.newCall(registerRequest).execute()
                val registerBody = registerResponse.body?.string()

                if (registerResponse.isSuccessful && registerBody != null) {
                    val result = JSONObject(registerBody)
                    if (result.optBoolean("ok", false)) {
                        val userId = result.optString("userId", "user_$user")

                        Log.d("SimpleLoginActivity", "✅ Zarejestrowano, userId: $userId")

                        // 🔽 WAŻNE: NAJPIERW ZAPISZ DO SHAREDPREFERENCES
                        saveLogin(user, userId)

                        // 🔽 DODATKOWO: ZAPISZ W FirebirdApiManager (to zrobi saveLogin)
                        // Ale dla pewności:
                        val apiManager = FirebirdApiManager(this@SimpleLoginActivity)
                        apiManager.setUserId(userId)

                        // 🔽 DODAJ MAŁE OPÓŹNIENIE DLA PEWNOŚCI
                        delay(200) // 100ms opóźnienia

                        runOnUiThread {
                            Toast.makeText(
                                this@SimpleLoginActivity,
                                "✅ Zarejestrowano! ID: $userId",
                                Toast.LENGTH_LONG
                            ).show()
                            goToMain(user)
                        }
                    } else {
                        val error = result.optString("error", "Błąd rejestracji")
                        runOnUiThread {
                            Toast.makeText(this@SimpleLoginActivity, error, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@SimpleLoginActivity, "Błąd serwera", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@SimpleLoginActivity, "Błąd rejestracji: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun skip() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val userId = "user_guest_${System.currentTimeMillis()}"

        prefs.edit().apply {
            putBoolean("guest", true)
            putString("user_id", userId)
            apply()
        }

        // 🔽 USTAW USER_ID DLA GOŚCIA
        val apiManager = FirebirdApiManager(this)
        apiManager.setUserId(userId)

        goToMain("Gość")
    }

    private fun saveLogin(username: String, userId: String? = null) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val finalUserId = userId ?: "user_$username"

        Log.d("SimpleLoginActivity", "💾 Zapisuję login do $PREFS_NAME:")
        Log.d("SimpleLoginActivity", "  - username: $username")
        Log.d("SimpleLoginActivity", "  - user_id: $finalUserId")
        Log.d("SimpleLoginActivity", "  - guest: false")

        prefs.edit().apply {
            putString("username", username)
            putString("user_id", finalUserId)
            putBoolean("guest", false)
            apply() // Asynchroniczny zapis
        }

        // 🔽 RÓWNIEŻ ZAPISZ DO FirebirdPrefs
        val firebirdPrefs = getSharedPreferences("FirebirdPrefs", MODE_PRIVATE)
        firebirdPrefs.edit().putString("user_id", finalUserId).apply()

        Log.d("SimpleLoginActivity", "✅ Zapisano w obu SharedPreferences")

        // 🔽 DODATKOWO: Sprawdź czy zapisano
        val savedUsername = prefs.getString("username", null)
        val savedUserId = prefs.getString("user_id", null)
        Log.d("SimpleLoginActivity", "🔍 Weryfikacja zapisu:")
        Log.d("SimpleLoginActivity", "  - saved username: $savedUsername")
        Log.d("SimpleLoginActivity", "  - saved user_id: $savedUserId")
    }

    private fun goToMain(username: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("username", username)
        }
        startActivity(intent)
        finish()
    }

    fun logout(view: android.view.View) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().clear().apply()
        startActivity(Intent(this, SimpleLoginActivity::class.java))
        finish()
    }
}