package com.example.viewmodel

import androidx.lifecycle.ViewModel
import com.example.engine.GameEngine
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlin.random.Random

// Represents the state of our breach protocol style hacking puzzle
data class HackingMinigameState(
    val active: Boolean = false,
    val targetDoorPos: Pair<Int, Int>? = null,
    val targetLootPos: Pair<Int, Int>? = null,
    val matrix: List<List<String>> = emptyList(),
    val sequence: List<String> = emptyList(),
    val buffer: List<String> = emptyList(),
    val isRowSelection: Boolean = true, // alternates between row and column
    val activeIndex: Int = 0, // row or column index currently allowed to click
    val solved: Boolean = false,
    val failed: Boolean = false,
    val selectedCells: Set<Pair<Int, Int>> = emptySet()
)

class GameViewModel : ViewModel() {

    // --- State Split Flows ---
    private val _playerStats = MutableStateFlow(PlayerStats())
    val playerStats: StateFlow<PlayerStats> = _playerStats.asStateFlow()

    private val _worldState = MutableStateFlow(WorldState())
    val worldState: StateFlow<WorldState> = _worldState.asStateFlow()

    private val _combatState = MutableStateFlow(CombatState())
    val combatState: StateFlow<CombatState> = _combatState.asStateFlow()

    private val _weatherState = MutableStateFlow(WeatherState())
    val weatherState: StateFlow<WeatherState> = _weatherState.asStateFlow()

    private val _hackingState = MutableStateFlow(HackingMinigameState())
    val hackingState: StateFlow<HackingMinigameState> = _hackingState.asStateFlow()

    private val _shopActive = MutableStateFlow(false)
    val shopActive: StateFlow<Boolean> = _shopActive.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    // --- Private Floor Cache (Lazy Loading) ---
    private val floorCache = mutableMapOf<Int, FloorData>()

    init {
        log("SYS_INIT // Booting sector crawler firmware...")
        log("DECK_SECURE // Cyberdeck online.")
        log("LOCAL_RESONANCE // Clear atmospheric static.")
        loadFloor(1)
    }

    // Exposes terminal logging
    fun log(message: String) {
        _terminalLogs.update { (it + message).takeLast(60) }
    }

    // --- Lazy Floor Loading Implementation ---
    fun loadFloor(floorNumber: Int) {
        val cached = floorCache[floorNumber]
        if (cached != null) {
            log("CACHE_LOAD // Retrieving Sector F-$floorNumber data from local core.")
            _worldState.update {
                WorldState(
                    currentFloor = cached.floorNumber,
                    zone = cached.zone,
                    playerX = cached.playerStart.first,
                    playerY = cached.playerStart.second,
                    exitPosition = cached.exitPosition,
                    walls = cached.walls,
                    enemies = cached.enemies,
                    loot = cached.loot,
                    doors = cached.doors,
                    explored = cached.explored,
                    width = cached.width,
                    height = cached.height
                )
            }
        } else {
            log("SECTOR_GEN // Mapping unknown layer F-$floorNumber procedurally...")
            val generated = GameEngine.generateFloor(floorNumber)
            floorCache[floorNumber] = generated
            _worldState.update {
                WorldState(
                    currentFloor = generated.floorNumber,
                    zone = generated.zone,
                    playerX = generated.playerStart.first,
                    playerY = generated.playerStart.second,
                    exitPosition = generated.exitPosition,
                    walls = generated.walls,
                    enemies = generated.enemies,
                    loot = generated.loot,
                    doors = generated.doors,
                    explored = generated.explored,
                    width = generated.width,
                    height = generated.height
                )
            }
        }
        
        // Refresh weather description for the new zone
        updateWeatherDescription()
    }

    // Saves current active world state into our private floor cache
    private fun cacheCurrentFloorState() {
        val state = _worldState.value
        val cached = floorCache[state.currentFloor]
        if (cached != null) {
            floorCache[state.currentFloor] = cached.copy(
                playerStart = Pair(state.playerX, state.playerY),
                enemies = state.enemies,
                loot = state.loot,
                doors = state.doors,
                explored = state.explored
            )
        }
    }

