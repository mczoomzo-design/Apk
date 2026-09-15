package com.zoomzo.spacefleet.combat

import com.zoomzo.spacefleet.util.Vec2

/** A small strike craft launched from a carrier. Cheap, fast, expendable. */
class Fighter(
    val team: Int,
    val color: Int,
    val parent: CombatShip
) {
    val pos = Vec2()
    val vel = Vec2()
    var angle = 0f
    var hull = 22f
    val maxHull = 22f
    val speed = 340f
    val size = 7f
    var fireCd = 0f
    var target: CombatShip? = null
    var alive = true
    var lifetime = 0f
}
