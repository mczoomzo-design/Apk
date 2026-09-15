package com.zoomzo.spacefleet.screens

import android.graphics.Paint
import com.zoomzo.spacefleet.engine.Button
import com.zoomzo.spacefleet.engine.Game
import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.engine.Screen
import com.zoomzo.spacefleet.engine.Ui
import com.zoomzo.spacefleet.model.Mission
import com.zoomzo.spacefleet.model.MissionStatus

/** Services hub at an allied station: repair, shipyard, loadout, and contracts. */
class StationScreen(game: Game) : Screen(game) {

    private val state get() = game.state
    private val btnShipyard = Button(label = "อู่ต่อยาน")
    private val btnLoadout = Button(label = "ปรับแต่งยาน")
    private val btnRepair = Button(label = "ซ่อมกองยาน")
    private val btnBack = Button(label = "ออก")
    private val missionButtons = mutableListOf<Pair<Mission, Button>>()

    override fun onEnter() { state.refreshMissions() }

    override fun update(dt: Float) {}

    override fun draw(p: Painter) {
        sync(p)
        p.clear(Palette.bgDeep)
        Hud.topBar(p, state, "สถานี · ${state.currentSystem.name}")

        val pad = s(16f)
        val top = s(56f)

        // Left: services
        val colW = vw * 0.42f
        Ui.panel(p, pad, top, colW, vh - top - pad, "บริการสถานี")
        val bx = pad + s(14f); val bw = colW - s(28f); val bh = s(48f)
        var by = top + s(50f)
        btnShipyard.set(bx, by, bw, bh); btnShipyard.draw(p, primary = true); by += bh + s(12f)
        btnLoadout.set(bx, by, bw, bh); btnLoadout.draw(p); by += bh + s(12f)

        val repCost = state.repairFleetCost()
        btnRepair.set(bx, by, bw, bh)
        btnRepair.label = if (repCost <= 0) "กองยานสมบูรณ์" else "ซ่อมกองยาน (₡$repCost)"
        btnRepair.enabled = repCost > 0 && state.canAfford(repCost)
        btnRepair.draw(p); by += bh + s(20f)

        p.text("กองยาน: ${state.fleet.size} ลำ", bx, by, s(14f), Palette.textDim); by += s(22f)
        p.text("พลังรวม: ${state.fleetPower()}", bx, by, s(14f), Palette.textDim); by += s(22f)
        p.text("ชื่อเสียงพันธมิตร: ${state.reputation[com.zoomzo.spacefleet.model.Faction.ALLY] ?: 0}",
            bx, by, s(14f), Palette.ally)

        btnBack.set(bx, vh - pad - bh - s(6f), bw, bh); btnBack.draw(p)

        // Right: contracts
        val rx = pad * 2 + colW
        val rw = vw - rx - pad
        Ui.panel(p, rx, top, rw, vh - top - pad, "ประกาศภารกิจ")
        drawMissions(p, rx, top, rw)
    }

    private fun drawMissions(p: Painter, rx: Float, top: Float, rw: Float) {
        missionButtons.clear()
        val offered = state.missions.filter {
            it.giverSystemId == state.currentSystem.id && it.status == MissionStatus.OFFERED
        }
        var yy = top + s(52f)
        if (offered.isEmpty())
            p.text("ยังไม่มีประกาศ ณ ขณะนี้", rx + s(16f), yy, s(14f), Palette.textDim)
        for (m in offered) {
            val cardH = s(78f)
            val cx = rx + s(12f); val cw = rw - s(24f)
            p.fillRound(cx, yy, cw, cardH, s(8f), Palette.bgPanelLight)
            p.strokeRound(cx, yy, cw, cardH, s(8f), Palette.strokeSoft, s(1f))
            p.text(m.title, cx + s(12f), yy + s(22f), s(15f), Palette.textPrimary, bold = true)
            p.text(m.desc, cx + s(12f), yy + s(42f), s(11.5f), Palette.textDim)
            p.text("₡${m.rewardCredits} · +${m.rewardRep} ชื่อเสียง", cx + s(12f), yy + s(62f),
                s(12f), Palette.accentWarm)
            val ab = Button(cx + cw - s(96f), yy + cardH - s(38f), s(84f), s(30f), "รับภารกิจ")
            ab.draw(p, primary = true)
            missionButtons.add(m to ab)
            yy += cardH + s(12f)
            if (yy > vh - s(40f)) break
        }
        val active = state.activeMissions.size
        p.text("กำลังทำอยู่: $active ภารกิจ", rx + rw - s(16f), vh - s(28f), s(12f),
            Palette.textMuted, Paint.Align.RIGHT)
    }

    override fun onTap(x: Float, y: Float) {
        if (btnShipyard.hit(x, y)) { game.push(ShipyardScreen(game)); return }
        if (btnLoadout.hit(x, y)) { game.push(LoadoutScreen(game)); return }
        if (btnBack.hit(x, y)) { game.pop(); return }
        if (btnRepair.hit(x, y)) {
            val paid = state.repairFleet()
            if (paid > 0) { game.toast("ซ่อมกองยานแล้ว (₡$paid)"); game.persist() }
            return
        }
        for ((m, b) in missionButtons) {
            if (b.hit(x, y)) {
                state.acceptMission(m)
                game.toast("รับภารกิจ: ${m.title}")
                game.persist()
                return
            }
        }
    }

    override fun onBack(): Boolean { game.pop(); return true }
}
