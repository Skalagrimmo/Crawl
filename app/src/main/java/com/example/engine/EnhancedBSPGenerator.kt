package com.example.engine

import com.example.model.*
import kotlin.random.Random

class EnhancedBSPGenerator {

    private class BSPNode(val x: Int, val y: Int, val w: Int, val h: Int) {
        var left: BSPNode? = null
        var right: BSPNode? = null
        var room: RoomData? = null

        fun split(minSize: Int, maxDepth: Int, currentDepth: Int): Boolean {
            if (currentDepth >= maxDepth) return false
            if (left != null || right != null) return false

            val splitH = if (w / h.toFloat() >= 1.25f) false
            else if (h / w.toFloat() >= 1.25f) true
            else Random.nextBoolean()

            val max = (if (splitH) h else w) - minSize
            if (max <= minSize) return false

            val splitPos = Random.nextInt(minSize, max)

            if (splitH) {
                left = BSPNode(x, y, w, splitPos)
                right = BSPNode(x, y + splitPos, w, h - splitPos)
            } else {
                left = BSPNode(x, y, splitPos, h)
                right = BSPNode(x + splitPos, y, w - splitPos, h)
            }
            return true
        }

        fun getLeaves(): List<BSPNode> {
            val list = mutableListOf<BSPNode>()
            if (left == null && right == null) {
                list.add(this)
            } else {
                left?.let { list.addAll(it.getLeaves()) }
                right?.let { list.addAll(it.getLeaves()) }
            }
            return list
        }
    }