    // --- Turn-Based Movement & Exploring Grid ---
    fun movePlayer(dx: Int, dy: Int) {
        // If in combat, hacking, or shop, freeze movement
        if (_combatState.value.inCombat || _hackingState.value.active || _shopActive.value) return

        val state = _worldState.value
        val targetX = state.playerX + dx
        val targetY = state.playerY + dy
        val targetCoord = Pair(targetX, targetY)

        // Direction facing update
        _worldState.update { it.copy(playerDirection = Pair(dx, dy)) }

        // Wall check
        if (state.walls.contains(targetCoord) || targetX < 0 || targetX >= state.width || targetY < 0 || targetY >= state.height) {
            log("MOVE_ERR // Structural barrier detected at ($targetX, $targetY)")
            return
        }

        // Door interaction & transition check
        if (state.doors.containsKey(targetCoord)) {
            val door = state.doors[targetCoord]!!

            // Handle Metroid-style transition door (e.g. City building entrance or sector lift)
            if (door.transitionFloor != null) {
                val targetFloor = door.transitionFloor
                log("SEAMLESS_TRANSITION // Crossing threshold into Sector F-$targetFloor...")
                cacheCurrentFloorState()
                loadFloor(targetFloor)
                return
            }

            if (door.isLocked && !door.isHacked && !door.isOpen) {
                val stats = _playerStats.value
                if (stats.keycards > 0) {
                    _playerStats.update { it.copy(keycards = stats.keycards - 1) }
                    _worldState.update {
                        val newDoors = it.doors.toMutableMap()
                        newDoors[targetCoord] = door.copy(isLocked = false, isOpen = true, isHacked = true)
                        it.copy(doors = newDoors)
                    }
                    log("SEC_PASS // Unlocked portal using keycard.")
                } else {
                    log("SEC_BLOCK // Locked gateway encountered. Initializing BREACH_PROTOCOL...")
                    startHackingMinigame(targetCoord, null)
                    return
                }
            } else {
                // Open unlocked door
                _worldState.update {
                    val newDoors = it.doors.toMutableMap()
                    newDoors[targetCoord] = door.copy(isOpen = true)
                    it.copy(doors = newDoors)
                }
            }
        }

        // Perform Movement
        _worldState.update {
            val newExplored = it.explored.toMutableSet()
            GameEngine.revealArea(targetX, targetY, newExplored, it.width, it.height)
            it.copy(
                playerX = targetX,
                playerY = targetY,
                explored = newExplored
            )
        }

        // Check Weather effects in the City region
        applyTurnWeatherEffects()

        // Interaction checks on the new tile
        val finalCoord = Pair(targetX, targetY)
        val finalState = _worldState.value

        // Check Enemy Combat trigger
        if (finalState.enemies.containsKey(finalCoord)) {
            val enemy = finalState.enemies[finalCoord]!!
            startCombat(enemy)
            return
        }

        // Check Loot Chest
        if (finalState.loot.containsKey(finalCoord)) {
            val lootType = finalState.loot[finalCoord]!!
            collectLoot(lootType, finalCoord)
        }

        // Incremental Turn Operations (Weather turn down counter)
        advanceWeatherTurn()
    }

    // --- Weather turn-based updates & DATA_STORM effects ---
    private fun advanceWeatherTurn() {
        val ws = _weatherState.value
        val remaining = ws.turnsRemaining - 1
        if (remaining <= 0) {
            // Weather Shift to forecasted condition
            val conditions = WeatherCondition.values()
            val nextCondition = ws.forecast
            val newForecast = conditions[Random.nextInt(conditions.size)]
            val nextTurns = 12 + Random.nextInt(15)
            
            _weatherState.update {
                it.copy(
                    condition = nextCondition,
                    turnsRemaining = nextTurns,
                    forecast = newForecast,
                    description = nextCondition.description
                )
            }
            log("WEATHER_SHIFT // Grid environment changed to: ${nextCondition.displayName}")
            log("FORECAST // Predictive sensor reports next shift: ${newForecast.displayName}")
        } else {
            _weatherState.update { it.copy(turnsRemaining = remaining) }
        }
    }

    private fun updateWeatherDescription() {
        val cond = _weatherState.value.condition
        _weatherState.update { it.copy(description = cond.description) }
    }

    // Toggle Cyberware Optics
    fun toggleNightVision() {
        _playerStats.update {
            val next = !it.isNightVisionActive
            log("CYBERWARE // Night Vision mode ${if (next) "ACTIVATED" else "DEACTIVATED"}")
            it.copy(isNightVisionActive = next)
        }
    }

