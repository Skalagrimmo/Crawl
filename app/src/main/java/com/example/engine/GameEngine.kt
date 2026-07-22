package com.example.engine

import com.example.model.*
import kotlin.random.Random

object GameEngine {

    // Procedurally generates a floor map based on the floor number and zone
    fun generateFloor(floorNumber: Int): FloorData {
        val zone = when {
            floorNumber <= 3 -> ZoneType.BUILDING
            floorNumber <= 6 -> ZoneType.COLLECTORS
            else -> ZoneType.CITY
        }

        val width = 15
        val height = 15

        // Start with a solid block of walls
        val walls = mutableSetOf<Pair<Int, Int>>()
        for (x in 0 until width) {
            for (y in 0 until height) {
                walls.add(Pair(x, y))
            }
        }

        // Room representation
        data class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
            val centerX: Int get() = x + w / 2
            val centerY: Int get() = y + h / 2
        }

        val rooms = mutableListOf<Room>()
        val numRooms = 3 + Random.nextInt(3) // 3 to 5 rooms

        // Try to place rooms
        var attempts = 0
        while (rooms.size < numRooms && attempts < 100) {
            attempts++
            // Room size: 3x3 to 4x4 for a compact 15x15 map
            val w = 3 + Random.nextInt(2)
            val h = 3 + Random.nextInt(2)
            val rx = 1 + Random.nextInt(width - w - 1)
            val ry = 1 + Random.nextInt(height - h - 1)

            // Check overlap
            val overlap = rooms.any { other ->
                rx < other.x + other.w && rx + w > other.x &&
                ry < other.y + other.h && ry + h > other.y
            }

            if (!overlap) {
                rooms.add(Room(rx, ry, w, h))
            }
        }

        // Carve rooms
        for (room in rooms) {
            for (x in room.x until room.x + room.w) {
                for (y in room.y until room.y + room.h) {
                    walls.remove(Pair(x, y))
                }
            }
        }

        // Connect rooms with corridors
        fun carveCorridor(x1: Int, y1: Int, x2: Int, y2: Int) {
            // Horizontal then vertical
            val startX = minOf(x1, x2)
            val endX = maxOf(x1, x2)
            for (cx in startX..endX) {
                walls.remove(Pair(cx, y1))
            }
            val startY = minOf(y1, y2)
            val endY = maxOf(y1, y2)
            for (cy in startY..endY) {
                walls.remove(Pair(x2, cy))
            }
        }

        for (i in 0 until rooms.size - 1) {
            carveCorridor(
                rooms[i].centerX, rooms[i].centerY,
                rooms[i + 1].centerX, rooms[i + 1].centerY
            )
        }

        // Player starting location
        val playerStart = Pair(rooms.first().centerX, rooms.first().centerY)

        // Exit position in the last room
        val exitPosition = Pair(rooms.last().centerX, rooms.last().centerY)
        if (exitPosition == playerStart) {
            // Edge case: fallback if only one room placed
            val fallbackExit = Pair(width - 2, height - 2)
            walls.remove(fallbackExit)
        }

        // Find empty spots (excluding starting position and exit)
        val emptyTiles = mutableListOf<Pair<Int, Int>>()
        for (x in 1 until width - 1) {
            for (y in 1 until height - 1) {
                val tile = Pair(x, y)
                if (!walls.contains(tile) && tile != playerStart && tile != exitPosition) {
                    emptyTiles.add(tile)
                }
            }
        }
        emptyTiles.shuffle()

        // Place Doors (at boundary interfaces between corridors and rooms)
        val doors = mutableMapOf<Pair<Int, Int>, Door>()
        // We find positions where there is a wall up-down and empty left-right, or vice versa
        val doorPlacementOpportunities = mutableListOf<Pair<Int, Int>>()
        for (x in 2 until width - 2) {
            for (y in 2 until height - 2) {
                val tile = Pair(x, y)
                if (!walls.contains(tile) && tile != playerStart && tile != exitPosition) {
                    val upWall = walls.contains(Pair(x, y - 1))
                    val downWall = walls.contains(Pair(x, y + 1))
                    val leftWall = walls.contains(Pair(x - 1, y))
                    val rightWall = walls.contains(Pair(x + 1, y))
                    
                    if ((upWall && downWall && !leftWall && !rightWall) || 
                        (leftWall && rightWall && !upWall && !downWall)) {
                        doorPlacementOpportunities.add(tile)
                    }
                }
            }
        }

