package com.example.model

enum class WeatherCondition(
    val displayName: String,
    val description: String,
    val noiseReduction: Float, // 0.0 to 0.5 (masks footstep noise)
    val visionModifier: Int,    // tile vision penalty
    val concealmentBonus: Float, // 0.0 to 0.4
    val glitchDistortion: Boolean
) {
    CLEAR(
        displayName = "CLEAR SKY",
        description = "GRID CORES OPERATING NORMALLY // STATIC CLEAR",
        noiseReduction = 0.0f,
        visionModifier = 0,
        concealmentBonus = 0.0f,
        glitchDistortion = false
    ),
    LIGHT_RAIN(
        displayName = "ACID RAIN (LIGHT)",
        description = "MILD ACID DRIZZLE // FOOTSTEPS AUDITORY MASK 25%",
        noiseReduction = 0.25f,
        visionModifier = 0,
        concealmentBonus = 0.1f,
        glitchDistortion = false
    ),
    HEAVY_RAIN(
        displayName = "HEAVY DOWNPOUR",
        description = "DENSE ACID RAIN // FOOTSTEPS AUDITORY MASK 50%",
        noiseReduction = 0.5f,
        visionModifier = -1,
        concealmentBonus = 0.25f,
        glitchDistortion = false
    ),
    STORM(
        displayName = "DATA STORM",
        description = "ELECTROMAGNETIC TEMPEST // ALL UNITS VISION -2 TILES",
        noiseReduction = 0.4f,
        visionModifier = -2,
        concealmentBonus = 0.3f,
        glitchDistortion = true
    ),
    GLITCH_RAIN(
        displayName = "GLITCH CASCADE",
        description = "NEURAL SHADER CORRUPTION // VISUAL SCANLINE ARTIFACTS",
        noiseReduction = 0.1f,
        visionModifier = -1,
        concealmentBonus = 0.2f,
        glitchDistortion = true
    ),
    FOG(
        displayName = "SMOG / DENSE FOG",
        description = "TOXIC INDUSTRIAL SMOG // CONCEALMENT +40%",
        noiseReduction = 0.15f,
        visionModifier = -2,
        concealmentBonus = 0.4f,
        glitchDistortion = false
    ),
    NEON_HAZE(
        displayName = "NEON HAZE",
        description = "ATMOSPHERIC NEON PARTICLES // ILLUMINATION GAIN",
        noiseReduction = 0.0f,
        visionModifier = 1,
        concealmentBonus = 0.05f,
        glitchDistortion = false
    )
}

data class WeatherState(
    val condition: WeatherCondition = WeatherCondition.CLEAR,
    val turnsRemaining: Int = 15,
    val forecast: WeatherCondition = WeatherCondition.LIGHT_RAIN,
    val description: String = condition.description
)