    fun toggleThermalOptics() {
        _playerStats.update {
            val next = !it.isThermalActive
            log("CYBERWARE // Thermal Optics scanner ${if (next) "ONLINE" else "OFFLINE"}")
            it.copy(isThermalActive = next)
        }
    }

    private fun applyTurnWeatherEffects() {
        val ws = _weatherState.value
        val zone = _worldState.value.zone

        // STORM or GLITCH_RAIN inside the CITY zone triggers movement hazards
        if ((ws.condition == WeatherCondition.STORM || ws.condition == WeatherCondition.GLITCH_RAIN) && zone == ZoneType.CITY) {
            if (Random.nextFloat() < 0.30f) {
                // Hazard Triggered!
                val hazardDmg = 8
                _playerStats.update {
                    var currentShield = it.shield
                    var currentHp = it.hp
                    if (currentShield >= hazardDmg) {
                        currentShield -= hazardDmg
                    } else {
                        val remainder = hazardDmg - currentShield
                        currentShield = 0
                        currentHp = maxOf(0, currentHp - remainder)
                    }
                    it.copy(shield = currentShield, hp = currentHp)
                }
                log("STORM_HIT // High intensity electromagnetic static struck your systems! Suffered 8 HP/Shield damage.")
                
                // Potential player system death check
                checkPlayerDeath()
            }
        }
    }

    // --- Loot & Object Collection ---
    private fun collectLoot(lootType: LootType, coord: Pair<Int, Int>) {
        _worldState.update {
            val mutableLoot = it.loot.toMutableMap()
            mutableLoot.remove(coord)
            it.copy(loot = mutableLoot)
        }

        when (lootType) {
            LootType.CREDITS -> {
                val amt = 40 + Random.nextInt(61)
                _playerStats.update { it.copy(credits = it.credits + amt) }
                log("JACK_IN // Found security bank core. Siphoned $amt Credits!")
            }
            LootType.MEDKIT -> {
                _playerStats.update { it.copy(medkits = it.medkits + 1) }
                log("CONTAINER // Acquired 1 Nanosuture Medkit.")
            }
            LootType.SHIELD_CELL -> {
                _playerStats.update { it.copy(shieldCells = it.shieldCells + 1) }
                log("CONTAINER // Acquired 1 Electro-Shield Core Cell.")
            }
            LootType.WEAPON_MOD -> {
                _playerStats.update { it.copy(weaponModMultiplier = it.weaponModMultiplier + 0.15f) }
                log("CHIP_SET // Embedded tactical weapon module. Physical damage boosted +15%!")
            }
            LootType.KEYCARD -> {
                _playerStats.update { it.copy(keycards = it.keycards + 1) }
                log("SECURITY_PASS // Acquired 1 Physical Gateway Override Keycard.")
            }
            LootType.TERMINAL -> {
                val amt = 75 + Random.nextInt(50)
                _playerStats.update { it.copy(credits = it.credits + amt, hackingSkill = it.hackingSkill + 2) }
                log("TERMINAL // Hacked mainframe terminal. Gained $amt Credits & +2 Cyber-Hacking skill!")
            }
            LootType.COMPUTER -> {
                _playerStats.update { it.copy(hackingSkill = it.hackingSkill + 3) }
                log("WORKSTATION // Extracted encryption keys. Cyber-Hacking skill +3!")
            }
            LootType.CAMERA -> {
                val ws = _worldState.value
                val newExplored = ws.explored.toMutableSet()
                GameEngine.revealArea(coord.first, coord.second, newExplored, ws.width, ws.height)
                _worldState.update { it.copy(explored = newExplored) }
                log("SEC_CAM // Hacked security feeds! Area mapped.")
            }
            LootType.DATA_STORE -> {
                val amt = 100 + Random.nextInt(100)
                _playerStats.update { it.copy(credits = it.credits + amt) }
                log("DATA_CORE // Downloaded classified corporate vault data. Gained $amt Credits!")
            }
            LootType.HEALING_STATION -> {
                _playerStats.update { it.copy(hp = it.maxHp, shield = it.maxShield) }
                log("MEDBAY_STATION // Activated nanite regen pod. Full HP and Shield restored!")
            }
        }
    }

