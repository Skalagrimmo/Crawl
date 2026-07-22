package com.example.model

enum class WeatherCondition {
    CLEAR, DATA_DRIFT, DATA_STORM, GLITCH_RAIN
}

data class WeatherState(
    val condition: WeatherCondition = WeatherCondition.CLEAR,
    val turnsRemaining: Int = 15,
    val description: String = "GRID CORES OPERATING NORMALLY // STATIC CLEAR"
)
