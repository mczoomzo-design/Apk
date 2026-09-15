package com.zoomzo.spacefleet.screens

import android.graphics.Paint
import com.zoomzo.spacefleet.engine.Button
import com.zoomzo.spacefleet.engine.Game
import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.engine.Screen
import com.zoomzo.spacefleet.engine.Ui
import com.zoomzo.spacefleet.model.Faction
import com.zoomzo.spacefleet.model.MissionStatus
import com.zoomzo.spacefleet.model.StarSystem
import com.zoomzo.spacefleet.util.MathUtil
import kotlin.math.hypot

/** The galaxy map: warp between star systems, pick fights and open stations. */
class GalaxyMapScreen(game: Game) : Screen(game) {

    private val state get() = game.state
    private val galaxy get() = state.galaxy

    private var mox = 0f
    private var moy = 0f
    private var mzoom = 1f
    private var needsFit = true
    private var t = 0f

    private var selectedId: Int = -1
    private var showMissions = false

    private val btnWarp = Button(label = "วาร์ป")
    private val btnStation = Button(label = "เข้าสถานี")
    private val btnInvade = Button(label = "บุกยึดสถานี", accent = Palette.bad)
    private val btnLoadout = Button(label = "ปรับแต่งยาน")
    private val btnMissions = Button(label = "ภารกิจ")
    private val btnMenu = Button(label = "เมนู")

    override fun onEnter() {
        state.checkMissionProgress()
        game.persist()
        if (selectedId == -1) selectedId = galaxy.currentId
    }

    override fun update(dt: Float) { t += dt }

    private fun fit(p: Painter) {
        val minX = galaxy.systems.minOf { it.worldX }
        val maxX = galaxy.systems.maxOf { it.worldX }
        val minY = galaxy.systems.minOf { it.worldY }
        val maxY = galaxy.systems.maxOf { it.worldY }
        val gw = (maxX - minX).coerceAtLeast(1f)
        val gh = (maxY - minY).coerceAtLeast(1f)
        val topPad = p.s(56f); val botPad = p.s(160f)
        val zx = (p.width - p.s(40f)) / gw
        val zy = (p.height - topPad - botPad) / gh
        mzoom = minOf(zx, zy).coerceIn(0.4f, 2.5f)
        mox = (p.width - gw * mzoom) / 2f - minX * mzoom
        moy = topPad + (p.height - topPad - botPad - gh * mzoom) / 2f - minY * mzoom
        needsFit = false
    }

    private fun sx(wx: Float) = wx * mzoom + mox
    private fun sy(wy: Float) = wy * mzoom + moy

    override fun draw(p: Painter) {
        sync(p)
        if (needsFit) fit(p)
        p.clear(Palette.bgDeep)
        drawStarfield(p)

        val cur = galaxy.current
        // Reachable connections from current system.
        for (r in galaxy.reachableFrom(cur.id)) {
            p.dottedLine(sx(cur.worldX), sy(cur.worldY), sx(r.worldX), sy(r.worldY),
                Palette.withAlpha(Palette.accent, 90), p.s(1.2f), p.s(6f))
        }

        for (s in galaxy.systems) drawSystem(p, s, cur)

        Hud.topBar(p, state, "กาแล็กซี · ${cur.name}")
        drawBottomPanel(p)
        if (showMissions) drawMissionsOverlay(p)
    }

    private fun drawStarfield(p: Painter) {
        // cheap parallax dots
        val n = 60
        for (i in 0 until n) {
            val fx = ((i * 73 + t * 2) % p.width)
            val fy = ((i * 129) % p.height)
            p.circle(fx, fy, p.s(0.8f), Palette.withAlpha(Palette.textMuted, 70))
        }
    }

