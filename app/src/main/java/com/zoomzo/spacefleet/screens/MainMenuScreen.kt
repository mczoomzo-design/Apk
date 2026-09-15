package com.zoomzo.spacefleet.screens

import android.graphics.Paint
import com.zoomzo.spacefleet.engine.Button
import com.zoomzo.spacefleet.engine.Game
import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.engine.Screen
import kotlin.random.Random

/** Title screen: New Game / Continue, with a drifting starfield. */
class MainMenuScreen(game: Game) : Screen(game) {

    private val newGame = Button(label = "เริ่มเกมใหม่")
    private val continueGame = Button(label = "เล่นต่อ")
    private val stars = FloatArray(180 * 3)
    private var t = 0f

    override fun onEnter() {
        val rng = Random(42)
        var i = 0
        while (i < stars.size) {
            stars[i] = rng.nextFloat()       // x fraction
            stars[i + 1] = rng.nextFloat()   // y fraction
            stars[i + 2] = 0.4f + rng.nextFloat() // speed/size factor
            i += 3
        }
        continueGame.enabled = game.hasSave()
    }

    override fun update(dt: Float) { t += dt }

    override fun draw(p: Painter) {
        p.clear(Palette.bgDeep)
        // starfield
        var i = 0
        while (i < stars.size) {
            val sx = ((stars[i] + t * 0.01f * stars[i + 2]) % 1f) * p.width
            val sy = stars[i + 1] * p.height
            val r = p.s(stars[i + 2])
            p.circle(sx, sy, r, Palette.withAlpha(Palette.textPrimary, (stars[i + 2] * 120).toInt()))
            i += 3
        }

        val cx = p.width / 2f
        p.text("SPACE FLEET", cx, p.height * 0.30f, p.s(46f), Palette.accent, Paint.Align.CENTER, true)
        p.text("COMMANDER", cx, p.height * 0.30f + p.s(46f), p.s(46f), Palette.accentWarm, Paint.Align.CENTER, true)
        p.text("ผู้บัญชาการกองยานอวกาศ", cx, p.height * 0.30f + p.s(84f), p.s(16f),
            Palette.textDim, Paint.Align.CENTER)

        val bw = p.s(260f); val bh = p.s(54f)
        val bx = cx - bw / 2f
        newGame.set(bx, p.height * 0.56f, bw, bh)
        continueGame.set(bx, p.height * 0.56f + bh + p.s(16f), bw, bh)
        newGame.draw(p, primary = true)
        continueGame.draw(p, primary = false)

        p.text("v1.0 · แตะเพื่อบัญชาการ", cx, p.height - p.s(24f), p.s(13f),
            Palette.textMuted, Paint.Align.CENTER)
    }

    override fun onTap(x: Float, y: Float) {
        if (newGame.hit(x, y)) {
            game.startNewGame()
            game.setRoot(GalaxyMapScreen(game))
        } else if (continueGame.hit(x, y)) {
            game.loadOrNew()
            game.setRoot(GalaxyMapScreen(game))
        }
    }
}
