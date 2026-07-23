package com.example.engine

import com.example.model.*
import kotlin.random.Random

object GameEngine {

    private val bspGenerator = EnhancedBSPGenerator()

    // Procedurally generates a floor map based on the floor number and zone using Enhanced BSP
    fun generateFloor(floorNumber: Int): FloorData {
        val zone = when {
            floorNumber == 1 -> ZoneType.CITY
            floorNumber <= 6 -> ZoneType.BUILDING
            floorNumber <= 11 -> ZoneType.COLLECTORS
            else -> ZoneType.BUILDING
        }

        val params = GenerationParams(
            floorNumber = floorNumber,
            zone = zone,
            width = if (zone == ZoneType.CITY) 70 else 50 + minOf(10, floorNumber),
            height = if (zone == ZoneType.CITY) 70 else 50 + minOf(10, floorNumber),
            maxDepth = 5,
            minRoomSize = 6,
            maxRoomSize = 12,
            targetRoomCount = 12
        )

        val result = if (zone == ZoneType.CITY) {
            bspGenerator.generateCity(params)
        } else {
            bspGenerator.generateBuilding(params)
        }

        // Explored set starting with player's visible range around starting position
        val explored = mutableSetOf<Pair<Int, Int>>()
        revealArea(result.playerStart.first, result.playerStart.second, explored, result.width, result.height)

        return FloorData(
            floorNumber = floorNumber,
            zone = zone,
            width = result.width,
            height = result.height,
            playerStart = result.playerStart,
            exitPosition = result.exitPosition,
            walls = result.walls,
            roadTiles = result.roadTiles,
            waterTiles = result.waterTiles,
            rooms = result.rooms,
            enemies = result.enemies,
            loot = result.loot,
            doors = result.doors,
            explored = explored
        )
    }