    private fun drawSystem(p: Painter, s: StarSystem, cur: StarSystem) {
        val x = sx(s.worldX); val y = sy(s.worldY)
        val known = s.explored || galaxy.isReachable(cur.id, s.id) || s.id == cur.id
        val baseR = p.s(9f)

        // selection / current rings
        if (s.id == selectedId) p.ringStroke(x, y, baseR + p.s(9f), Palette.accent, p.s(2f))
        if (s.id == cur.id) {
            val pulse = baseR + p.s(12f) + MathUtil.clamp((t * 2f) % 2f, 0f, 1f) * p.s(6f)
            p.ringStroke(x, y, pulse, Palette.player, p.s(2f))
        }

        // star
        val col = if (known) s.starColor else Palette.withAlpha(Palette.textMuted, 120)
        p.circle(x, y, baseR, col)

        // station / faction marker
        if (s.hasStation && known) {
            p.ringStroke(x, y, baseR + p.s(4f), s.stationFaction.color, p.s(2f))
        }
        // hostiles indicator
        if (known && s.hasHostilePatrol) {
            p.triangle(x + baseR + p.s(3f), y - baseR, x + baseR + p.s(11f), y - baseR,
                x + baseR + p.s(7f), y - baseR - p.s(8f), Palette.bad)
        }
        // danger dots
        if (known && s.danger > 0) {
            p.text("★".repeat(s.danger.coerceAtMost(3)), x, y + baseR + p.s(13f), p.s(9f),
                Palette.withAlpha(Palette.warn, 200), Paint.Align.CENTER)
        }
        if (known) {
            p.text(shortName(s.name), x, y - baseR - p.s(6f), p.s(11f),
                if (s.id == cur.id) Palette.player else Palette.textDim, Paint.Align.CENTER)
        } else {
            p.text("?", x, y + p.s(4f), p.s(12f), Palette.textMuted, Paint.Align.CENTER)
        }
    }

    private fun shortName(n: String): String = if (n.length > 14) n.take(13) + "…" else n

    private fun drawBottomPanel(p: Painter) {
        val ph = p.s(150f)
        val py = p.height - ph
        p.fillRect(0f, py, p.width, ph, Palette.withAlpha(Palette.bgPanel, 245))
        p.line(0f, py, p.width, py, Palette.strokeSoft, p.s(1.5f))

        val sel = galaxy.systems.firstOrNull { it.id == selectedId }
        val pad = p.s(16f)
        if (sel != null) {
            val known = sel.explored || galaxy.isReachable(galaxy.currentId, sel.id) || sel.id == galaxy.currentId
            p.text(if (known) sel.name else "ระบบที่ยังไม่สำรวจ", pad, py + p.s(26f),
                p.s(19f), Palette.textPrimary, bold = true)
            if (known) {
                p.text("ครอบครองโดย: ${sel.owner.displayName}", pad, py + p.s(48f), p.s(13f), sel.owner.color)
                p.text("อันตราย: ${"★".repeat(sel.danger)}${"·".repeat(5 - sel.danger)}",
                    pad, py + p.s(68f), p.s(13f), Palette.warn)
                val hostiles = if (sel.hasHostilePatrol) "ศัตรู ${sel.patrolFleet.size} ลำ" else "ปลอดภัย"
                p.text("สถานะ: $hostiles", pad, py + p.s(88f), p.s(13f),
                    if (sel.hasHostilePatrol) Palette.bad else Palette.good)
                if (sel.hasStation)
                    p.text("มีสถานีอวกาศ (${sel.stationFaction.displayName})",
                        pad, py + p.s(108f), p.s(13f), sel.stationFaction.color)
            }
        }

        // Action buttons (right side of panel)
        val bw = p.s(150f); val bh = p.s(40f)
        var bx = p.width - bw - pad
        val row1 = py + p.s(18f)
        val row2 = row1 + bh + p.s(10f)
        val row3 = row2 + bh + p.s(10f)

        val cur = galaxy.current
        val canWarp = sel != null && sel.id != cur.id && galaxy.isReachable(cur.id, sel.id)
        btnWarp.set(bx, row1, bw, bh); btnWarp.enabled = canWarp
        btnWarp.label = if (sel != null && sel.hasHostilePatrol) "วาร์ป & สู้" else "วาร์ป"
        btnWarp.draw(p, primary = canWarp)

        // Context button for current system
        val atAlly = cur.hasStation && cur.stationFaction == Faction.ALLY
        val enemyStationHere = cur.hasStation &&
            (cur.stationFaction == Faction.ENEMY || cur.stationFaction == Faction.PIRATE) &&
            !cur.hasHostilePatrol
        when {
            atAlly -> { btnStation.set(bx, row2, bw, bh); btnStation.draw(p, primary = true) }
            enemyStationHere -> { btnInvade.set(bx, row2, bw, bh); btnInvade.draw(p, primary = true) }
            else -> { }
        }

        bx = p.width - bw * 2 - pad * 1.5f
        btnLoadout.set(bx, row1, bw, bh); btnLoadout.draw(p)
        btnMissions.set(bx, row2, bw, bh)
        btnMissions.label = "ภารกิจ (${state.activeMissions.size})"
        btnMissions.draw(p)
        btnMenu.set(bx, row3, bw, bh); btnMenu.draw(p)
    }

