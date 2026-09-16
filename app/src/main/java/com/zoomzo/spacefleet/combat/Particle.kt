package com.zoomzo.spacefleet.combat

import com.zoomzo.spacefleet.util.Vec2

enum class ParticleKind { SPARK, DEBRIS, FLASH, SMOKE }

/** A visual-only particle for explosions, thruster puffs and warp flashes. */
class Particle(
    val pos: Vec2,
    val vel: Vec2,
    var life: Float,
    val maxLife: Float,
    var size: Float,
    val color: Int,
    val kind: ParticleKind
) {
    var alive = true
    val fade: Float get() = (life / maxLife).coerceIn(0f, 1f)

    fun update(dt: Float) {
        pos.addScaled(vel, dt)
        when (kind) {
            ParticleKind.SMOKE -> { vel.scale(0.94f); size += 40f * dt }
            ParticleKind.DEBRIS -> vel.scale(0.97f)
            ParticleKind.SPARK -> vel.scale(0.9f)
            ParticleKind.FLASH -> size += 120f * dt
        }
        life -= dt
        if (life <= 0f) alive = false
    }
}
