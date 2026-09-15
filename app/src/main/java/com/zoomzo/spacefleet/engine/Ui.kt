package com.zoomzo.spacefleet.engine

import android.graphics.Paint

/** A tappable rectangular button with cached bounds. Sizes are in device pixels. */
class Button(
    var x: Float = 0f, var y: Float = 0f, var w: Float = 0f, var h: Float = 0f,
    var label: String = "",
    var enabled: Boolean = true,
    var accent: Int = Palette.accent
) {
    fun set(nx: Float, ny: Float, nw: Float, nh: Float): Button {
        x = nx; y = ny; w = nw; h = nh; return this
    }

    fun hit(px: Float, py: Float): Boolean =
        enabled && px >= x && px <= x + w && py >= y && py <= y + h

    fun draw(p: Painter, primary: Boolean = false) {
        val r = p.s(8f)
        val bg = when {
            !enabled -> Palette.bgPanel
            primary -> Palette.withAlpha(accent, 40)
            else -> Palette.bgPanelLight
        }
        p.fillRound(x, y, w, h, r, bg)
        p.strokeRound(x, y, w, h, r, if (enabled) accent else Palette.strokeSoft, p.s(1.5f))
        val tc = if (enabled) Palette.textPrimary else Palette.textMuted
        p.text(label, x + w / 2f, y + h / 2f + p.s(6f), p.s(16f), tc, Paint.Align.CENTER, primary)
    }
}

object Ui {
    /** Panel with border and optional title. */
    fun panel(p: Painter, x: Float, y: Float, w: Float, h: Float, title: String? = null) {
        p.fillRound(x, y, w, h, p.s(10f), Palette.bgPanel)
        p.strokeRound(x, y, w, h, p.s(10f), Palette.strokeSoft, p.s(1.5f))
        if (title != null) {
            p.text(title, x + p.s(14f), y + p.s(24f), p.s(17f), Palette.accent, bold = true)
            p.line(x + p.s(14f), y + p.s(34f), x + w - p.s(14f), y + p.s(34f), Palette.strokeSoft, p.s(1f))
        }
    }

    /** Horizontal stat/progress bar (0..1). */
    fun bar(p: Painter, x: Float, y: Float, w: Float, h: Float, frac: Float, color: Int) {
        val f = frac.coerceIn(0f, 1f)
        p.fillRound(x, y, w, h, h / 2f, Palette.bgPanelLight)
        if (f > 0f) p.fillRound(x, y, w * f, h, h / 2f, color)
    }

    fun toast(p: Painter, msg: String, alpha: Float) {
        if (alpha <= 0f) return
        val a = (alpha.coerceIn(0f, 1f) * 235).toInt()
        val tw = p.textWidth(msg, p.s(16f), true)
        val pad = p.s(18f)
        val bw = tw + pad * 2
        val bh = p.s(40f)
        val x = (p.width - bw) / 2f
        val y = p.height - p.s(90f)
        p.fillRound(x, y, bw, bh, p.s(10f), Palette.withAlpha(Palette.bgPanelLight, a))
        p.strokeRound(x, y, bw, bh, p.s(10f), Palette.withAlpha(Palette.accent, a), p.s(1.5f))
        p.text(msg, p.width / 2f, y + bh / 2f + p.s(6f), p.s(16f),
            Palette.withAlpha(Palette.textPrimary, a), Paint.Align.CENTER, true)
    }
}
