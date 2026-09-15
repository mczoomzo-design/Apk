package com.zoomzo.spacefleet.combat

import com.zoomzo.spacefleet.model.Ship
import com.zoomzo.spacefleet.model.ShipClass
import com.zoomzo.spacefleet.model.WeaponDef
import com.zoomzo.spacefleet.util.Vec2

/** One weapon mount with its own cooldown timer and tech multipliers. */
class WeaponMount(val def: WeaponDef) {
    var cooldown = 0f
    var dmgMult = 1f
    var rateMult = 1f
    val interval: Float get() = 1f / (def.fireRate * rateMult)
}

/**
 * A ship (or station) participating in a battle. Wraps either a player-owned
 * [source] Ship (so damage can be written back) or a spawned enemy hull.
 */
class CombatShip(
    var team: Int,               // 0 = player side, 1 = hostile (flips to 0 on capture)
    val defId: String,
    val cls: ShipClass,
    val displayName: String,
    val color: Int,
    val source: Ship?            // non-null for the player's real ships
) {
    val pos = Vec2()
    val vel = Vec2()
    var angle = 0f

    var maxHull = 1f
    var hull = 1f
    var maxShield = 0f
    var shield = 0f
    var shieldRegen = 0f
    var speed = 100f
    var turnRate = 2f
    var size = 20f
    var repairRate = 0f
    var sensorRange = 700f

    val mounts = mutableListOf<WeaponMount>()

    var fighterCapacity = 0
    var troopCapacity = 0
    var activeFighters = 0
    var fighterLaunchCd = 0f

    var isStation = false
    var stationTroops = 0

    // Orders (player) / AI intent
    var moveGoal: Vec2? = null       // explicit move order
    var attackTarget: CombatShip? = null
    var selected = false
    var shieldFlash = 0f
    var hullFlash = 0f

    var alive = true

    val hullFraction: Float get() = if (maxHull <= 0f) 0f else (hull / maxHull).coerceIn(0f, 1f)
    val shieldFraction: Float get() = if (maxShield <= 0f) 0f else (shield / maxShield).coerceIn(0f, 1f)

    /** Longest weapon range on this ship (for engagement positioning). */
    val maxRange: Float get() = mounts.maxOfOrNull { it.def.range } ?: 0f
    val minEngageRange: Float get() = mounts.minOfOrNull { it.def.range } ?: 300f

    fun takeDamage(dmg: Float, shieldFactor: Float, armorFactor: Float) {
        var remaining = dmg
        if (shield > 0f) {
            val toShield = dmg * shieldFactor
            if (toShield <= shield) { shield -= toShield; shieldFlash = 0.15f; return }
            remaining = (toShield - shield) / shieldFactor
            shield = 0f
            shieldFlash = 0.15f
        }
        hull -= remaining * armorFactor
        hullFlash = 0.12f
        if (hull <= 0f) { hull = 0f; alive = false }
    }

    /** Write combat result back onto the owning player Ship. */
    fun commitResult() {
        val src = source ?: return
        if (!alive) {
            src.hullDamage = src.maxHull // fully destroyed; caller removes it
        } else {
            src.hullDamage = (src.maxHull - hull).coerceAtLeast(0f)
        }
    }
}