        doorPlacementOpportunities.shuffle()
        val numDoors = minOf(3, doorPlacementOpportunities.size)
        for (i in 0 until numDoors) {
            val pos = doorPlacementOpportunities[i]
            emptyTiles.remove(pos) // don't place enemy/loot on doors
            // 40% chance of locked door
            val isLocked = Random.nextFloat() < 0.4f
            doors[pos] = Door(position = pos, isLocked = isLocked, isHacked = false)
        }

        // Place Enemies
        val enemies = mutableMapOf<Pair<Int, Int>, Enemy>()
        val enemyCount = 3 + Random.nextInt(3) // 3 to 5 enemies
        val finalEnemyCount = minOf(enemyCount, emptyTiles.size)

        val enemyNames = when (zone) {
            ZoneType.BUILDING -> listOf("Sentry Droid v1.2", "SecOps Agent", "A.I. Drone Scout")
            ZoneType.COLLECTORS -> listOf("Steam Scrapper", "Rusty Marauder", "Pipeline Hunter")
            ZoneType.CITY -> listOf("Syndicate Thug", "Nano-Ninja Elite", "Corp Exec-Slayer")
        }

        for (i in 0 until finalEnemyCount) {
            val pos = emptyTiles.removeAt(0)
            val name = enemyNames.random()
            
            // Stats scaling with floor
            val hp = 30 + floorNumber * 12
            val shield = 10 + floorNumber * 8
            val attack = 8 + floorNumber * 2
            val defense = 2 + floorNumber * 2
            val exp = 20 + floorNumber * 10
            val credits = 25 + floorNumber * 15

            enemies[pos] = Enemy(
                id = "enemy_${floorNumber}_$i",
                name = name,
                hp = hp,
                maxHp = hp,
                shield = shield,
                maxShield = shield,
                attack = attack,
                defense = defense,
                expValue = exp,
                creditValue = credits,
                type = name,
                position = pos
            )
        }

        // Place Loot
        val loot = mutableMapOf<Pair<Int, Int>, LootType>()
        val lootCount = 3 + Random.nextInt(3) // 3 to 5 loots
        val finalLootCount = minOf(lootCount, emptyTiles.size)
        
        val lootPool = listOf(
            LootType.CREDITS, LootType.CREDITS, // more common
            LootType.MEDKIT,
            LootType.SHIELD_CELL,
            LootType.WEAPON_MOD,
            LootType.KEYCARD
        )

        for (i in 0 until finalLootCount) {
            val pos = emptyTiles.removeAt(0)
            loot[pos] = lootPool.random()
        }

        // Explored set starting with player's visible range around players
        val explored = mutableSetOf<Pair<Int, Int>>()
        revealArea(playerStart.first, playerStart.second, explored, width, height)