    private fun drawMissionsOverlay(p: Painter) {
        val w = p.width * 0.7f; val h = p.height * 0.7f
        val x = (p.width - w) / 2f; val y = (p.height - h) / 2f
        Ui.panel(p, x, y, w, h, "บันทึกภารกิจ")
        var yy = y + p.s(56f)
        val active = state.missions.filter { it.status == MissionStatus.ACTIVE }
        if (active.isEmpty())
            p.text("ยังไม่มีภารกิจที่รับ — ไปรับที่สถานีพันธมิตร", x + p.s(16f), yy, p.s(14f), Palette.textDim)
        for (m in active) {
            val target = galaxy.systems.firstOrNull { it.id == m.targetSystemId }
            p.text("• ${m.title}", x + p.s(16f), yy, p.s(15f), Palette.textPrimary, bold = true)
            p.text("₡${m.rewardCredits}", x + w - p.s(16f), yy, p.s(14f), Palette.accentWarm, Paint.Align.RIGHT)
            yy += p.s(20f)
            p.text(m.desc, x + p.s(24f), yy, p.s(12f), Palette.textDim)
            yy += p.s(30f)
            if (yy > y + h - p.s(30f)) break
        }
        p.text("แตะที่ใดก็ได้เพื่อปิด", x + w / 2f, y + h - p.s(14f), p.s(12f),
            Palette.textMuted, Paint.Align.CENTER)
    }

    // ---- input ----

    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {
        if (showMissions) return
        mox += dx; moy += dy
    }

    override fun onPinch(factor: Float, focusX: Float, focusY: Float) {
        val newZoom = MathUtil.clamp(mzoom * factor, 0.4f, 3f)
        // zoom around focus
        val wx = (focusX - mox) / mzoom
        val wy = (focusY - moy) / mzoom
        mzoom = newZoom
        mox = focusX - wx * mzoom
        moy = focusY - wy * mzoom
    }

    override fun onTap(x: Float, y: Float) {
        if (showMissions) { showMissions = false; return }

        // Buttons first.
        if (btnWarp.hit(x, y)) { doWarp(); return }
        if (btnStation.hit(x, y)) { game.push(StationScreen(game)); return }
        if (btnInvade.hit(x, y)) { startCombat(galaxy.current.id, invasion = true); return }
        if (btnLoadout.hit(x, y)) { game.push(LoadoutScreen(game)); return }
        if (btnMissions.hit(x, y)) { showMissions = true; return }
        if (btnMenu.hit(x, y)) { game.persist(); game.setRoot(MainMenuScreen(game)); return }

        // Otherwise pick a system (ignore taps inside the bottom panel or top HUD).
        if (y > vh - s(150f) || y < s(46f)) return
        var best: StarSystem? = null; var bestD = Float.MAX_VALUE
        for (sysItem in galaxy.systems) {
            val d = hypot(x - sx(sysItem.worldX), y - sy(sysItem.worldY))
            if (d < s(22f) && d < bestD) { bestD = d; best = sysItem }
        }
        if (best != null) selectedId = best.id
    }

    private fun doWarp() {
        val sel = selectedId
        if (sel == galaxy.currentId) return
        if (!galaxy.isReachable(galaxy.currentId, sel)) return
        state.warpTo(sel)
        selectedId = sel
        game.persist()
        val cur = galaxy.current
        if (cur.hasHostilePatrol) {
            startCombat(cur.id, invasion = false)
        } else {
            game.toast("วาร์ปถึง ${cur.name}")
        }
    }

    private fun startCombat(systemId: Int, invasion: Boolean) {
        if (state.fleet.isEmpty()) { game.toast("ไม่มียานในกองเรือ!"); return }
        game.push(CombatScreen(game, galaxy.system(systemId), invasion))
    }
}
