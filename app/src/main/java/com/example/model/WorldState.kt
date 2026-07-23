package com.example.model

enum class ZoneType {
    BUILDING, COLLECTORS, CITY
}

enum class RoomType {
    SERVER_FARM,
    SECURITY,
    STORAGE,
    OFFICE,
    ARMORY,
    MEDBAY,
    RESIDENTIAL,
    COMMERCIAL,
    INDUSTRIAL,
    PARK,
    WATER
}

enum class LootType {
    CREDITS, MEDKIT, SHIELD_CELL, WEAPON_MOD, KEYCARD, TERMINAL, COMPUTER, CAMERA, DATA_STORE, HEALING_STATION
}

data class RoomData(
    val id: Int,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val type: RoomType,
    val isDangerZone: Boolean = false,
    val name: String = type.name.lowercase().replace('_', ' ').capitalize()
) {
    val centerX: Int get() = x + w / 2
    val centerY: Int get() = y + h / 2
    fun contains(px: Int, py: Int): Boolean = px >= x && px < x + w && py >= y && py < y + h
}

data class Enemy(
    val id: String,
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val shield: Int,
    val maxShield: Int,
    val attack: Int,
    val defense: Int,
    val expValue: Int,
    val creditValue: Int,
    val type: String,
    val position: Pair<Int, Int>
)

data class Door(
    val position: Pair<Int, Int>,
    val isLocked: Boolean = false,
    val isOpen: Boolean = false,
    val isHacked: Boolean = false,
    val transitionZone: ZoneType? = null,
    val transitionFloor: Int? = null,
    val transitionTargetPos: Pair<Int, Int>? = null
)

data class GenerationParams(
    val floorNumber: Int,
    val zone: ZoneType,
    val width: Int = 50,
    val height: Int = 50,
    val maxDepth: Int = 5,
    val minRoomSize: Int = 6,
    val maxRoomSize: Int = 12,
    val targetRoomCount: Int = 12
)

data class DungeonResult(
    val width: Int,
    val height: Int,
    val playerStart: Pair<Int, Int>,
    val exitPosition: Pair<Int, Int>,
    val walls: Set<Pair<Int, Int>>,
    val roadTiles: Set<Pair<Int, Int>> = emptySet(),
    val waterTiles: Set<Pair<Int, Int>> = emptySet(),
    val rooms: List<RoomData> = emptyList(),
    val doors: Map<Pair<Int, Int>, Door> = emptyMap(),
    val enemies: Map<Pair<Int, Int>, Enemy> = emptyMap(),
    val loot: Map<Pair<Int, Int>, LootType> = emptyMap()
)

data class FloorData(
    val floorNumber: Int,
    val zone: ZoneType,
    val width: Int,
    val height: Int,
    val playerStart: Pair<Int, Int>,
    val exitPosition: Pair<Int, Int>,
    val walls: Set<Pair<Int, Int>>,
    val roadTiles: Set<Pair<Int, Int>> = emptySet(),
    val waterTiles: Set<Pair<Int, Int>> = emptySet(),
    val rooms: List<RoomData> = emptyList(),
    val enemies: Map<Pair<Int, Int>, Enemy>,
    val loot: Map<Pair<Int, Int>, LootType>,
    val doors: Map<Pair<Int, Int>, Door>,
    val explored: Set<Pair<Int, Int>>
)

data class WorldState(
    val currentFloor: Int = 1,
    val zone: ZoneType = ZoneType.CITY,
    val playerX: Int = 1,
    val playerY: Int = 1,
    val playerDirection: Pair<Int, Int> = Pair(0, 1), // facing Down initially
    val width: Int = 70,
    val height: Int = 70,
    val exitPosition: Pair<Int, Int> = Pair(65, 65),
    val walls: Set<Pair<Int, Int>> = emptySet(),
    val roadTiles: Set<Pair<Int, Int>> = emptySet(),
    val waterTiles: Set<Pair<Int, Int>> = emptySet(),
    val rooms: List<RoomData> = emptyList(),
    val enemies: Map<Pair<Int, Int>, Enemy> = emptyMap(),
    val loot: Map<Pair<Int, Int>, LootType> = emptyMap(),
    val doors: Map<Pair<Int, Int>, Door> = emptyMap(),
    val explored: Set<Pair<Int, Int>> = emptySet()
)

