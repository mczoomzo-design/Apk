package com.zoomzo.spacefleet.screens

import android.graphics.Paint
import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.model.GameState

/** Shared top status bar used by the hub screens. */
object Hud {
    fun topBar(p: Painter, state: GameState, subtitle: String) {
        val h = p.s(46f)
        p.fillRect(0f, 0f, p.width, h, Palette.bgPanel)
        p.line(0f, h, p.width, h, Palette.strokeSoft, p.s(1.5f))
        p.text("₡ ${state.credits}", p.s(16f), h * 0.62f, p.s(18f), Palette.accentWarm, bold = true)
        p.text("วัน ${state.day}", p.s(150f), h * 0.62f, p.s(15f), Palette.textDim)
        p.text("พลัง ${state.fleetPower()}", p.s(240f), h * 0.62f, p.s(15f), Palette.textDim)
        p.text(subtitle, p.width - p.s(16f), h * 0.62f, p.s(15f),
            Palette.textPrimary, Paint.Align.RIGHT, bold = true)
    }
}