    // --- Morrowind-Style Turn-Based Combat System ---
    private fun startCombat(enemy: Enemy) {
        _combatState.update {
            CombatState(
                inCombat = true,
                activeEnemy = enemy,
                turn = CombatTurn.PLAYER,
                logs = listOf(
                    "COMBAT_ENGAGED // Security Intercept!",
                    "TARGET // ${enemy.name} (${enemy.type}) HP: ${enemy.hp} | Shield: ${enemy.shield}",
                    "COMMAND // Standard security protocols active. Make your tactical strike."
                )
            )
        }
        log("COMBAT_INIT // Encountered enemy: ${enemy.name}")
    }

    fun executeCombatAction(action: String) {
        if (!_combatState.value.inCombat) return
        val currentCombat = _combatState.value
        val enemy = currentCombat.activeEnemy ?: return
        if (currentCombat.turn != CombatTurn.PLAYER) return

        val pStats = _playerStats.value
        var enemyHp = enemy.hp
        var enemyShield = enemy.shield
        var enemyDefense = enemy.defense

        val actionLog = mutableListOf<String>()

        // 1. Player Attack Stage
        when (action) {
            "QUICK_HACK" -> {
                // Bypasses shields entirely! Deals hacking-based system damage
                val baseDmg = pStats.hackingSkill
                val variation = Random.nextInt(5) - 2 // -2 to +2
                val totalDmg = maxOf(5, baseDmg + variation)
                
                enemyHp = maxOf(0, enemyHp - totalDmg)
                actionLog.add("COMBAT // Injecting system Trojan... Shield bypass successful!")
                actionLog.add("DAMAGE // Dealt $totalDmg direct system damage to ${enemy.name}.")
            }
            "BRUTE_FORCE" -> {
                // High physical attack, blocked by armor defense. Hits Shield first, then HP
                val baseAtk = (pStats.attack * pStats.weaponModMultiplier).toInt()
                val variation = Random.nextInt(8) - 3 // -3 to +4
                var totalDmg = maxOf(3, baseAtk + variation - enemyDefense)

                actionLog.add("COMBAT // Powering weapon coil. Heavy weapon blast discharged!")
                if (enemyShield > 0) {
                    if (enemyShield >= totalDmg) {
                        enemyShield -= totalDmg
                        actionLog.add("ABSORB // Dealt $totalDmg kinetic damage. Absorbed by enemy shields.")
                    } else {
                        val remaining = totalDmg - enemyShield
                        enemyShield = 0
                        enemyHp = maxOf(0, enemyHp - remaining)
                        actionLog.add("PIERCE // Broke enemy shields for $enemyShield. Dealt $remaining physical damage to core.")
                    }
                } else {
                    enemyHp = maxOf(0, enemyHp - totalDmg)
                    actionLog.add("DAMAGE // Dealt $totalDmg damage directly to core.")
                }
            }
            "SHIELD_BREAK" -> {
                // Decimates enemy defense and disables shields, deals very light flat damage
                val flatDmg = 8
                actionLog.add("COMBAT // Discharging EM disruptor pulse!")
                if (enemyShield > 0) {
                    actionLog.add("SHATTER // Enemy shield barrier overloaded and disabled completely!")
                    enemyShield = 0
                }
                
                // Halve the enemy armor defense permanently
                val armorStripped = enemyDefense / 2
                enemyDefense = maxOf(0, enemyDefense - armorStripped)
                actionLog.add("STRIP // Enemy structural defense weakened by $armorStripped points.")
                
                enemyHp = maxOf(0, enemyHp - flatDmg)
                actionLog.add("DAMAGE // Dealt $flatDmg impact damage.")
            }
        }

        // Check if enemy dead
        if (enemyHp <= 0) {
            // Victory phase!
            actionLog.add("SUCCESS // Threat neutralized. Recovering credits and clearing sector grid...")
            _combatState.update { it.copy(logs = it.logs + actionLog) }
            
            // Apply rewards
            val rewardedStats = pStats.addExp(enemy.expValue)
            val updatedRewardStats = rewardedStats.copy(credits = rewardedStats.credits + enemy.creditValue)
            _playerStats.value = updatedRewardStats

            // Remove enemy from the map tile
            _worldState.update {
                val newEnemies = it.enemies.toMutableMap()
                newEnemies.remove(enemy.position)
                it.copy(enemies = newEnemies)
            }

            log("VICTORY // Defeated ${enemy.name}. Reward: +${enemy.expValue} EXP, +${enemy.creditValue} Credits.")
            if (updatedRewardStats.level > pStats.level) {
                log("LEVEL_UP // Core system operating at Level ${updatedRewardStats.level} now!")
            }

            // Close combat after a brief display
            _combatState.update { CombatState(inCombat = false) }
            advanceWeatherTurn()
            return
        }

        // Enemy is still alive. Update combat state for player action
        val updatedEnemy = enemy.copy(hp = enemyHp, shield = enemyShield, defense = enemyDefense)
        _combatState.update {
            it.copy(
                activeEnemy = updatedEnemy,
                turn = CombatTurn.ENEMY,
                logs = it.logs + actionLog
            )
        }

        // Trigger Enemy turn automatically (turn-based flow)
        executeEnemyCombatTurn(updatedEnemy)
    }

