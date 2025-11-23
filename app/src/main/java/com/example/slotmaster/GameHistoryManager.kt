package com.example.slotmaster

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.slotmaster.models.GameHistory
import java.text.SimpleDateFormat
import java.util.*
class GameHistoryManager(private val dbHelper: GameHistoryHelper) {


    companion object {
        private const val TABLE_HISTORY = "game_history"
    }
 private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private fun getCurrentDateTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
fun saveDailyResult(finalBalance: Int, spinsCount: Int, biggestWin: Int): Boolean {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("game_date", getCurrentDate())
            put("final_balance", finalBalance)
            put("spins_count", spinsCount)
            put("biggest_win", biggestWin)
            put("created_at", getCurrentDateTime())
        }
 val result = db.insert(TABLE_HISTORY, null, values)
        db.close()
        return result != -1L
    }
