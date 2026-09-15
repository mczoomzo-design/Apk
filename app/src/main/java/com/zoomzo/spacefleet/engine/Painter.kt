package com.zoomzo.spacefleet.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Thin wrapper over Canvas with reusable Paint objects and convenience helpers.
 * A single Painter instance is reused every frame (no per-frame allocation).
 */
class Painter {
    lateinit var canvas: Canvas
    var width = 0f
    var height = 0f
    /** Density scale (1f == mdpi). UI sizes are multiplied by this. */
    var scale = 1f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val rect = RectF()
    private val path = Path()

    fun begin(c: Canvas, w: Float, h: Float, densityScale: Float) {
        canvas = c; width = w; height = h; scale = densityScale
    }

    /** Scale a design unit (authored at mdpi) to device pixels. */
    fun s(v: Float): Float = v * scale
    fun s(v: Int): Float = v * scale

    fun clear(color: Int) = canvas.drawColor(color)

    fun fillRect(x: Float, y: Float, w: Float, h: Float, color: Int) {
        fill.color = color
        canvas.drawRect(x, y, x + w, y + h, fill)
    }

    fun fillRound(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        fill.color = color
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, r, r, fill)
    }

    fun strokeRound(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int, lw: Float) {
        strokePaint.color = color; strokePaint.strokeWidth = lw
        rect.set(x, y, x + w, y + h)
        canvas.drawRoundRect(rect, r, r, strokePaint)
    }

    fun circle(cx: Float, cy: Float, radius: Float, color: Int) {
        fill.color = color
        canvas.drawCircle(cx, cy, radius, fill)
    }

    fun ringStroke(cx: Float, cy: Float, radius: Float, color: Int, lw: Float) {
        strokePaint.color = color; strokePaint.strokeWidth = lw
        canvas.drawCircle(cx, cy, radius, strokePaint)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, lw: Float) {
        strokePaint.color = color; strokePaint.strokeWidth = lw
        canvas.drawLine(x1, y1, x2, y2, strokePaint)
    }

    /** Draw a dashed-ish connection by segments (cheap). */
    fun dottedLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, lw: Float, seg: Float) {
        strokePaint.color = color; strokePaint.strokeWidth = lw
        val dx = x2 - x1; val dy = y2 - y1
        val len = kotlin.math.hypot(dx, dy)
        if (len < 1f) return
        val steps = (len / (seg * 2f)).toInt().coerceAtLeast(1)
        val ux = dx / len; val uy = dy / len
        var d = 0f
        for (i in 0 until steps) {
            val sx = x1 + ux * d; val sy = y1 + uy * d
            val ex = x1 + ux * (d + seg); val ey = y1 + uy * (d + seg)
            canvas.drawLine(sx, sy, ex, ey, strokePaint)
            d += seg * 2f
        }
    }

    fun text(str: String, x: Float, y: Float, sizePx: Float, color: Int,
             align: Paint.Align = Paint.Align.LEFT, bold: Boolean = false) {
        textPaint.textSize = sizePx
        textPaint.color = color
        textPaint.textAlign = align
        textPaint.isFakeBoldText = bold
        canvas.drawText(str, x, y, textPaint)
    }

    fun textWidth(str: String, sizePx: Float, bold: Boolean = false): Float {
        textPaint.textSize = sizePx; textPaint.isFakeBoldText = bold
        return textPaint.measureText(str)
    }

    /** Draw a filled triangle (used for ships / markers). */
    fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
        fill.color = color
        path.reset()
        path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        canvas.drawPath(path, fill)
    }

    fun save() = canvas.save()
    fun restore() = canvas.restore()
    fun translate(dx: Float, dy: Float) = canvas.translate(dx, dy)
    fun scaleCanvas(sx: Float, sy: Float) = canvas.scale(sx, sy)
    fun rotate(deg: Float, px: Float, py: Float) = canvas.rotate(deg, px, py)
    fun clipRect(x: Float, y: Float, w: Float, h: Float) = canvas.clipRect(x, y, x + w, y + h)
}
