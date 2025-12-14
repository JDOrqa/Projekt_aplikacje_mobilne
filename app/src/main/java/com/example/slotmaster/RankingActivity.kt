package com.example.slotmaster

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.slotmaster.databinding.ActivityRankingBinding
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RankingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRankingBinding
    private lateinit var firebirdApiManager: FirebirdApiManager
    private lateinit var rankingAdapter: RankingAdapter
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRankingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "🏆 Ranking Graczy"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        firebirdApiManager = FirebirdApiManager(this)

        setupRecyclerView()
        loadRankingData()

        binding.btnRefresh.setOnClickListener {
            loadRankingData()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            loadRankingData()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupRecyclerView() {
        rankingAdapter = RankingAdapter(emptyList())

        binding.recyclerViewRanking.apply {
            layoutManager = LinearLayoutManager(this@RankingActivity)
            adapter = rankingAdapter
            setHasFixedSize(true)
        }
    }

    private fun loadRankingData() {
        binding.swipeRefreshLayout.isRefreshing = true
        binding.tvLoading.text = "Ładowanie rankingu..."

        scope.launch {
            try {
                // Pobierz wszystkich użytkowników
                val users = firebirdApiManager.getUsers()

                // Jeśli nie ma użytkowników, pokaż komunikat
                if (users.isEmpty()) {
                    runOnUiThread {
                        binding.tvLoading.text = "Brak danych rankingowych"
                        binding.swipeRefreshLayout.isRefreshing = false
                    }
                    return@launch
                }

                // Przygotuj dane rankingowe
                val rankingData = prepareRankingData(users)

                // Posortuj według największej wygranej (malejąco)
                val sortedRanking = rankingData.sortedByDescending { it.biggestWin }

                runOnUiThread {
                    if (sortedRanking.isNotEmpty()) {
                        rankingAdapter.updateData(sortedRanking)
                        binding.tvLoading.text = "Znaleziono ${sortedRanking.size} graczy"

                        // Pokaż statystyki
                        showRankingStats(sortedRanking)
                    } else {
                        binding.tvLoading.text = "Brak danych do wyświetlenia"
                    }
                    binding.swipeRefreshLayout.isRefreshing = false
                }

            } catch (e: Exception) {
                Log.e("RankingActivity", "Błąd ładowania rankingu: ${e.message}")
                runOnUiThread {
                    binding.tvLoading.text = "Błąd ładowania rankingu"
                    binding.swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(
                        this@RankingActivity,
                        "Nie można załadować rankingu",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun prepareRankingData(users: List<User>): List<RankingItem> {
        return users.map { user ->
            RankingItem(
                userId = user.userId,
                userName = user.userName.ifEmpty { extractUserNameFromId(user.userId) },
                biggestWin = user.balance, // Używamy balance jako biggestWin
                rank = 0, // Tymczasowo 0, posortujemy później
                gamesCount = 0 // Możesz dodać liczbę gier jeśli masz takie dane
            )
        }
    }

    private fun showRankingStats(ranking: List<RankingItem>) {
        if (ranking.isNotEmpty()) {
            val topPlayer = ranking.first()
            val totalPlayers = ranking.size
            val avgWin = ranking.map { it.biggestWin }.average().toInt()

            binding.tvStats.text = "🎯 Top: ${topPlayer.userName} (${topPlayer.biggestWin}💰)\n" +
                    "👥 Graczy: $totalPlayers | 📊 Śr. wygrana: $avgWin💰"
        }
    }

    private fun extractUserNameFromId(userId: String): String {
        return if (userId.startsWith("user_") && userId.contains("_")) {
            val parts = userId.split("_")
            if (parts.size >= 2) {
                parts[1].replace("_", " ").split(" ").joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
            } else {
                "Anonimowy"
            }
        } else {
            "Anonimowy"
        }
    }
}

data class RankingItem(
    val userId: String,
    val userName: String,
    val biggestWin: Int,
    val rank: Int,
    val gamesCount: Int = 0
)