package com.zoomzo.spacefleet.combat

import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.model.ShipClass
import kotlin.math.cos
import kotlin.math.sin

/** Draws distinct, layered ship silhouettes (Harbinger-flavored) procedurally. */
object ShipArt {

    // Local silhouettes: forward = +x, pairs of (x,y) roughly within [-1.5,1.7].
    private val SCOUT = floatArrayOf(1.5f,0f, -0.5f,0.5f, -0.9f,0f, -0.5f,-0.5f)
    private val INTERCEPTOR = floatArrayOf(1.7f,0f, 0.2f,0.32f, -0.5f,0.95f, -0.9f,0.28f,
        -0.9f,-0.28f, -0.5f,-0.95f, 0.2f,-0.32f)
    private val FRIGATE = floatArrayOf(1.6f,0f, 0.6f,0.5f, -0.9f,0.5f, -1.2f,0f, -0.9f,-0.5f, 0.6f,-0.5f)
    private val DESTROYER = floatArrayOf(1.6f,0f, 1.0f,0.5f, -1.0f,0.68f, -1.25f,0.3f,
        -1.25f,-0.3f, -1.0f,-0.68f, 1.0f,-0.5f)
    private val BATTLESHIP = floatArrayOf(1.8f,0f, 0.8f,0.5f, -1.1f,0.72f, -1.35f,0f,
        -1.1f,-0.72f, 0.8f,-0.5f)
    private val CARRIER = floatArrayOf(1.2f,0.72f, 1.5f,0.5f, 1.5f,-0.5f, 1.2f,-0.72f,
        -1.25f,-0.8f, -1.45f,0f, -1.25f,0.8f)
    private val TRANSPORT = floatArrayOf(1.1f,0.5f, 1.35f,0f, 1.1f,-0.5f, -1.1f,-0.62f, -1.1f,0.62f)

    private fun shape(cls: ShipClass): FloatArray = when (cls) {
        ShipClass.SCOUT -> SCOUT
        ShipClass.INTERCEPTOR -> INTERCEPTOR
        ShipClass.FRIGATE -> FRIGATE
        ShipClass.DESTROYER -> DESTROYER
        ShipClass.BATTLESHIP -> BATTLESHIP
        ShipClass.CARRIER -> CARRIER
        ShipClass.TRANSPORT -> TRANSPORT
    }

    private val buf = FloatArray(32)

    fun draw(p: Painter, cls: ShipClass, x: Float, y: Float, angle: Float, r: Float,
             base: Int, engineColor: Int, t: Float) {
        val ca = cos(angle); val sa = sin(angle)
        val local = shape(cls)
        val n = local.size / 2
        for (i in 0 until n) {
            val lx = local[i * 2]; val ly = local[i * 2 + 1]
            buf[i * 2] = x + (lx * ca - ly * sa) * r
            buf[i * 2 + 1] = y + (lx * sa + ly * ca) * r
        }
        val pts = buf.copyOf(n * 2)

        // Engine glow at the tail.
        val pulse = 0.75f + 0.25f * sin(t * 12f + x * 0.05f)
        val tailX = x - ca * r * 1.15f; val tailY = y - sa * r * 1.15f
        p.glow(tailX, tailY, r * 0.55f * pulse, engineColor, 70)

        // Hull with outline and highlight.
        p.polygon(pts, Palette.darken(base, 0.55f))
        p.polygonOutline(pts, Palette.lighten(base, 0.25f), (r * 0.12f).coerceAtLeast(1.2f))

        // Spine accent + cockpit.
        val spineX1 = x - ca * r * 0.8f; val spineY1 = y - sa * r * 0.8f
        val spineX2 = x + ca * r * 1.1f; val spineY2 = y + sa * r * 1.1f
        p.line(spineX1, spineY1, spineX2, spineY2, base, (r * 0.14f).coerceAtLeast(1f))
        val cockX = x + ca * r * 0.55f; val cockY = y + sa * r * 0.55f
        p.circle(cockX, cockY, (r * 0.22f).coerceAtLeast(1.5f), Palette.lighten(base, 0.55f))

        // Class extras.
        if (cls == ShipClass.CARRIER) {
            // deck stripes
            for (o in -1..1) {
                val ox = -sa * r * 0.4f * o; val oy = ca * r * 0.4f * o
                p.line(x - ca * r * 0.9f + ox, y - sa * r * 0.9f + oy,
                    x + ca * r * 0.9f + ox, y + sa * r * 0.9f + oy,
                    Palette.withAlpha(Palette.lighten(base, 0.3f), 120), r * 0.05f)
            }
        }
        if (cls == ShipClass.BATTLESHIP) {
            // side turrets
            for (s in intArrayOf(-1, 1)) {
                val tx = x - ca * r * 0.2f - sa * r * 0.55f * s
                val ty = y - sa * r * 0.2f + ca * r * 0.55f * s
                p.circle(tx, ty, r * 0.18f, Palette.darken(base, 0.2f))
            }
        }
    }
}