    // Reveals a 3-4 tile radius surrounding area when exploring
    fun revealArea(px: Int, py: Int, explored: MutableSet<Pair<Int, Int>>, width: Int, height: Int) {
        val radius = 4
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                val nx = px + dx
                val ny = py + dy
                if (nx in 0 until width && ny in 0 until height) {
                    if (Math.abs(dx) + Math.abs(dy) <= radius) {
                        explored.add(Pair(nx, ny))
                    }
                }
            }
        }
    }

    // Connects box drawing coordinates based on adjacent wall positions
    private fun getBuildingWallChar(x: Int, y: Int, walls: Set<Pair<Int, Int>>, w: Int, h: Int): Char {
        val u = y > 0 && (walls.contains(Pair(x, y - 1)) || y - 1 == 0)
        val d = y < h - 1 && (walls.contains(Pair(x, y + 1)) || y + 1 == h - 1)
        val l = x > 0 && (walls.contains(Pair(x - 1, y)) || x - 1 == 0)
        val r = x < w - 1 && (walls.contains(Pair(x + 1, y)) || x + 1 == w - 1)

        return when {
            u && d && l && r -> '╬'
            u && d && l -> '╣'
            u && d && r -> '╠'
            l && r && u -> '╩'
            l && r && d -> '╦'
            u && d -> '║'
            l && r -> '═'
            d && r -> '╔'
            d && l -> '╗'
            u && r -> '╚'
            u && l -> '╝'
            u || d -> '║'
            l || r -> '═'
            else -> '■'
        }
    }

    private fun getCollectorsWallChar(x: Int, y: Int, walls: Set<Pair<Int, Int>>, w: Int, h: Int): Char {
        val u = y > 0 && (walls.contains(Pair(x, y - 1)) || y - 1 == 0)
        val d = y < h - 1 && (walls.contains(Pair(x, y + 1)) || y + 1 == h - 1)
        val l = x > 0 && (walls.contains(Pair(x - 1, y)) || x - 1 == 0)
        val r = x < w - 1 && (walls.contains(Pair(x + 1, y)) || x + 1 == w - 1)

        return when {
            u && d && l && r -> '┼'
            u && d && l -> '┤'
            u && d && r -> '├'
            l && r && u -> '┴'
            l && r && d -> '┬'
            u && d -> '│'
            l && r -> '─'
            d && r -> '┌'
            d && l -> '┐'
            u && r -> '└'
            u && l -> '┘'
            u || d -> '│'
            l || r -> '─'
            else -> '■'
        }
    }

    // Main 2D grid text renderer for level map
    fun renderMapToLines(worldState: WorldState): List<String> {
        val lines = mutableListOf<String>()
        val w = worldState.width
        val h = worldState.height

        for (y in 0 until h) {
            val sb = StringBuilder()
            for (x in 0 until w) {
                val coord = Pair(x, y)

                if (!worldState.explored.contains(coord)) {
                    sb.append("  ")
                    continue
                }

                when {
                    x == worldState.playerX && y == worldState.playerY -> {
                        val playerChar = when (worldState.playerDirection) {
                            Pair(0, -1) -> "▲"
                            Pair(0, 1) -> "▼"
                            Pair(-1, 0) -> "◀"
                            Pair(1, 0) -> "▶"
                            else -> "◆"
                        }
                        sb.append("$playerChar ")
                    }
                    worldState.enemies.containsKey(coord) -> {
                        val char = when (worldState.zone) {
                            ZoneType.BUILDING -> "⌁"
                            ZoneType.COLLECTORS -> "⏆"
                            ZoneType.CITY -> "☠"
                        }
                        sb.append("$char ")
                    }
                    worldState.loot.containsKey(coord) -> {
                        val lootType = worldState.loot[coord]!!
                        val char = when (lootType) {
                            LootType.CREDITS -> "⛀"
                            LootType.MEDKIT -> "⛃"
                            LootType.SHIELD_CELL -> "⛁"
                            LootType.WEAPON_MOD -> "⚙"
                            LootType.KEYCARD -> "⚿"
                            LootType.TERMINAL -> "🖥"
                            LootType.COMPUTER -> "💻"
                            LootType.CAMERA -> "👁"
                            LootType.DATA_STORE -> "🗄"
                            LootType.HEALING_STATION -> "🩹"
                        }
                        sb.append("$char ")
                    }
                    worldState.doors.containsKey(coord) -> {
                        val door = worldState.doors[coord]!!
                        val char = when {
                            door.isOpen || door.isHacked || !door.isLocked -> "🔓"
                            else -> "🔒"
                        }
                        sb.append("$char")
                    }
                    x == worldState.exitPosition.first && y == worldState.exitPosition.second -> {
                        sb.append("⛛ ")
                    }
                    worldState.waterTiles.contains(coord) -> {
                        sb.append("approx ")
                    }
                    worldState.roadTiles.contains(coord) -> {
                        sb.append("░░")
                    }
                    worldState.walls.contains(coord) -> {
                        val wallChar = when (worldState.zone) {
                            ZoneType.BUILDING -> getBuildingWallChar(x, y, worldState.walls, w, h)
                            ZoneType.COLLECTORS -> getCollectorsWallChar(x, y, worldState.walls, w, h)
                            ZoneType.CITY -> '█'
                        }
                        sb.append("$wallChar$wallChar")
                    }
                    else -> {
                        val bgChar = when (worldState.zone) {
                            ZoneType.BUILDING -> "· "
                            ZoneType.COLLECTORS -> {
                                if (Random(x * 31 + y * 17).nextFloat() < 0.1f) "♨ " else "· "
                            }
                            ZoneType.CITY -> {
                                if (Random(x * 19 + y * 23).nextFloat() < 0.08f) "⁞ " else "  "
                            }
                        }
                        sb.append(bgChar)
                    }
                }
            }
            lines.add(sb.toString())
        }
        return lines
    }

    // Generates a 9x9 viewport minimap centered around the player
    fun generateMinimap(worldState: WorldState): List<String> {
        val minimap = mutableListOf<String>()
        val px = worldState.playerX
        val py = worldState.playerY

        for (yOffset in -4..4) {
            val sb = StringBuilder()
            val y = py + yOffset
            for (xOffset in -4..4) {
                val x = px + xOffset
                val coord = Pair(x, y)

                if (x < 0 || x >= worldState.width || y < 0 || y >= worldState.height) {
                    sb.append("░")
                    continue
                }

                if (!worldState.explored.contains(coord)) {
                    sb.append("▒")
                    continue
                }

                when {
                    x == px && y == py -> {
                        sb.append("◆")
                    }
                    worldState.enemies.containsKey(coord) -> {
                        sb.append("☠")
                    }
                    worldState.loot.containsKey(coord) -> {
                        sb.append("?")
                    }
                    worldState.doors.containsKey(coord) -> {
                        val door = worldState.doors[coord]!!
                        if (door.isLocked && !door.isHacked && !door.isOpen) sb.append("⊞") else sb.append("⌸")
                    }
                    x == worldState.exitPosition.first && y == worldState.exitPosition.second -> {
                        sb.append("⛛")
                    }
                    worldState.walls.contains(coord) -> {
                        sb.append("█")
                    }
                    worldState.waterTiles.contains(coord) -> {
                        sb.append("~")
                    }
                    worldState.roadTiles.contains(coord) -> {
                        sb.append("=")
                    }
                    else -> {
                        sb.append("·")
                    }
                }
            }
            minimap.add(sb.toString())
        }
        return minimap
    }
}
