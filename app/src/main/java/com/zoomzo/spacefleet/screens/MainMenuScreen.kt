package com.zoomzo.spacefleet.screens

import android.graphics.Paint
import com.zoomzo.spacefleet.engine.Backdrop
import com.zoomzo.spacefleet.engine.Button
import com.zoomzo.spacefleet.engine.Game
import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.engine.Screen
import com.zoomzo.spacefleet.engine.Ui
import com.zoomzo.spacefleet.model.Difficulty

/** Title screen with nebula backdrop, difficulty selection and New/Continue. */
class MainMenuScreen(game: Game) : Screen(game) {

    private val backdrop = Backdrop(2024L)
    private var t = 0f
    private var difficulty = Difficulty.NORMAL

    private val newGame = Button(label = "เริ่มภารกิจใหม่")
    private val continueGame = Button(label = "เล่นต่อ")
    private val diffButtons = Array(Difficulty.values().size) { Button() }

    override fun onEnter() { continueGame.enabled = game.hasSave() }
    override fun update(dt: Float) { t += dt }

    override fun draw(p: Painter) {
        sync(p)
        backdrop.draw(p, t, t * 6f)

        val cx = p.width / 2f
        p.glow(cx, p.height * 0.24f, s(160f), Palette.accent, 22)
        p.text("SPACE FLEET", cx, p.height * 0.22f, s(46f), Palette.accent, Paint.Align.CENTER, true)
        p.text("COMMANDER", cx, p.height * 0.22f + s(44f), s(44f), Palette.accentWarm, Paint.Align.CENTER, true)
        p.text("ผู้บัญชาการกองยานอวกาศ · แนว Harbinger", cx, p.height * 0.22f + s(78f), s(14f),
            Palette.textDim, Paint.Align.CENTER)

        // Difficulty selector
        p.text("เลือกระดับความยาก", cx, p.height * 0.46f, s(14f), Palette.textDim, Paint.Align.CENTER, true)
        val dw = s(150f); val dh = s(46f); val gap = s(10f)
        val totalW = dw * Difficulty.values().size + gap * (Difficulty.values().size - 1)
        var dx = cx - totalW / 2f
        val dy = p.height * 0.46f + s(14f)
        Difficulty.values().forEachIndexed { i, d ->
            val b = diffButtons[i].set(dx, dy, dw, dh)
            b.label = d.displayName
            b.accent = if (d == difficulty) Palette.accentWarm else Palette.accent
            b.draw(p, primary = d == difficulty)
            dx += dw + gap
        }
        p.text(difficulty.subtitle, cx, dy + dh + s(24f), s(13f), Palette.textPrimary, Paint.Align.CENTER)

        // Main buttons
        val bw = s(280f); val bh = s(52f)
        val bx = cx - bw / 2f
        newGame.set(bx, p.height * 0.70f, bw, bh)
        continueGame.set(bx, p.height * 0.70f + bh + s(14f), bw, bh)
        newGame.draw(p, primary = true)
        continueGame.draw(p, primary = false)

        p.text("v1.2 · แตะเพื่อบัญชาการ", cx, p.height - s(20f), s(12f), Palette.textMuted, Paint.Align.CENTER)
    }

    override fun onTap(x: Float, y: Float) {
        diffButtons.forEachIndexed { i, b ->
            if (b.hit(x, y)) { difficulty = Difficulty.values()[i]; return }
        }
        if (newGame.hit(x, y)) {
            game.startNewGame(difficulty)
            game.setRoot(GalaxyMapScreen(game))
        } else if (continueGame.hit(x, y)) {
            game.loadOrNew()
            game.setRoot(GalaxyMapScreen(game))
        }
    }
}
