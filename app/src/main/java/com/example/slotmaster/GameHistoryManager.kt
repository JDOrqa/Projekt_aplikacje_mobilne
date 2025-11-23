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
    fun getRecentHistory(days: Int = 7): List<GameHistory> {
        val history = mutableListOf<GameHistory>()
        val db = dbHelper.readableDatabase
         val query = """
            SELECT * FROM $TABLE_HISTORY 
            WHERE game_date >= date('now', '-$days days') 
            ORDER BY game_date DESC
        """.trimIndent()
        val cursor = db.rawQuery(query, null)
        while (cursor.moveToNext()) {
            val gameHistory = GameHistory(
                id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                gameDate = cursor.getString(cursor.getColumnIndexOrThrow("game_date")),
                finalBalance = cursor.getInt(cursor.getColumnIndexOrThrow("final_balance")),
                spinsCount = cursor.getInt(cursor.getColumnIndexOrThrow("spins_count")),
                biggestWin = cursor.getInt(cursor.getColumnIndexOrThrow("biggest_win")),
                createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"))
            )
            history.add(gameHistory)
        }
 cursor.close()
        db.close()
        return history
    }
fun isTodaySaved(): Boolean {
        val db = dbHelper.readableDatabase
        val query = "SELECT COUNT(*) FROM $TABLE_HISTORY WHERE game_date = ?"
        val cursor = db.rawQuery(query, arrayOf(getCurrentDate()))

        cursor.moveToFirst()
        val count = cursor.getInt(0)

        cursor.close()
        db.close()
        return count > 0
    }
fun deleteTodaysRecord(): Boolean {
        val db = dbHelper.writableDatabase
        val result = db.delete(TABLE_HISTORY, "game_date = ?", arrayOf(getCurrentDate()))
        db.close()
        return result > 0
    }