    fun generateBuilding(params: GenerationParams): DungeonResult {
        val width = params.width.coerceIn(40, 60)
        val height = params.height.coerceIn(40, 60)
        val floorNum = params.floorNumber

        val root = BSPNode(1, 1, width - 2, height - 2)
        val maxDepth = params.maxDepth.coerceIn(4, 6)
        val minRoomSize = params.minRoomSize.coerceIn(5, 8)

        // Recursively build BSP Tree
        fun splitTree(node: BSPNode, depth: Int) {
            if (depth < maxDepth && node.split(minRoomSize + 2, maxDepth, depth)) {
                node.left?.let { splitTree(it, depth + 1) }
                node.right?.let { splitTree(it, depth + 1) }
            }
        }
        splitTree(root, 0)

        val leaves = root.getLeaves()
        val walls = mutableSetOf<Pair<Int, Int>>()
        for (x in 0 until width) {
            for (y in 0 until height) {
                walls.add(Pair(x, y))
            }
        }

        val roomTypes = listOf(
            RoomType.SERVER_FARM,
            RoomType.SECURITY,
            RoomType.STORAGE,
            RoomType.OFFICE,
            RoomType.ARMORY,
            RoomType.MEDBAY
        )

        val rooms = mutableListOf<RoomData>()
        var roomIdCounter = 1

        // Carve rooms inside BSP leaves
        for (leaf in leaves) {
            val roomW = Random.nextInt(minRoomSize, minOf(leaf.w - 1, params.maxRoomSize))
            val roomH = Random.nextInt(minRoomSize, minOf(leaf.h - 1, params.maxRoomSize))
            val roomX = leaf.x + Random.nextInt(0, maxOf(1, leaf.w - roomW))
            val roomY = leaf.y + Random.nextInt(0, maxOf(1, leaf.h - roomH))

            val type = roomTypes.random()
            val isDanger = type == RoomType.SECURITY || type == RoomType.ARMORY

            val room = RoomData(
                id = roomIdCounter++,
                x = roomX,
                y = roomY,
                w = roomW,
                h = roomH,
                type = type,
                isDangerZone = isDanger
            )
            leaf.room = room
            rooms.add(room)

            // Carve room floor
            for (rx in roomX until roomX + roomW) {
                for (ry in roomY until roomY + roomH) {
                    walls.remove(Pair(rx, ry))
                }
            }
        }

        // Connect rooms with straight corridors
        fun connectNodes(node1: BSPNode, node2: BSPNode) {
            val r1 = node1.room ?: return
            val r2 = node2.room ?: return

            var cx = r1.centerX
            var cy = r1.centerY
            val targetX = r2.centerX
            val targetY = r2.centerY

            while (cx != targetX) {
                walls.remove(Pair(cx, cy))
                cx += if (targetX > cx) 1 else -1
            }
            while (cy != targetY) {
                walls.remove(Pair(cx, cy))
                cy += if (targetY > cy) 1 else -1
            }
        }

        fun connectBSPTree(node: BSPNode) {
            val l = node.left
            val r = node.right
            if (l != null && r != null) {
                val lLeaves = l.getLeaves()
                val rLeaves = r.getLeaves()
                if (lLeaves.isNotEmpty() && rLeaves.isNotEmpty()) {
                    connectNodes(lLeaves.random(), rLeaves.random())
                }
                connectBSPTree(l)
                connectBSPTree(r)
            }
        }
        connectBSPTree(root)

        val playerStart = Pair(rooms.first().centerX, rooms.first().centerY)
        val exitPosition = Pair(rooms.last().centerX, rooms.last().centerY)

        // Doors & Interfaces
        val doors = mutableMapOf<Pair<Int, Int>, Door>()
        for (room in rooms) {
            // Find perimeter points where room wall meets corridor
            for (x in room.x until room.x + room.w) {
                val topCoord = Pair(x, room.y - 1)
                val bottomCoord = Pair(x, room.y + room.h)
                if (!walls.contains(topCoord) && doors.size < 20) {
                    val isLocked = Random.nextFloat() < 0.35f
                    doors[Pair(x, room.y)] = Door(Pair(x, room.y), isLocked = isLocked)
                }
                if (!walls.contains(bottomCoord) && doors.size < 20) {
                    val isLocked = Random.nextFloat() < 0.35f
                    doors[Pair(x, room.y + room.h - 1)] = Door(Pair(x, room.y + room.h - 1), isLocked = isLocked)
                }
            }
        }

        // Enemies & Loot based on room types
        val enemies = mutableMapOf<Pair<Int, Int>, Enemy>()
        val loot = mutableMapOf<Pair<Int, Int>, LootType>()

        var enemyId = 1
        for (room in rooms) {
            val validTiles = mutableListOf<Pair<Int, Int>>()
            for (x in (room.x + 1) until (room.x + room.w - 1)) {
                for (y in (room.y + 1) until (room.y + room.h - 1)) {
                    val pos = Pair(x, y)
                    if (pos != playerStart && pos != exitPosition && !doors.containsKey(pos)) {
                        validTiles.add(pos)
                    }
                }
            }
            validTiles.shuffle()

            // Spawn thematic room objects
            when (room.type) {
                RoomType.SERVER_FARM -> {
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.TERMINAL
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.DATA_STORE
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.CREDITS
                }
                RoomType.SECURITY -> {
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.CAMERA
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.KEYCARD
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.SHIELD_CELL
                }
                RoomType.STORAGE -> {
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.CREDITS
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.MEDKIT
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.WEAPON_MOD
                }
                RoomType.OFFICE -> {
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.COMPUTER
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.CREDITS
                }
                RoomType.ARMORY -> {
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.WEAPON_MOD
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.SHIELD_CELL
                }
                RoomType.MEDBAY -> {
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.MEDKIT
                    if (validTiles.isNotEmpty()) loot[validTiles.removeAt(0)] = LootType.HEALING_STATION
                }
                else -> {}
            }

            // Spawn enemies in danger zones and standard rooms
            val enemyCount = if (room.isDangerZone) 2 else if (Random.nextFloat() < 0.6f) 1 else 0
            for (e in 0 until enemyCount) {
                if (validTiles.isNotEmpty()) {
                    val pos = validTiles.removeAt(0)
                    val hp = 25 + floorNum * 15
                    val attack = 7 + floorNum * 3
                    val defense = 2 + floorNum * 2
                    val exp = 20 + floorNum * 12
                    val credits = 30 + floorNum * 15

                    val name = when (room.type) {
                        RoomType.SECURITY -> "SecOps Sentinel v${floorNum}.0"
                        RoomType.ARMORY -> "Heavy Enforcer Droid"
                        RoomType.SERVER_FARM -> "A.I. Core Intruder Guard"
                        else -> "Cyber Scrapper Unit"
                    }

                    enemies[pos] = Enemy(
                        id = "enemy_${floorNum}_${enemyId++}",
                        name = name,
                        hp = hp,
                        maxHp = hp,
                        shield = 10 + floorNum * 8,
                        maxShield = 10 + floorNum * 8,
                        attack = attack,
                        defense = defense,
                        expValue = exp,
                        creditValue = credits,
                        type = name,
                        position = pos
                    )
                }
            }
        }

        return DungeonResult(
            width = width,
            height = height,
            playerStart = playerStart,
            exitPosition = exitPosition,
            walls = walls,
            rooms = rooms,
            doors = doors,
            enemies = enemies,
            loot = loot
        )
    }