        return FloorData(
            floorNumber = floorNumber,
            zone = zone,
            width = width,
            height = height,
            playerStart = playerStart,
            exitPosition = exitPosition,
            walls = walls,
            enemies = enemies,
            loot = loot,
            doors = doors,
            explored = explored
        )
    }

    // Reveals a 3x3 surrounding area when exploring
    fun revealArea(px: Int, py: Int, explored: MutableSet<Pair<Int, Int>>, width: Int, height: Int) {
        for (dx in -2..2) {
            for (dy in -2..2) {
                val nx = px + dx
                val ny = py + dy
                if (nx in 0 until width && ny in 0 until height) {
                    // Simple line-of-sight check can be modeled, but 2-step Manhattan distance is perfect and clean
                    if (Math.abs(dx) + Math.abs(dy) <= 3) {
                        explored.add(Pair(nx, ny))
                    }
                }
            }
        }
    }

    // Connects box drawing coordinates based on adjacent wall positions
    private fun getBuildingWallChar(x: Int, y: Int, walls: Set<Pair<Int, Int>>, w: Int, h: Int): Char {
        // Double lines connection logic
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
        // Single lines / pipelines connection logic
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

    // Main 2D grid text renderer for the whole level map
    fun renderMapToLines(worldState: WorldState): List<String> {
        val lines = mutableListOf<String>()
        val w = worldState.width
        val h = worldState.height

        for (y in 0 until h) {
            val sb = StringBuilder()
            for (x in 0 until w) {
                val coord = Pair(x, y)

                // Fog of War check: If it has never been explored, render as empty black space
                if (!worldState.explored.contains(coord)) {
                    sb.append("  ") // 2 spaces for square aspect ratio
                    continue
                }

                // Aspect ratio modifier: render each cell with 2 characters (e.g. wall '██', player '▲ ')
                when {
                    x == worldState.playerX && y == worldState.playerY -> {
                        // Show player direction symbol
                        val playerChar = when (worldState.playerDirection) {
                            Pair(0, -1) -> "▲" // Up
                            Pair(0, 1) -> "▼"  // Down
                            Pair(-1, 0) -> "◀" // Left
                            Pair(1, 0) -> "▶"  // Right
                            else -> "◆"
                        }
                        sb.append("$playerChar ")
                    }
                    worldState.enemies.containsKey(coord) -> {
                        val enemy = worldState.enemies[coord]!!
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
                        }
                        sb.append("$char ")
                    }
                    worldState.doors.containsKey(coord) -> {
                        val door = worldState.doors[coord]!!
                        val char = when {
                            door.isHacked || !door.isLocked -> "🔓"
                            else -> "🔒"
                        }
                        sb.append("$char") // emoji is usually double width
                    }
                    x == worldState.exitPosition.first && y == worldState.exitPosition.second -> {
                        sb.append("⛛ ") // Exit lift
                    }
                    worldState.walls.contains(coord) -> {
                        val wallChar = when (worldState.zone) {
                            ZoneType.BUILDING -> getBuildingWallChar(x, y, worldState.walls, w, h)
                            ZoneType.COLLECTORS -> getCollectorsWallChar(x, y, worldState.walls, w, h)
                            ZoneType.CITY -> '█' // Bold block towers
                        }
                        sb.append("$wallChar$wallChar") // double-up for square proportions
                    }
                    else -> {
                        // Floor background elements
                        val bgChar = when (worldState.zone) {
                            ZoneType.BUILDING -> "· "
                            ZoneType.COLLECTORS -> {
                                if (Random(x * 31 + y * 17).nextFloat() < 0.1f) "♨ " else "· " // Steam exhaust vents
                            }
                            ZoneType.CITY -> {
                                if (Random(x * 19 + y * 23).nextFloat() < 0.08f) "⁞ " else "  " // digital rain
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

        // We want 9 rows, 9 columns centered on player (from px-4 to px+4, py-4 to py+4)
        for (yOffset in -4..4) {
            val sb = StringBuilder()
            val y = py + yOffset
            for (xOffset in -4..4) {
                val x = px + xOffset
                val coord = Pair(x, y)

                // Check bounds
                if (x < 0 || x >= worldState.width || y < 0 || y >= worldState.height) {
                    sb.append("░") // Out of level boundaries
                    continue
                }

                // Fog of War check: If it has never been explored, render as dim block
                if (!worldState.explored.contains(coord)) {
                    sb.append("▒")
                    continue
                }

                // Render cell contents
                when {
                    x == px && y == py -> {
                        sb.append("◆") // Player position in minimap
                    }
                    worldState.enemies.containsKey(coord) -> {
                        sb.append("☠")
                    }
                    worldState.loot.containsKey(coord) -> {
                        sb.append("?")
                    }
                    worldState.doors.containsKey(coord) -> {
                        val door = worldState.doors[coord]!!
                        if (door.isLocked && !door.isHacked) sb.append("⊞") else sb.append("⌸")
                    }
                    x == worldState.exitPosition.first && y == worldState.exitPosition.second -> {
                        sb.append("⛛")
                    }
                    worldState.walls.contains(coord) -> {
                        sb.append("█")
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