    private fun executeEnemyCombatTurn(enemy: Enemy) {
        val currentCombat = _combatState.value
        if (!currentCombat.inCombat) return

        val pStats = _playerStats.value
        val enemyLog = mutableListOf<String>()

        enemyLog.add("WARNING // Enemy taking offensive posture...")

        // Enemy action
        val variation = Random.nextInt(6) - 2 // -2 to +3
        val enemyBaseAtk = enemy.attack
        val finalAtk = maxOf(3, enemyBaseAtk + variation - pStats.defense / 2)

        _playerStats.update {
            var currentShield = it.shield
            var currentHp = it.hp
            
            if (currentShield > 0) {
                if (currentShield >= finalAtk) {
                    currentShield -= finalAtk
                    enemyLog.add("HOSTILE // ${enemy.name} fires weapon. Your shield absorbed $finalAtk damage.")
                } else {
                    val remaining = finalAtk - currentShield
                    currentShield = 0
                    currentHp = maxOf(0, currentHp - remaining)
                    enemyLog.add("HOSTILE // Shield barrier breached! Suffered $finalAtk damage ($remaining taken to HP).")
                }
            } else {
                currentHp = maxOf(0, currentHp - finalAtk)
                enemyLog.add("CRITICAL // Kinetic hit directly on chassis! Suffered $finalAtk physical damage.")
            }
            it.copy(shield = currentShield, hp = currentHp)
        }

        // Update turn back to player
        _combatState.update {
            it.copy(
                turn = CombatTurn.PLAYER,
                logs = it.logs + enemyLog + "COMMAND // Action required. Choose attack matrix."
            )
        }

        // Check player death
        checkPlayerDeath()
    }

    private fun checkPlayerDeath() {
        val stats = _playerStats.value
        if (stats.hp <= 0) {
            log("SYS_FAIL // Structural integrity failed. Initiating neural memory clone procedure...")
            // Respawn penalty: lose 25% credits
            val creditPenalty = (stats.credits * 0.25f).toInt()
            _playerStats.update {
                PlayerStats(
                    hp = it.maxHp,
                    shield = it.maxShield,
                    maxHp = it.maxHp,
                    maxShield = it.maxShield,
                    credits = maxOf(0, it.credits - creditPenalty),
                    level = it.level,
                    attack = it.attack,
                    defense = it.defense,
                    hackingSkill = it.hackingSkill,
                    medkits = it.medkits,
                    shieldCells = it.shieldCells,
                    keycards = it.keycards,
                    equippedWeapon = it.equippedWeapon,
                    weaponModMultiplier = it.weaponModMultiplier
                )
            }
            
            // Return to Floor 1 (or starting of floor)
            log("CLONE_REBORN // Reconstructed in safe grid base. System penalty: Deducted $creditPenalty Credits.")
            _combatState.update { CombatState(inCombat = false) }
            _hackingState.update { HackingMinigameState(active = false) }
            _shopActive.update { false }
            loadFloor(1)
        }
    }

    // --- Interactive Hacking Breach Protocol Minigame ---
    private fun startHackingMinigame(doorPos: Pair<Int, Int>?, lootPos: Pair<Int, Int>?) {
        // Generate a 4x4 matrix of codes
        val codePool = listOf("1C", "55", "E9", "7A", "BD")
        val matrix = List(4) { List(4) { codePool.random() } }
        
        // Target sequence of 2-3 items
        val sequenceSize = 2 + Random.nextInt(2) // 2 or 3
        val sequence = List(sequenceSize) { codePool.random() }

        _hackingState.update {
            HackingMinigameState(
                active = true,
                targetDoorPos = doorPos,
                targetLootPos = lootPos,
                matrix = matrix,
                sequence = sequence,
                buffer = emptyList(),
                isRowSelection = true, // start with choosing any cell in Row 0
                activeIndex = 0, // row 0 is active
                solved = false,
                failed = false,
                selectedCells = emptySet()
            )
        }
    }