    fun generateCity(params: GenerationParams): DungeonResult {
        val width = 70
        val height = 70

        val root = BSPNode(2, 2, width - 4, height - 4)
        fun splitTree(node: BSPNode, depth: Int) {
            if (depth < 4 && node.split(12, 4, depth)) {
                node.left?.let { splitTree(it, depth + 1) }
                node.right?.let { splitTree(it, depth + 1) }
            }
        }
        splitTree(root, 0)

        val leaves = root.getLeaves()
        val walls = mutableSetOf<Pair<Int, Int>>()
        val roadTiles = mutableSetOf<Pair<Int, Int>>()
        val waterTiles = mutableSetOf<Pair<Int, Int>>()

        // Start with empty terrain
        val cityTypes = listOf(
            RoomType.RESIDENTIAL,
            RoomType.COMMERCIAL,
            RoomType.INDUSTRIAL,
            RoomType.PARK,
            RoomType.WATER
        )

        val rooms = mutableListOf<RoomData>()
        var blockId = 1

        // Carve City Blocks & Roads
        for (x in 0 until width) {
            for (y in 0 until height) {
                // Perimeter city barrier
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    walls.add(Pair(x, y))
                } else {
                    roadTiles.add(Pair(x, y)) // Base asphalt ground
                }
            }
        }

        val doors = mutableMapOf<Pair<Int, Int>, Door>()

        for (leaf in leaves) {
            val type = cityTypes[(blockId - 1) % cityTypes.size]
            val bx = leaf.x + 2
            val by = leaf.y + 2
            val bw = maxOf(6, leaf.w - 4)
            val bh = maxOf(6, leaf.h - 4)

            val room = RoomData(
                id = blockId++,
                x = bx,
                y = by,
                w = bw,
                h = bh,
                type = type
            )
            rooms.add(room)

            // Fill block based on City Zone Type
            for (x in bx until (bx + bw)) {
                for (y in by until (by + bh)) {
                    val pos = Pair(x, y)
                    roadTiles.remove(pos)

                    when (type) {
                        RoomType.PARK -> {
                            if ((x + y) % 5 == 0) {
                                waterTiles.add(pos)
                            }
                        }
                        RoomType.WATER -> {
                            waterTiles.add(pos)
                        }
                        else -> {
                            // Buildings have outer walls
                            if (x == bx || x == bx + bw - 1 || y == by || y == by + bh - 1) {
                                walls.add(pos)
                            }
                        }
                    }
                }
            }

            // Metroid-style Entrance Doors for Buildings
            if (type != RoomType.PARK && type != RoomType.WATER) {
                val doorPos = Pair(bx + bw / 2, by + bh - 1)
                walls.remove(doorPos)
                doors[doorPos] = Door(
                    position = doorPos,
                    isOpen = false,
                    isLocked = false,
                    transitionZone = ZoneType.BUILDING,
                    transitionFloor = 2,
                    transitionTargetPos = Pair(25, 25)
                )
            }
        }

        val playerStart = Pair(rooms.first().centerX, rooms.first().centerY)
        val exitPosition = Pair(rooms.last().centerX, rooms.last().centerY)

        // Spawn Street Enemies & Terminals
        val enemies = mutableMapOf<Pair<Int, Int>, Enemy>()
        val loot = mutableMapOf<Pair<Int, Int>, LootType>()

        var enemyCounter = 1
        for (room in rooms) {
            val validRoads = roadTiles.filter { room.contains(it.first, it.second) }.toMutableList()
            validRoads.shuffle()

            if (validRoads.isNotEmpty()) {
                loot[validRoads.removeAt(0)] = LootType.TERMINAL
            }
            if (validRoads.isNotEmpty()) {
                loot[validRoads.removeAt(0)] = LootType.CREDITS
            }

            if (validRoads.isNotEmpty()) {
                val pos = validRoads.removeAt(0)
                enemies[pos] = Enemy(
                    id = "city_enemy_${enemyCounter++}",
                    name = "Syndicate Street Thug",
                    hp = 30,
                    maxHp = 30,
                    shield = 10,
                    maxShield = 10,
                    attack = 8,
                    defense = 2,
                    expValue = 25,
                    creditValue = 35,
                    type = "Syndicate Thug",
                    position = pos
                )
            }
        }

        return DungeonResult(
            width = width,
            height = height,
            playerStart = playerStart,
            exitPosition = exitPosition,
            walls = walls,
            roadTiles = roadTiles,
            waterTiles = waterTiles,
            rooms = rooms,
            doors = doors,
            enemies = enemies,
            loot = loot
        )
    }
}
