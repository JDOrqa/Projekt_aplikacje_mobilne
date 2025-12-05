package com.example.slotmaster

data class GameState(
    val balance: Int = 5000,
    val spinsCount: Int = 0,
    val biggestWin: Int = 0,
    val visitedLocations: List<Boolean> = listOf(false, false, false),
    val selectedLines: Int = 1,
    val lastShakeTime: Long = 0
)
