
package com.example.slotmaster

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class GameHistoryHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "SlotMaster.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_HISTORY = "game_history"

        private const val COLUMN_ID = "id"
        private const val COLUMN_GAME_DATE = "game_date"
        private const val COLUMN_FINAL_BALANCE = "final_balance"
        private const val COLUMN_SPINS_COUNT = "spins_count"
        private const val COLUMN_BIGGEST_WIN = "biggest_win"
        private const val COLUMN_CREATED_AT = "created_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_HISTORY (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_GAME_DATE TEXT NOT NULL,
                $COLUMN_FINAL_BALANCE INTEGER NOT NULL,
                $COLUMN_SPINS_COUNT INTEGER NOT NULL,
                $COLUMN_BIGGEST_WIN INTEGER NOT NULL,
                $COLUMN_CREATED_AT TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }
}
