package com.example.engine

import com.example.model.*
import kotlin.math.abs
import kotlin.math.sqrt

enum class LightType {
    STREET, NEON, WINDOW, PLAYER, ENEMY
}

data class LightSource(
    val position: Pair<Int, Int>,
    val radius: Float,
    val intensity: Float, // 0.0 to 1.0
    val colorHex: Long,  // ARGB color hex representation
    val type: LightType,
    val direction: Pair<Int, Int>? = null // Direction for cones (player/enemy)
)

class LightingManager {

    fun generateLightSources(worldState: WorldState): List<LightSource> {
        val sources = mutableListOf<LightSource>()

        // 1. Streetlights on City Roads (every 6-8 tiles along roads)
        for ((pos, _) in worldState.doors) {
            sources.add(
                LightSource(
                    position = pos,
                    radius = 4.5f,
                    intensity = 0.85f,
                    colorHex = 0xFF00FFCC, // Cyber cyan portal glow
                    type = LightType.NEON
                )
            )
        }

        for (room in worldState.rooms) {
            val center = Pair(room.centerX, room.centerY)
            when (room.type) {
                RoomType.COMMERCIAL, RoomType.OFFICE -> {
                    sources.add(
                        LightSource(
                            position = center,
                            radius = 6.0f,
                            intensity = 0.8f,
                            colorHex = 0xFFFF00FF, // Magenta neon
                            type = LightType.NEON
                        )
                    )
                }
                RoomType.SECURITY, RoomType.ARMORY -> {
                    sources.add(
                        LightSource(
                            position = center,
                            radius = 5.0f,
                            intensity = 0.9f,
                            colorHex = 0xFFFF3333, // Red warning light
                            type = LightType.NEON
                        )
                    )
                }
                RoomType.SERVER_FARM -> {
                    sources.add(
                        LightSource(
                            position = center,
                            radius = 5.5f,
                            intensity = 0.75f,
                            colorHex = 0xFF00E5FF, // Electric blue
                            type = LightType.NEON
                        )
                    )
                }
                RoomType.RESIDENTIAL, RoomType.MEDBAY -> {
                    sources.add(
                        LightSource(
                            position = center,
                            radius = 5.0f,
                            intensity = 0.7f,
                            colorHex = 0xFFFFFF99, // Warm window amber
                            type = LightType.WINDOW
                        )
                    )
                }
                else -> {
                    sources.add(
                        LightSource(
                            position = center,
                            radius = 4.0f,
                            intensity = 0.6f,
                            colorHex = 0xFFFFB703,
                            type = LightType.STREET
                        )
                    )
                }
            }
        }

        // Streetlights along road tiles
        for (road in worldState.roadTiles) {
            if ((road.first % 7 == 0) && (road.second % 7 == 0)) {
                sources.add(
                    LightSource(
                        position = road,
                        radius = 5.0f,
                        intensity = 0.85f,
                        colorHex = 0xFFFFD166, // Golden streetlight
                        type = LightType.STREET
                    )
                )
            }
        }

        // 2. Player Flashlight
        val playerPos = Pair(worldState.playerX, worldState.playerY)
        sources.add(
            LightSource(
                position = playerPos,
                radius = 5.5f,
                intensity = 0.95f,
                colorHex = 0xFFFFFFFF,
                type = LightType.PLAYER,
                direction = worldState.playerDirection
            )
        )

        // 3. Enemy Searchlights
        for ((enemyPos, _) in worldState.enemies) {
            sources.add(
                LightSource(
                    position = enemyPos,
                    radius = 4.0f,
                    intensity = 0.85f,
                    colorHex = 0xFFFF0055, // Red search cone
                    type = LightType.ENEMY
                )
            )
        }

        return sources
    }

    fun computeLightMap(
        worldState: WorldState,
        nightVisionActive: Boolean = false
    ): Map<Pair<Int, Int>, Float> {
        val map = mutableMapOf<Pair<Int, Int>, Float>()
        val baseAmbient = if (worldState.zone == ZoneType.CITY) 0.25f else 0.35f
        val sources = generateLightSources(worldState)

        // Compute light intensity for explored tiles
        for (tile in worldState.explored) {
            var brightness = baseAmbient

            if (nightVisionActive) {
                brightness = maxOf(brightness, 0.85f)
            } else {
                for (source in sources) {
                    val dx = (tile.first - source.position.first).toFloat()
                    val dy = (tile.second - source.position.second).toFloat()
                    val dist = sqrt(dx * dx + dy * dy)

                    if (dist <= source.radius) {
                        val falloff = (1.0f - dist / source.radius).coerceIn(0.0f, 1.0f)
                        
                        // Check cone direction if source is directional (e.g. Flashlight)
                        var directionBonus = 1.0f
                        if (source.direction != null && source.direction != Pair(0, 0)) {
                            val dot = (dx * source.direction.first + dy * source.direction.second) / (dist.coerceAtLeast(0.1f))
                            directionBonus = if (dot > 0.3f) 1.4f else 0.3f
                        }

                        brightness += source.intensity * falloff * directionBonus
                    }
                }
            }

            map[tile] = brightness.coerceIn(0.0f, 1.0f)
        }

        return map
    }

    fun getPlayerLightExposure(
        playerPos: Pair<Int, Int>,
        lightMap: Map<Pair<Int, Int>, Float>
    ): Float {
        return lightMap[playerPos] ?: 0.35f
    }

    fun getEnemyDetectionMultiplier(
        playerPos: Pair<Int, Int>,
        lightMap: Map<Pair<Int, Int>, Float>,
        weather: WeatherCondition
    ): Float {
        val lightLevel = getPlayerLightExposure(playerPos, lightMap)
        
        // Base multiplier from lighting:
        // Dark tile (< 0.35) -> 0.7x detection (30% harder to detect in shadows)
        // Bright tile (> 0.70) -> 1.3x detection (30% easier to detect in spotlight)
        val lightMult = when {
            lightLevel < 0.35f -> 0.70f
            lightLevel > 0.70f -> 1.30f
            else -> 1.0f
        }

        // Apply weather concealment reduction
        val finalMult = (lightMult - weather.concealmentBonus).coerceIn(0.3f, 1.6f)
        return finalMult
    }
}
