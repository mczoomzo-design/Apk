package com.zoomzo.spacefleet.combat

import com.zoomzo.spacefleet.model.WeaponType
import com.zoomzo.spacefleet.util.Vec2

/** A live shot in the combat world. Missiles home; everything else is dumb-fire. */
class Projectile(
    val team: Int,
    val pos: Vec2,
    val vel: Vec2,
    var damage: Float,
    val range: Float,
    val speed: Float,
    val type: WeaponType,
    val shieldFactor: Float,
    val armorFactor: Float,
    var target: CombatShip?,       // homing target for missiles
    val tracking: Float,           // rad/s
    val color: Int
) {
    var traveled = 0f
    var alive = true
    val radius: Float = when (type) {
        WeaponType.MISSILE -> 5f
        WeaponType.RAILGUN -> 3f
        WeaponType.PLASMA -> 5f
        else -> 2.5f
    }
    /** Missiles can be shot down by point defense. */
    val interceptable: Boolean get() = type == WeaponType.MISSILE
    var hp: Float = if (type == WeaponType.MISSILE) 6f else 1f
}
