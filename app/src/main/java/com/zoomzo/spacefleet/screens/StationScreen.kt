package com.zoomzo.spacefleet.screens

import android.graphics.Paint
import com.zoomzo.spacefleet.engine.Button
import com.zoomzo.spacefleet.engine.Game
import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.engine.Screen
import com.zoomzo.spacefleet.engine.Ui

/** Allied station services hub: repair, shipyard, loadout, technology. */
class StationScreen(game: Game) : Screen(game) {

    private val state get() = game.state
    private val btnShipyard = Button(label = "อู่ต่อยาน")
    private val btnLoadout = Button(label = "ปรับแต่งยาน")
    private val btnTech = Button(label = "เทคโนโลยี")
    private val btnRepair = Button(label = "ซ่อมกองยาน")
    private val btnBack = Button(label = "ออก")

    override fun update(dt: Float) {}

    override fun draw(p: Painter) {
        sync(p)
        p.clear(Palette.bgDeep)
        Hud.topBar(p, state, "สถานี · ${state.currentSystem.name}")

        val pad = s(16f); val top = s(56f)
        val colW = vw * 0.42f
        Ui.panel(p, pad, top, colW, vh - top - pad, "บริการสถานี")
        val bx = pad + s(14f); val bw = colW - s(28f); val bh = s(46f)
        var by = top + s(48f)
        btnShipyard.set(bx, by, bw, bh); btnShipyard.draw(p, primary = true); by += bh + s(10f)
        btnLoadout.set(bx, by, bw, bh); btnLoadout.draw(p); by += bh + s(10f)
        btnTech.set(bx, by, bw, bh); btnTech.label = "เทคโนโลยี (⚙${state.research})"; btnTech.draw(p); by += bh + s(10f)

        val repCost = state.repairFleetCost()
        btnRepair.set(bx, by, bw, bh)
        btnRepair.label = if (repCost <= 0) "กองยานสมบูรณ์" else "ซ่อม (₡$repCost)"
        btnRepair.enabled = repCost > 0 && state.canAfford(repCost)
        btnRepair.draw(p); by += bh + s(16f)

        p.text("กองยาน ${state.fleet.size} ลำ · พลัง ${state.fleetPower()}", bx, by, s(13f), Palette.textDim)

        btnBack.set(bx, vh - pad - bh - s(4f), bw, bh); btnBack.draw(p)

        // Right: fleet roster.
        val rx = pad * 2 + colW; val rw = vw - rx - pad
        Ui.panel(p, rx, top, rw, vh - top - pad, "กองยานของคุณ")
        var yy = top + s(50f)
        for (ship in state.fleet) {
            val flag = ship.id == state.flagshipId
            p.text((if (flag) "★ " else "") + ship.name + "  ·  " + ship.def.cls.displayName,
                rx + s(14f), yy, s(14f), if (flag) Palette.accentWarm else Palette.textPrimary, bold = flag)
            yy += s(18f)
            Ui.bar(p, rx + s(14f), yy - s(10f), rw - s(120f), s(8f), ship.hullFraction, Palette.hullBar)
            p.text("${(ship.hullFraction * 100).toInt()}%  HP${ship.maxHull.toInt()} DPS${ship.dps.toInt()}",
                rx + rw - s(14f), yy - s(3f), s(11f), Palette.textDim, Paint.Align.RIGHT)
            yy += s(22f)
            if (yy > vh - s(40f)) break
        }
    }

    override fun onTap(x: Float, y: Float) {
        if (btnShipyard.hit(x, y)) { game.push(ShipyardScreen(game)); return }
        if (btnLoadout.hit(x, y)) { game.push(LoadoutScreen(game)); return }
        if (btnTech.hit(x, y)) { game.push(TechScreen(game)); return }
        if (btnBack.hit(x, y)) { game.pop(); return }
        if (btnRepair.hit(x, y)) {
            val paid = state.repairFleet()
            if (paid > 0) { game.toast("ซ่อมกองยานแล้ว (₡$paid)"); game.persist() }
            return
        }
    }

    override fun onBack(): Boolean { game.pop(); return true }
}
