package com.zoomzo.spacefleet.engine

import android.graphics.Color

/** Central color palette so the whole game reads as one visual system. */
object Palette {
    val bgDeep = Color.rgb(9, 12, 28)
    val bgPanel = Color.rgb(18, 24, 48)
    val bgPanelLight = Color.rgb(28, 38, 70)
    val stroke = Color.rgb(60, 80, 130)
    val strokeSoft = Color.rgb(40, 54, 92)

    val textPrimary = Color.rgb(226, 236, 255)
    val textDim = Color.rgb(150, 168, 205)
    val textMuted = Color.rgb(96, 112, 150)

    val accent = Color.rgb(127, 211, 255)      // cyan
    val accentWarm = Color.rgb(255, 209, 102)  // gold
    val good = Color.rgb(120, 224, 143)
    val bad = Color.rgb(255, 107, 107)
    val warn = Color.rgb(255, 176, 74)

    // Faction colors
    val player = Color.rgb(120, 210, 255)
    val ally = Color.rgb(120, 224, 143)
    val enemy = Color.rgb(255, 107, 107)
    val pirate = Color.rgb(216, 130, 255)
    val neutral = Color.rgb(170, 180, 200)

    val hullBar = Color.rgb(120, 224, 143)
    val shieldBar = Color.rgb(127, 190, 255)

    fun withAlpha(color: Int, a: Int): Int =
        Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
}
