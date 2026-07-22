package com.example.model

enum class ZoneType {
    BUILDING, COLLECTORS, CITY
}

enum class LootType {
    CREDITS, MEDKIT, SHIELD_CELL, WEAPON_MOD, KEYCARD
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
    val isHacked: Boolean = false
)

data class FloorData(
    val floorNumber: Int,
    val zone: ZoneType,
    val width: Int,
    val height: Int,
    val playerStart: Pair<Int, Int>,
    val exitPosition: Pair<Int, Int>,
    val walls: Set<Pair<Int, Int>>,
    val enemies: Map<Pair<Int, Int>, Enemy>,
    val loot: Map<Pair<Int, Int>, LootType>,
    val doors: Map<Pair<Int, Int>, Door>,
    val explored: Set<Pair<Int, Int>>
)

data class WorldState(
    val currentFloor: Int = 1,
    val zone: ZoneType = ZoneType.BUILDING,
    val playerX: Int = 1,
    val playerY: Int = 1,
    val playerDirection: Pair<Int, Int> = Pair(0, 1), // facing Down initially
    val width: Int = 15,
    val height: Int = 15,
    val exitPosition: Pair<Int, Int> = Pair(13, 13),
    val walls: Set<Pair<Int, Int>> = emptySet(),
    val enemies: Map<Pair<Int, Int>, Enemy> = emptyMap(),
    val loot: Map<Pair<Int, Int>, LootType> = emptyMap(),
    val doors: Map<Pair<Int, Int>, Door> = emptyMap(),
    val explored: Set<Pair<Int, Int>> = emptySet()
)
