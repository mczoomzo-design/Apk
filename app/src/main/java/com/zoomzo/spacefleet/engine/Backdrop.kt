package com.zoomzo.spacefleet.engine

import kotlin.random.Random

/** Deterministic nebula + parallax starfield shared by every screen. */
class Backdrop(seed: Long = 1337L) {
    // stars: fraction x, fraction y, size factor, twinkle phase
    private val stars = FloatArray(220 * 4)
    // nebula blobs: fraction x, y, radius factor, colorIndex
    private val blobs = FloatArray(7 * 4)

    init {
        val rng = Random(seed)
        var i = 0
        while (i < stars.size) {
            stars[i] = rng.nextFloat()
            stars[i + 1] = rng.nextFloat()
            stars[i + 2] = 0.3f + rng.nextFloat() * rng.nextFloat() * 2.4f
            stars[i + 3] = rng.nextFloat() * 6.28f
            i += 4
        }
        var j = 0
        while (j < blobs.size) {
            blobs[j] = rng.nextFloat()
            blobs[j + 1] = rng.nextFloat()
            blobs[j + 2] = 0.3f + rng.nextFloat() * 0.5f
            blobs[j + 3] = rng.nextInt(3).toFloat()
            j += 4
        }
    }

    /** Draw the full backdrop. [scroll] pans the starfield horizontally (world feel). */
    fun draw(p: Painter, t: Float, scroll: Float = 0f) {
        p.clear(Palette.bgDeep)
        // nebula clouds
        var j = 0
        while (j < blobs.size) {
            val bx = blobs[j] * p.width
            val by = blobs[j + 1] * p.height
            val r = blobs[j + 2] * p.width * 0.6f
            val color = when (blobs[j + 3].toInt()) {
                0 -> Palette.nebulaA; 1 -> Palette.nebulaB; else -> Palette.nebulaC
            }
            p.glow(bx, by, r, color, 16)
            j += 4
        }
        // stars (3 depth layers via size)
        var i = 0
        while (i < stars.size) {
            val depth = stars[i + 2]
            val sx = ((stars[i] * p.width - scroll * depth * 0.05f) % p.width + p.width) % p.width
            val sy = stars[i + 1] * p.height
            val tw = 0.6f + 0.4f * kotlin.math.sin(t * 1.5f + stars[i + 3])
            val a = (60 + depth * 70f * tw).toInt().coerceIn(30, 255)
            p.circle(sx, sy, p.s(depth * 0.7f), Palette.withAlpha(Palette.textPrimary, a))
            i += 4
        }
    }
}