    fun clickHackingCell(row: Int, col: Int) {
        val hs = _hackingState.value
        if (!hs.active || hs.solved || hs.failed) return

        // Verify if selection is valid based on alternating Row/Col selection rules
        if (hs.isRowSelection) {
            if (row != hs.activeIndex) return // must click within highlighted row
        } else {
            if (col != hs.activeIndex) return // must click within highlighted col
        }

        // Prevent clicking already selected cell
        val cell = Pair(row, col)
        if (hs.selectedCells.contains(cell)) return

        val selectedCode = hs.matrix[row][col]
        val newBuffer = hs.buffer + selectedCode
        val newSelectedCells = hs.selectedCells + cell

        // Check if buffer contains the target sequence in the correct continuous order
        var isSolved = false
        // A buffer matches if the target sequence is a contiguous sublist of the buffer
        if (newBuffer.size >= hs.sequence.size) {
            for (i in 0..(newBuffer.size - hs.sequence.size)) {
                val subList = newBuffer.subList(i, i + hs.sequence.size)
                if (subList == hs.sequence) {
                    isSolved = true
                    break
                }
            }
        }

        val isFailed = !isSolved && newBuffer.size >= 4 // max 4 buffer size

        if (isSolved) {
            // Success! Unlock door or give loot
            _hackingState.update {
                it.copy(
                    buffer = newBuffer,
                    selectedCells = newSelectedCells,
                    solved = true
                )
            }
            applyHackingSuccess()
        } else if (isFailed) {
            // Failed
            _hackingState.update {
                it.copy(
                    buffer = newBuffer,
                    selectedCells = newSelectedCells,
                    failed = true
                )
            }
            log("SEC_ALARM // Matrix breach failed. Intruders detected. Spend Credits or exit to try again.")
        } else {
            // Continue puzzle: Alternates the selection constraint
            _hackingState.update {
                it.copy(
                    buffer = newBuffer,
                    selectedCells = newSelectedCells,
                    isRowSelection = !it.isRowSelection,
                    activeIndex = if (it.isRowSelection) col else row // if row was chosen, next is column of selected cell
                )
            }
        }
    }

    private fun applyHackingSuccess() {
        val hs = _hackingState.value
        val doorPos = hs.targetDoorPos
        val lootPos = hs.targetLootPos

        if (doorPos != null) {
            _worldState.update {
                val newDoors = it.doors.toMutableMap()
                val oldDoor = newDoors[doorPos]
                if (oldDoor != null) {
                    newDoors[doorPos] = oldDoor.copy(isLocked = false, isHacked = true)
                }
                it.copy(doors = newDoors)
            }
            log("BREACH_SUCCESS // Portal safety lock overridden successfully. Path open.")
        }

        // Close minigame automatically after success
        _hackingState.update { HackingMinigameState(active = false) }
        advanceWeatherTurn()
    }

    fun resetHackingMinigame() {
        val hs = _hackingState.value
        if (!hs.active) return
        
        // Spend minor credits to attempt reset or try for free
        val stats = _playerStats.value
        val resetCost = 20
        if (stats.credits >= resetCost) {
            _playerStats.update { it.copy(credits = it.credits - resetCost) }
            log("SYS_RESET // Expended $resetCost Credits to force-reboot target security buffer.")
            startHackingMinigame(hs.targetDoorPos, hs.targetLootPos)
        } else {
            log("RESET_DENIED // Insufficient credits to reboot. Needed $resetCost Credits.")
        }
    }

    fun cancelHacking() {
        _hackingState.update { HackingMinigameState(active = false) }
        log("BREACH_ABORT // Disconnected cyberdeck from portal. Gate remains locked.")
    }

