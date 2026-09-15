package com.zoomzo.spacefleet.screens

import android.graphics.Paint
import com.zoomzo.spacefleet.engine.Button
import com.zoomzo.spacefleet.engine.Game
import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.engine.Screen
import com.zoomzo.spacefleet.engine.Ui
import com.zoomzo.spacefleet.model.Formation
import com.zoomzo.spacefleet.model.TechType

/** Spend research points on global fleet technology, and pick a battle formation. */
class TechScreen(game: Game) : Screen(game) {

    private val state get() = game.state
    private val btnBack = Button(label = "ออก")
    private val techButtons = mutableListOf<Pair<TechType, Button>>()
    private val formButtons = mutableListOf<Pair<Formation, Button>>()

    override fun update(dt: Float) {}

    override fun draw(p: Painter) {
        sync(p)
        p.clear(Palette.bgDeep)
        Hud.topBar(p, state, "เทคโนโลยี & รูปแบบกองยาน")
        btnBack.set(vw - s(84f), s(8f), s(74f), s(32f)); btnBack.draw(p)

        val pad = s(14f); val top = s(56f)
        val leftW = vw * 0.60f
        Ui.panel(p, pad, top, leftW, vh - top - pad, "อัปเกรดเทคโนโลยี (⚙ ${state.research})")

        techButtons.clear()
        var y = top + s(48f)
        val cardH = s(64f)
        for (tech in TechType.values()) {
            val x = pad + s(12f); val w = leftW - s(24f)
            p.fillRound(x, y, w, cardH, s(8f), Palette.bgPanelLight)
            p.strokeRound(x, y, w, cardH, s(8f), Palette.strokeSoft, s(1f))
            val lvl = state.techLevel(tech)
            p.text("${tech.displayName}  [${lvl}/${tech.maxLevel}]", x + s(12f), y + s(22f),
                s(15f), Palette.accent, bold = true)
            p.text(tech.desc, x + s(12f), y + s(42f), s(11.5f), Palette.textDim)
            // level pips
            for (i in 0 until tech.maxLevel) {
                val px = x + w - s(150f) + i * s(12f)
                p.circle(px, y + s(52f), s(4f), if (i < lvl) Palette.good else Palette.strokeSoft)
            }
            val maxed = lvl >= tech.maxLevel
            val cost = state.techCost(tech)
            val b = Button(x + w - s(110f), y + s(10f), s(98f), s(30f),
                if (maxed) "สูงสุด" else "⚙$cost")
            b.enabled = state.canUpgradeTech(tech)
            b.draw(p, primary = b.enabled)
            techButtons.add(tech to b)
            y += cardH + s(10f)
        }

        // Formation panel
        val rx = pad * 2 + leftW; val rw = vw - rx - pad
        Ui.panel(p, rx, top, rw, vh - top - pad, "รูปแบบกองยาน")
        formButtons.clear()
        var fy = top + s(50f)
        p.text("เลือกการจัดวางก่อนรบ:", rx + s(14f), fy, s(12.5f), Palette.textDim); fy += s(14f)
        for (f in Formation.values()) {
            val b = Button(rx + s(12f), fy, rw - s(24f), s(44f), f.displayName)
            b.draw(p, primary = f == state.formation)
            formButtons.add(f to b)
            fy += s(52f)
        }
        p.text("รูปแบบมีผลต่อตำแหน่งเริ่มต้น", rx + s(14f), fy + s(6f), s(11.5f), Palette.textMuted)
        p.text("ของกองยานในสนามรบ", rx + s(14f), fy + s(22f), s(11.5f), Palette.textMuted)
    }

    override fun onTap(x: Float, y: Float) {
        if (btnBack.hit(x, y)) { game.pop(); return }
        for ((tech, b) in techButtons) if (b.hit(x, y)) {
            if (state.buyTech(tech)) { game.toast("อัปเกรด ${tech.displayName}!"); game.persist() }
            else game.toast("วิจัยไม่พอ")
            return
        }
        for ((f, b) in formButtons) if (b.hit(x, y)) {
            state.formation = f; game.toast("รูปแบบ: ${f.displayName}"); game.persist(); return
        }
    }

    override fun onBack(): Boolean { game.pop(); return true }
}
