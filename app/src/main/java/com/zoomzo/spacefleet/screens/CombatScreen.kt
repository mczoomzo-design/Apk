package com.zoomzo.spacefleet.screens

import android.graphics.Paint
import com.zoomzo.spacefleet.combat.BattleResult
import com.zoomzo.spacefleet.combat.CombatShip
import com.zoomzo.spacefleet.combat.CombatWorld
import com.zoomzo.spacefleet.engine.Button
import com.zoomzo.spacefleet.engine.Camera
import com.zoomzo.spacefleet.engine.Game
import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.engine.Screen
import com.zoomzo.spacefleet.model.Faction
import com.zoomzo.spacefleet.model.StarSystem
import kotlin.math.cos
import kotlin.math.sin

/** Top-down RTS battle. Tap a ship to select, tap to move/attack; pinch to zoom. */
class CombatScreen(
    game: Game,
    private val system: StarSystem,
    private val invasion: Boolean
) : Screen(game) {

    private val state get() = game.state
    private val world = CombatWorld(state.fleet, system, invasion)
    private val cam = Camera()
    private var camInit = false

    private var ended = false
    private var outcome = BattleResult.RUNNING
    private var resolved = false

    private val btnAll = Button(label = "เลือกทั้งหมด")
    private val btnAuto = Button(label = "ออโต้")
    private val btnRetreat = Button(label = "ล่าถอย", accent = Palette.warn)
    private val btnContinue = Button(label = "ดำเนินการต่อ")

    override fun onEnter() {
        cam.zoom = 0.55f
    }

    override fun update(dt: Float) {
        if (ended) return
        world.update(dt)
        val r = world.result()
        if (r != BattleResult.RUNNING) { ended = true; outcome = r }
    }

    private fun initCam() {
        cam.setViewport(vw, vh)
        val players = world.ships.filter { it.team == 0 }
        if (players.isNotEmpty()) {
            cam.cx = players.map { it.pos.x }.average().toFloat()
            cam.cy = players.map { it.pos.y }.average().toFloat()
        } else { cam.cx = world.worldW / 2f; cam.cy = world.worldH / 2f }
        camInit = true
    }

    override fun draw(p: Painter) {
        sync(p)
        if (!camInit) initCam()
        cam.setViewport(vw, vh)
        p.clear(Palette.bgDeep)
        drawGrid(p)

        for (t in world.tracers)
            p.line(cam.worldToScreenX(t.x1), cam.worldToScreenY(t.y1),
                cam.worldToScreenX(t.x2), cam.worldToScreenY(t.y2), t.color, s(2f))

        for (proj in world.projectiles) {
            val x = cam.worldToScreenX(proj.pos.x); val y = cam.worldToScreenY(proj.pos.y)
            if (x < -20 || x > vw + 20 || y < -20 || y > vh + 20) continue
            p.circle(x, y, (proj.radius * cam.zoom).coerceAtLeast(s(1.5f)), proj.color)
        }

        for (f in world.fighters) {
            if (!f.alive) continue
            val x = cam.worldToScreenX(f.pos.x); val y = cam.worldToScreenY(f.pos.y)
            val r = (f.size * cam.zoom).coerceAtLeast(s(2f))
            p.triangle(
                x + cos(f.angle) * r * 1.6f, y + sin(f.angle) * r * 1.6f,
                x + cos(f.angle + 2.5f) * r, y + sin(f.angle + 2.5f) * r,
                x + cos(f.angle - 2.5f) * r, y + sin(f.angle - 2.5f) * r, f.color)
        }

        for (sh in world.ships) if (sh.alive) drawShip(p, sh)

        drawHud(p)
        if (ended) drawOutcome(p)
    }

    private fun drawGrid(p: Painter) {
        val step = 200f
        val color = Palette.withAlpha(Palette.strokeSoft, 60)
        var gx = 0f
        while (gx <= world.worldW) {
            val x = cam.worldToScreenX(gx)
            if (x in 0f..vw) p.line(x, 0f, x, vh, color, s(1f))
            gx += step
        }
        var gy = 0f
        while (gy <= world.worldH) {
            val y = cam.worldToScreenY(gy)
            if (y in 0f..vh) p.line(0f, y, vw, y, color, s(1f))
            gy += step
        }
    }

    private fun drawShip(p: Painter, sh: CombatShip) {
        val x = cam.worldToScreenX(sh.pos.x); val y = cam.worldToScreenY(sh.pos.y)
        val r = (sh.size * cam.zoom).coerceAtLeast(s(4f))
        if (x < -r - 40 || x > vw + r + 40 || y < -r - 40 || y > vh + r + 40) return

        if (sh.isStation) {
            // Station: hexagon-ish ring.
            p.ringStroke(x, y, r, sh.color, s(3f))
            p.circle(x, y, r * 0.5f, Palette.withAlpha(sh.color, 120))
            if (sh.shield > 0f) p.ringStroke(x, y, r + s(6f), Palette.withAlpha(Palette.shieldBar, 160), s(2f))
        } else {
            if (sh.selected) p.ringStroke(x, y, r + s(8f), Palette.accent, s(2f))
            // shield bubble
            if (sh.shield > 0f)
                p.ringStroke(x, y, r + s(4f),
                    Palette.withAlpha(Palette.shieldBar, (120 * sh.shieldFraction + 40).toInt()), s(1.5f))
            val flash = if (sh.hullFlash > 0f) Palette.bad else sh.color
            p.triangle(
                x + cos(sh.angle) * r * 1.7f, y + sin(sh.angle) * r * 1.7f,
                x + cos(sh.angle + 2.4f) * r, y + sin(sh.angle + 2.4f) * r,
                x + cos(sh.angle - 2.4f) * r, y + sin(sh.angle - 2.4f) * r, flash)
        }

        // bars
        val bw = (r * 2.4f).coerceAtLeast(s(24f))
        val bx = x - bw / 2f; val by = y - r - s(12f)
        p.fillRect(bx, by, bw, s(3f), Palette.withAlpha(Palette.bgPanelLight, 200))
        p.fillRect(bx, by, bw * sh.hullFraction, s(3f), Palette.hullBar)
        if (sh.maxShield > 0f) {
            p.fillRect(bx, by - s(4f), bw, s(2.5f), Palette.withAlpha(Palette.bgPanelLight, 200))
            p.fillRect(bx, by - s(4f), bw * sh.shieldFraction, s(2.5f), Palette.shieldBar)
        }
        if (sh.isStation && sh.stationTroops > 0)
            p.text("พลรบ ${sh.stationTroops}", x, y + r + s(16f), s(12f), Palette.enemy, Paint.Align.CENTER)
    }

    private fun drawHud(p: Painter) {
        val h = s(44f)
        p.fillRect(0f, 0f, vw, h, Palette.withAlpha(Palette.bgPanel, 235))
        p.line(0f, h, vw, h, Palette.strokeSoft, s(1.5f))
        val obj = if (invasion) "ภารกิจ: บุกยึดสถานี ${system.name}" else "ยุทธการที่ ${system.name}"
        p.text(obj, s(14f), h * 0.64f, s(15f), Palette.accent, bold = true)
        val myShips = world.playerShipsAlive()
        val enemy = world.ships.count { it.team == 1 && it.alive }
        p.text("ยานของเรา: $myShips   ศัตรู: $enemy", vw - s(14f), h * 0.64f, s(14f),
            Palette.textPrimary, Paint.Align.RIGHT)
        if (invasion && world.station != null)
            p.text("สถานะสถานี: ${(world.station!!.hullFraction * 100).toInt()}%",
                vw / 2f, h * 0.64f, s(13f), Palette.warn, Paint.Align.CENTER)

        // bottom controls
        val bw = s(130f); val bh = s(42f); val pad = s(12f)
        val by = vh - bh - pad
        btnAll.set(pad, by, bw, bh); btnAll.draw(p)
        btnAuto.set(pad * 2 + bw, by, bw, bh); btnAuto.draw(p)
        btnRetreat.set(vw - bw - pad, by, bw, bh); btnRetreat.draw(p)
        val selc = world.selectedCount
        if (selc > 0)
            p.text("เลือก $selc ลำ · แตะเพื่อสั่งเคลื่อน/โจมตี", vw / 2f, by - s(10f), s(12f),
                Palette.accent, Paint.Align.CENTER)
        else
            p.text("แตะยานของคุณเพื่อเลือก · ลากเพื่อเลื่อนจอ · หุบนิ้วเพื่อซูม",
                vw / 2f, by - s(10f), s(12f), Palette.textMuted, Paint.Align.CENTER)
    }

    private fun drawOutcome(p: Painter) {
        p.fillRect(0f, 0f, vw, vh, Palette.withAlpha(0x000000, 170))
        val win = outcome == BattleResult.PLAYER_WIN
        val title = if (win) "ชัยชนะ!" else "พ่ายแพ้"
        p.text(title, vw / 2f, vh * 0.4f, s(40f), if (win) Palette.good else Palette.bad,
            Paint.Align.CENTER, true)
        val sub = if (win) "กวาดล้างศัตรูสำเร็จ +₡${world.salvageCredits} จากซากยาน"
            else "กองยานถูกทำลายในสมรภูมิ"
        p.text(sub, vw / 2f, vh * 0.4f + s(36f), s(15f), Palette.textDim, Paint.Align.CENTER)
        btnContinue.set(vw / 2f - s(120f), vh * 0.58f, s(240f), s(50f))
        btnContinue.draw(p, primary = true)
    }

    // ---- input ----

    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {
        if (ended) return
        cam.panScreen(dx, dy)
        cam.clampTo(world.worldW, world.worldH)
    }

    override fun onPinch(factor: Float, focusX: Float, focusY: Float) {
        if (ended) return
        cam.applyPinch(factor)
    }

    override fun onTap(x: Float, y: Float) {
        if (ended) {
            if (btnContinue.hit(x, y)) resolveAndExit()
            return
        }
        // Ignore taps on control bars.
        if (y < s(44f)) return
        if (y > vh - s(60f)) {
            if (btnAll.hit(x, y)) { world.selectAll(); return }
            if (btnAuto.hit(x, y)) { world.setAutoEngageAll(); world.clearSelection(); game.toast("โหมดออโต้"); return }
            if (btnRetreat.hit(x, y)) { retreat(); return }
            return
        }
        val wx = cam.screenToWorldX(x); val wy = cam.screenToWorldY(y)
        if (!world.selectAt(wx, wy)) world.orderAt(wx, wy)
    }

    override fun onBack(): Boolean {
        if (ended) { resolveAndExit(); return true }
        retreat(); return true
    }

    // ---- resolution ----

    private fun retreat() {
        if (resolved) return
        // Commit damage but leave the system's hostiles in place.
        world.survivingPlayerShips().forEach { it.commitResult() }
        state.fleet.removeAll { it.isDestroyed }
        state.earn(world.salvageCredits / 2)
        resolved = true
        game.persist()
        game.toast("ล่าถอยจากสมรภูมิ")
        finishBattleNavigation()
    }

    private fun resolveAndExit() {
        if (resolved) { finishBattleNavigation(); return }
        world.survivingPlayerShips().forEach { it.commitResult() }
        val destroyedCount = state.fleet.count { it.isDestroyed }
        state.fleet.removeAll { it.isDestroyed }

        if (outcome == BattleResult.PLAYER_WIN) {
            state.earn(world.salvageCredits)
            // Enemies cleared from open space.
            system.patrolFleet.clear()
            if (invasion) {
                system.garrisonFleet.clear()
                when {
                    world.stationCaptured -> {
                        system.stationFaction = Faction.ALLY
                        system.owner = Faction.ALLY
                        system.stationTroops = 0
                        state.addRep(Faction.ALLY, 20)
                        game.toast("ยึดสถานีสำเร็จ! +₡${world.salvageCredits}")
                    }
                    world.stationDestroyed -> {
                        system.hasStation = false
                        system.stationFaction = Faction.NEUTRAL
                        system.owner = Faction.NEUTRAL
                        game.toast("ทำลายสถานีศัตรู! +₡${world.salvageCredits}")
                    }
                    else -> game.toast("ชนะการรบ! +₡${world.salvageCredits}")
                }
            } else {
                game.toast("ชนะการรบ! +₡${world.salvageCredits}")
            }
            state.checkMissionProgress()
        } else {
            game.toast("กองยานพ่ายแพ้ ($destroyedCount ลำถูกทำลาย)")
        }
        resolved = true
        game.persist()
        finishBattleNavigation()
    }

    private fun finishBattleNavigation() {
        if (state.fleet.isEmpty()) {
            // Total defeat — end the campaign.
            game.save.delete()
            game.toast("จบเกม: กองยานถูกทำลายทั้งหมด")
            game.setRoot(MainMenuScreen(game))
        } else {
            game.pop()
        }
    }
}