    // --- Inventory Consumables ---
    fun useMedkit() {
        val stats = _playerStats.value
        if (stats.medkits <= 0) {
            log("USE_ERR // No Nanosuture Medkits in deck slots.")
            return
        }
        if (stats.hp >= stats.maxHp) {
            log("USE_ERR // Bodily chassis operating at maximum peak integrity.")
            return
        }

        val healAmt = 50
        _playerStats.update {
            it.copy(
                medkits = it.medkits - 1,
                hp = minOf(it.maxHp, it.hp + healAmt)
            )
        }
        log("SYS_RESTORE // Injected nanite threader. Restored $healAmt HP.")
        
        // If in combat, utilizing a medkit can trigger enemy immediate reaction
        val currentCombat = _combatState.value
        if (currentCombat.inCombat && currentCombat.turn == CombatTurn.PLAYER) {
            // Enemy gets a free counter-strike since player spent action healing
            val enemy = currentCombat.activeEnemy ?: return
            _combatState.update { it.copy(logs = it.logs + "SYSTEM // Consumed item. Turn elapsed.") }
            executeEnemyCombatTurn(enemy)
        }
    }

    fun useShieldCell() {
        val stats = _playerStats.value
        if (stats.shieldCells <= 0) {
            log("USE_ERR // No Electro-Shield Cells in inventory.")
            return
        }
        if (stats.shield >= stats.maxShield) {
            log("USE_ERR // Shield capacitor is already fully saturated.")
            return
        }

        val shieldAmt = 40
        _playerStats.update {
            it.copy(
                shieldCells = it.shieldCells - 1,
                shield = minOf(it.maxShield, it.shield + shieldAmt)
            )
        }
        log("SYS_RESTORE // Recharged shield core. Siphoned $shieldAmt Shield units.")

        // If in combat, consuming a cell causes enemy turn
        val currentCombat = _combatState.value
        if (currentCombat.inCombat && currentCombat.turn == CombatTurn.PLAYER) {
            val enemy = currentCombat.activeEnemy ?: return
            _combatState.update { it.copy(logs = it.logs + "SYSTEM // Recharged barrier. Turn elapsed.") }
            executeEnemyCombatTurn(enemy)
        }
    }

    // --- Black Market Shop (Comm-Link Tab) ---
    fun toggleShop(active: Boolean) {
        if (_combatState.value.inCombat || _hackingState.value.active) {
            log("SHOP_DENIED // Cannot establish Black Market link during active threats/hacks.")
            return
        }
        _shopActive.value = active
        if (active) {
            log("SHOP_LINK // Black Market proxy connection secured. Buy resources.")
        } else {
            log("SHOP_TERM // Link decoupled.")
        }
    }

    fun buyShopItem(itemName: String, cost: Int) {
        val stats = _playerStats.value
        if (stats.credits < cost) {
            log("BUY_DENIED // Insufficient credits. Needed $cost Credits.")
            return
        }

        _playerStats.update { current ->
            val updated = when (itemName) {
                "MEDKIT" -> current.copy(medkits = current.medkits + 1)
                "SHIELD_CELL" -> current.copy(shieldCells = current.shieldCells + 1)
                "WEAPON_UPGRADE" -> current.copy(
                    attack = current.attack + 4,
                    equippedWeapon = "Heuristics Overload Blade"
                )
                "DECK_CHIP" -> current.copy(hackingSkill = current.hackingSkill + 6)
                "KEYCARD" -> current.copy(keycards = current.keycards + 1)
                else -> current
            }
            updated.copy(credits = current.credits - cost)
        }
        
        val displayItem = when (itemName) {
            "MEDKIT" -> "Nanosuture Medkit"
            "SHIELD_CELL" -> "Shield Charger Cell"
            "WEAPON_UPGRADE" -> "Overload Blade Upgrade (+4 ATK)"
            "DECK_CHIP" -> "Cyber-Chip Hacker Up (+6 HACK)"
            "KEYCARD" -> "Gateway Bypass Keycard"
            else -> itemName
        }
        log("PURCHASED // Acquired $displayItem for $cost Credits.")
    }

    // Transitioning Floors Manually / Lift Activation
    fun activateElevator() {
        val state = _worldState.value
        if (state.playerX == state.exitPosition.first && state.playerY == state.exitPosition.second) {
            val nextFloor = state.currentFloor + 1
            log("SEC_PASS // Accessing lift core... Ascending to Sector F-$nextFloor")
            cacheCurrentFloorState()
            loadFloor(nextFloor)
        } else {
            log("USE_ERR // You are not situated on the elevator lift console (rendered as ⛛).")
        }
    }
}
