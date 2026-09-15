package com.zoomzo.spacefleet.util

import kotlin.math.PI
import kotlin.math.abs

object MathUtil {
    const val TAU = (PI * 2.0).toFloat()

    /** Wrap an angle into (-PI, PI]. */
    fun wrapAngle(a: Float): Float {
        var x = a
        while (x <= -PI) x += TAU
        while (x > PI) x -= TAU
        return x
    }

    /** Shortest signed angular difference from -> to. */
    fun angleDiff(from: Float, to: Float): Float = wrapAngle(to - from)

    /** Rotate [from] toward [to] by at most [maxStep] radians. */
    fun turnToward(from: Float, to: Float, maxStep: Float): Float {
        val d = angleDiff(from, to)
        if (abs(d) <= maxStep) return to
        return wrapAngle(from + if (d > 0) maxStep else -maxStep)
    }

    fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v
    fun clampInt(v: Int, lo: Int, hi: Int): Int = if (v < lo) lo else if (v > hi) hi else v
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
