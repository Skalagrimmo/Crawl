package com.example.model

data class PlayerStats(
    val hp: Int = 100,
    val maxHp: Int = 100,
    val shield: Int = 50,
    val maxShield: Int = 50,
    val credits: Int = 150, // Nuyen / Credits
    val exp: Int = 0,
    val level: Int = 1,
    val attack: Int = 12,
    val defense: Int = 5,
    val hackingSkill: Int = 15,
    val medkits: Int = 2,
    val shieldCells: Int = 2,
    val keycards: Int = 0,
    val equippedWeapon: String = "Mono-Wire Razor",
    val weaponModMultiplier: Float = 1.0f
) {
    val expToNextLevel: Int
        get() = level * 100

    fun addExp(amount: Int): PlayerStats {
        var currentExp = exp + amount
        var newLevel = level
        var newAttack = attack
        var newDefense = defense
        var newHacking = hackingSkill
        var newMaxHp = maxHp
        var newMaxShield = maxShield
        
        while (currentExp >= newLevel * 100) {
            currentExp -= newLevel * 100
            newLevel++
            newMaxHp += 20
            newMaxShield += 15
            newAttack += 4
            newDefense += 2
            newHacking += 5
        }
        
        return copy(
            exp = currentExp,
            level = newLevel,
            maxHp = newMaxHp,
            maxShield = newMaxShield,
            hp = if (newLevel > level) newMaxHp else hp,
            shield = if (newLevel > level) newMaxShield else shield,
            attack = newAttack,
            defense = newDefense,
            hackingSkill = newHacking
        )
    }
}
