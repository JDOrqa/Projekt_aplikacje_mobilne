package com.example.slotmaster.models

data class GameHistory(
    val id: Int,
    val gameDate: String,
    val finalBalance: Int,
    val spinsCount: Int,
    val biggestWin: Int,
    val createdAt: String,
    val userId: String = ""

)