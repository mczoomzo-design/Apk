package com.zoomzo.spacefleet.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Simple mutable 2D vector used across the engine and combat sim. */
class Vec2(var x: Float = 0f, var y: Float = 0f) {

    fun set(nx: Float, ny: Float): Vec2 { x = nx; y = ny; return this }
    fun set(o: Vec2): Vec2 { x = o.x; y = o.y; return this }

    fun add(o: Vec2): Vec2 { x += o.x; y += o.y; return this }
    fun addScaled(o: Vec2, s: Float): Vec2 { x += o.x * s; y += o.y * s; return this }
    fun sub(o: Vec2): Vec2 { x -= o.x; y -= o.y; return this }
    fun scale(s: Float): Vec2 { x *= s; y *= s; return this }

    fun length(): Float = hypot(x, y)
    fun lengthSq(): Float = x * x + y * y

    fun normalize(): Vec2 {
        val l = length()
        if (l > 1e-5f) { x /= l; y /= l }
        return this
    }

    fun angle(): Float = atan2(y, x)

    fun copy(): Vec2 = Vec2(x, y)

    companion object {
        fun fromAngle(a: Float, len: Float = 1f) = Vec2(cos(a) * len, sin(a) * len)

        fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float = hypot(ax - bx, ay - by)

        fun distSq(ax: Float, ay: Float, bx: Float, by: Float): Float {
            val dx = ax - bx; val dy = ay - by; return dx * dx + dy * dy
        }
    }
}
