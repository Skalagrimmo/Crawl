package com.example.model

enum class CombatTurn {
    PLAYER, ENEMY
}

data class CombatState(
    val inCombat: Boolean = false,
    val activeEnemy: Enemy? = null,
    val turn: CombatTurn = CombatTurn.PLAYER,
    val logs: List<String> = emptyList()
)
