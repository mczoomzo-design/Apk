package com.zoomzo.spacefleet.screens

import android.graphics.Paint
import com.zoomzo.spacefleet.combat.BattleResult
import com.zoomzo.spacefleet.combat.CombatShip
import com.zoomzo.spacefleet.combat.CombatWorld
import com.zoomzo.spacefleet.combat.ParticleKind
import com.zoomzo.spacefleet.combat.ShipArt
import com.zoomzo.spacefleet.engine.Backdrop
import com.zoomzo.spacefleet.engine.Button
import com.zoomzo.spacefleet.engine.Camera
import com.zoomzo.spacefleet.engine.Game
import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.engine.Screen
import com.zoomzo.spacefleet.engine.Ui
import com.zoomzo.spacefleet.model.Catalog
import com.zoomzo.spacefleet.model.Faction
import com.zoomzo.spacefleet.model.StarSystem
import com.zoomzo.spacefleet.util.MathUtil
import kotlin.math.cos
import kotlin.math.sin

/** Top-down RTS battle. Tap a ship to select, tap to move/attack; pinch to zoom. */
class CombatScreen(
    game: Game,
    private val system: StarSystem,
    private val invasion: Boolean,
    private val ambush: Boolean = false
) : Screen(game) {

    private val state get() = game.state
    private val world = CombatWorld(
        state.fleet, system, invasion, state.techBonus(), state.formation,
        ambush || system.isAmbush, state.difficulty
    )
    private var gainedResearch = 0
    private var gainedCredits = 0
    private var gainedWeapon: String? = null
    private var gainedModule: String? = null
    private val lootRng = kotlin.random.Random(System.nanoTime())
    private val cam = Camera()
    private var camInit = false
    private val backdrop = Backdrop(system.id.toLong() * 7919L + 13L)
    private var t = 0f

    // playback speed: 0 = paused
    private var speed = 1f
    private var paused = false
    private val btnPause = Button(label = "⏸")
    private val btnSp05 = Button(label = "0.5x")
    private val btnSp1 = Button(label = "1x")
    private val btnSp2 = Button(label = "2x")

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
        t += dt
        if (ended) return
        val scale = if (paused) 0f else speed
        if (scale <= 0f) return
        // step in capped sub-steps so 2x speed stays stable
        var remaining = dt * scale
        while (remaining > 0f) {
            val step = remaining.coerceAtMost(0.05f)
            world.update(step)
            remaining -= step
        }
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
        backdrop.draw(p, t, cam.cx)
        drawGrid(p)

        for (tr in world.tracers)
            p.line(cam.worldToScreenX(tr.x1), cam.worldToScreenY(tr.y1),
                cam.worldToScreenX(tr.x2), cam.worldToScreenY(tr.y2),
                Palette.withAlpha(tr.color, 220), s(2f))

        drawProjectiles(p)
        drawParticles(p)

        for (f in world.fighters) {
            if (!f.alive) continue
            val x = cam.worldToScreenX(f.pos.x); val y = cam.worldToScreenY(f.pos.y)
            val r = (f.size * cam.zoom).coerceAtLeast(s(2f))
            p.glow(x - cos(f.angle) * r, y - sin(f.angle) * r, r * 0.8f,
                if (f.team == 0) Palette.enginePlayer else Palette.engineEnemy, 60)
            p.triangle(
                x + cos(f.angle) * r * 1.7f, y + sin(f.angle) * r * 1.7f,
                x + cos(f.angle + 2.5f) * r, y + sin(f.angle + 2.5f) * r,
                x + cos(f.angle - 2.5f) * r, y + sin(f.angle - 2.5f) * r,
                Palette.lighten(f.color, 0.2f))
        }

        drawWarpPortals(p)
        drawOrderLines(p)
        for (sh in world.ships) if (sh.alive) drawShip(p, sh)

        drawHud(p)
        drawMinimap(p)
        drawSelectedInfo(p)
        drawWarpWarning(p)
        if (ended) drawOutcome(p)
    }

    private fun drawProjectiles(p: Painter) {
        for (proj in world.projectiles) {
            val x = cam.worldToScreenX(proj.pos.x); val y = cam.worldToScreenY(proj.pos.y)
            if (x < -20 || x > vw + 20 || y < -20 || y > vh + 20) continue
            val r = (proj.radius * cam.zoom).coerceAtLeast(s(1.5f))
            p.glow(x, y, r * 2.2f, proj.color, 55)
            p.circle(x, y, r, Palette.lighten(proj.color, 0.4f))
        }
    }

    private fun drawParticles(p: Painter) {
        for (pt in world.particles) {
            val x = cam.worldToScreenX(pt.pos.x); val y = cam.worldToScreenY(pt.pos.y)
            if (x < -30 || x > vw + 30 || y < -30 || y > vh + 30) continue
            val a = (pt.fade * 255).toInt().coerceIn(0, 255)
            val r = (pt.size * cam.zoom).coerceAtLeast(s(1f))
            when (pt.kind) {
                ParticleKind.FLASH -> p.glow(x, y, r, pt.color, (a * 0.6f).toInt())
                ParticleKind.SMOKE -> p.circle(x, y, r, Palette.withAlpha(pt.color, (a * 0.4f).toInt()))
                else -> p.circle(x, y, r, Palette.withAlpha(pt.color, a))
            }
        }
    }

    /** Telegraphed warp portals where hostile reinforcements are about to jump in. */
    private fun drawWarpPortals(p: Painter) {
        for (w in world.waves) {
            if (!w.portalVisible) continue
            val x = cam.worldToScreenX(w.pos.x); val y = cam.worldToScreenY(w.pos.y)
            val base = s(52f) * cam.zoom.coerceAtLeast(0.5f)
            val r = base * (0.3f + w.progress)
            val a = (200 * (0.3f + w.progress * 0.7f)).toInt().coerceAtMost(255)
            p.glow(x, y, r * 0.7f, Palette.shieldBar, (a * 0.4f).toInt())
            val rot = t * 200f
            p.arc(x, y, r, rot, 110f, Palette.withAlpha(Palette.accent, a), s(3f))
            p.arc(x, y, r, rot + 180f, 110f, Palette.withAlpha(Palette.pirate, a), s(3f))
            p.ringStroke(x, y, r * 0.5f, Palette.withAlpha(Palette.warn, a), s(1.5f))
            p.text("⚠", x, y + s(6f), s(18f), Palette.withAlpha(Palette.bad, a), Paint.Align.CENTER, true)
        }
    }

    private fun drawWarpWarning(p: Painter) {
        if (world.lastWarpWarning <= 0f || ended) return
        val blink = ((world.lastWarpWarning * 4f).toInt() % 2 == 0)
        if (!blink) return
        val msg = "⚠ ศัตรูกำลังวาร์ปเข้ามา!"
        val tw = p.textWidth(msg, s(20f), true)
        val bw = tw + s(40f)
        p.fillRound((vw - bw) / 2f, s(54f), bw, s(38f), s(8f), Palette.withAlpha(Palette.bad, 60))
        p.text(msg, vw / 2f, s(80f), s(20f), Palette.bad, Paint.Align.CENTER, true)
    }

    private fun drawGrid(p: Painter) {
        val step = 260f
        val color = Palette.withAlpha(Palette.strokeSoft, 34)
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

    private val octBuf = FloatArray(16)

    private fun drawShip(p: Painter, sh: CombatShip) {
        val x = cam.worldToScreenX(sh.pos.x); val y = cam.worldToScreenY(sh.pos.y)
        val r = (sh.size * cam.zoom).coerceAtLeast(s(4f))
        if (x < -r - 60 || x > vw + r + 60 || y < -r - 60 || y > vh + r + 60) return

        if (sh.selected && !sh.isStation) drawBrackets(p, x, y, r * 1.7f, Palette.selection)

        if (sh.isStation) {
            p.glow(x, y, r * 1.05f, sh.color, 34)
            val rot = t * 0.4f
            for (i in 0 until 8) {
                val a = rot + i * (MathUtil.TAU / 8f)
                octBuf[i * 2] = x + cos(a) * r
                octBuf[i * 2 + 1] = y + sin(a) * r
            }
            p.polygon(octBuf, Palette.darken(sh.color, 0.55f))
            p.polygonOutline(octBuf, Palette.lighten(sh.color, 0.25f), s(2.5f))
            p.ringStroke(x, y, r * 0.55f, Palette.lighten(sh.color, 0.3f), s(2f))
            p.circle(x, y, r * 0.22f, Palette.lighten(sh.color, 0.5f))
            if (sh.shield > 0f)
                p.ringStroke(x, y, r + s(7f), Palette.withAlpha(Palette.shieldBar,
                    (120 * sh.shieldFraction + 40).toInt()), s(2f))
        } else {
            if (sh.shield > 0f)
                p.ringStroke(x, y, r + s(4f), Palette.withAlpha(Palette.shieldBar,
                    (110 * sh.shieldFraction + 40).toInt()), s(1.5f))
            val engine = if (sh.team == 0) Palette.enginePlayer else Palette.engineEnemy
            ShipArt.draw(p, sh.cls, x, y, sh.angle, r, sh.color, engine, t)
            if (sh.hullFlash > 0f) p.glow(x, y, r * 1.3f, Palette.bad, 90)
        }

        // health/shield bars
        val bw = (r * 2.4f).coerceAtLeast(s(24f))
        val bx = x - bw / 2f; val by = y - r - s(13f)
        p.fillRound(bx, by, bw, s(3.2f), s(1.5f), Palette.withAlpha(Palette.bgDeep, 220))
        p.fillRound(bx, by, bw * sh.hullFraction, s(3.2f), s(1.5f),
            if (sh.team == 0) Palette.hullBar else Palette.enemy)
        if (sh.maxShield > 0f) {
            p.fillRound(bx, by - s(4.5f), bw, s(2.6f), s(1.3f), Palette.withAlpha(Palette.bgDeep, 220))
            p.fillRound(bx, by - s(4.5f), bw * sh.shieldFraction, s(2.6f), s(1.3f), Palette.shieldBar)
        }
        if (sh.isStation && sh.stationTroops > 0)
            p.text("พลรบ ${sh.stationTroops}", x, y + r + s(18f), s(12f), Palette.enemy, Paint.Align.CENTER)

        if (sh.source != null && sh.source.id == state.flagshipId) {
            val dy = by - s(10f)
            p.triangle(x, dy - s(6f), x - s(5f), dy, x + s(5f), dy, Palette.accentWarm)
            p.triangle(x, dy + s(6f), x - s(5f), dy, x + s(5f), dy, Palette.accentWarm)
        }
    }

    /** Waypoint / attack lines showing where each ship is heading. */
    private fun drawOrderLines(p: Painter) {
        val hasSel = world.selectedCount > 0
        for (sh in world.ships) {
            if (sh.team != 0 || !sh.alive || sh.isStation) continue
            val emphasize = sh.selected || !hasSel
            val sxp = cam.worldToScreenX(sh.pos.x); val syp = cam.worldToScreenY(sh.pos.y)
            val tgt = sh.attackTarget
            val goal = sh.moveGoal
            if (tgt != null && tgt.alive) {
                val tx = cam.worldToScreenX(tgt.pos.x); val ty = cam.worldToScreenY(tgt.pos.y)
                val a = if (emphasize) 200 else 60
                p.dottedLine(sxp, syp, tx, ty, Palette.withAlpha(Palette.bad, a), s(1.6f), s(7f))
                if (emphasize) {
                    p.ringStroke(tx, ty, s(11f), Palette.withAlpha(Palette.bad, 220), s(1.6f))
                    p.line(tx - s(14f), ty, tx + s(14f), ty, Palette.withAlpha(Palette.bad, 180), s(1f))
                    p.line(tx, ty - s(14f), tx, ty + s(14f), Palette.withAlpha(Palette.bad, 180), s(1f))
                }
            } else if (goal != null) {
                val gx = cam.worldToScreenX(goal.x); val gy = cam.worldToScreenY(goal.y)
                val a = if (emphasize) 200 else 55
                p.dottedLine(sxp, syp, gx, gy, Palette.withAlpha(Palette.selection, a), s(1.6f), s(7f))
                if (emphasize) {
                    p.ringStroke(gx, gy, s(8f), Palette.withAlpha(Palette.selection, 220), s(1.6f))
                    p.circle(gx, gy, s(2.5f), Palette.selection)
                }
            }
        }
    }

    /** RTS-style corner brackets around a selected unit. */
    private fun drawBrackets(p: Painter, x: Float, y: Float, half: Float, color: Int) {
        val c = color; val lw = s(2f); val len = half * 0.4f
        // top-left
        p.line(x - half, y - half, x - half + len, y - half, c, lw)
        p.line(x - half, y - half, x - half, y - half + len, c, lw)
        // top-right
        p.line(x + half, y - half, x + half - len, y - half, c, lw)
        p.line(x + half, y - half, x + half, y - half + len, c, lw)
        // bottom-left
        p.line(x - half, y + half, x - half + len, y + half, c, lw)
        p.line(x - half, y + half, x - half, y + half - len, c, lw)
        // bottom-right
        p.line(x + half, y + half, x + half - len, y + half, c, lw)
        p.line(x + half, y + half, x + half, y + half - len, c, lw)
    }

    /** Tactical minimap in the bottom-right corner. */
    private fun drawMinimap(p: Painter) {
        val mw = s(150f); val mh = mw * (world.worldH / world.worldW)
        val mx = vw - mw - s(12f); val my = vh - mh - s(62f)
        Ui.frame(p, mx, my, mw, mh, Palette.panelEdge)
        val kx = mw / world.worldW; val ky = mh / world.worldH
        for (sh in world.ships) {
            if (!sh.alive) continue
            val dx = mx + sh.pos.x * kx; val dy = my + sh.pos.y * ky
            val col = when { sh.isStation -> Palette.warn; sh.team == 0 -> Palette.player; else -> Palette.enemy }
            p.circle(dx, dy, s(if (sh.isStation) 3f else 2f), col)
        }
        for (w in world.waves) if (w.portalVisible) {
            p.ringStroke(mx + w.pos.x * kx, my + w.pos.y * ky, s(3f), Palette.pirate, s(1f))
        }
        // viewport rect
        val vx0 = mx + cam.screenToWorldX(0f) * kx; val vy0 = my + cam.screenToWorldY(0f) * ky
        val vx1 = mx + cam.screenToWorldX(vw) * kx; val vy1 = my + cam.screenToWorldY(vh) * ky
        p.strokeRound(vx0.coerceIn(mx, mx + mw), vy0.coerceIn(my, my + mh),
            (vx1 - vx0).coerceAtMost(mw), (vy1 - vy0).coerceAtMost(mh), 0f,
            Palette.withAlpha(Palette.textPrimary, 120), s(1f))
    }

    /** Panel describing the single selected ship. */
    private fun drawSelectedInfo(p: Painter) {
        if (ended) return
        val sel = world.ships.firstOrNull { it.selected && it.team == 0 && it.alive } ?: return
        val w = s(190f); val h = s(74f)
        val x = s(12f); val y = vh - h - s(62f)
        Ui.frame(p, x, y, w, h, Palette.panelEdge)
        p.text(sel.displayName, x + s(12f), y + s(20f), s(14f), Palette.accent, bold = true)
        p.text(sel.cls.displayName, x + w - s(12f), y + s(20f), s(11f), Palette.textDim, Paint.Align.RIGHT)
        Ui.bar(p, x + s(12f), y + s(30f), w - s(24f), s(7f), sel.hullFraction, Palette.hullBar)
        p.text("HULL ${sel.hull.toInt()}/${sel.maxHull.toInt()}", x + s(12f), y + s(45f), s(10f), Palette.textDim)
        if (sel.maxShield > 0f) {
            Ui.bar(p, x + s(12f), y + s(50f), w - s(24f), s(6f), sel.shieldFraction, Palette.shieldBar)
            p.text("SHIELD ${sel.shield.toInt()}", x + s(12f), y + s(66f), s(10f), Palette.shieldBar)
        }
    }

    private fun drawHud(p: Painter) {
        val h = s(44f)
        Ui.headerStrip(p, h)
        val obj = if (invasion) "▶ บุกยึดสถานี ${system.name}" else "▶ ยุทธการที่ ${system.name}"
        p.text(obj, s(14f), h * 0.64f, s(15f), Palette.accent, bold = true)
        val myShips = world.playerShipsAlive()
        val enemy = world.ships.count { it.team == 1 && it.alive }
        val incoming = if (world.pendingWaves) "  ⚠วาร์ป:${world.waves.size}" else ""
        p.text("ยานเรา $myShips   ศัตรู $enemy$incoming", vw - s(14f), h * 0.64f, s(14f),
            if (world.pendingWaves) Palette.warn else Palette.textPrimary, Paint.Align.RIGHT)
        p.text("[${state.difficulty.displayName}]", vw / 2f + s(60f), h * 0.64f, s(11f),
            Palette.textMuted, Paint.Align.LEFT)
        if (invasion && world.station != null)
            p.text("สถานี ${(world.station!!.hullFraction * 100).toInt()}%",
                vw / 2f - s(40f), h * 0.64f, s(13f), Palette.warn, Paint.Align.RIGHT)

        // bottom command bar
        val bw = s(128f); val bh = s(42f); val pad = s(12f)
        val by = vh - bh - pad
        btnAll.set(pad, by, bw, bh); btnAll.draw(p)
        btnAuto.set(pad * 2 + bw, by, bw, bh); btnAuto.draw(p)
        btnRetreat.set(vw - bw - pad, by, bw, bh); btnRetreat.draw(p)
        val selc = world.selectedCount
        if (selc > 0)
            p.text("เลือก $selc ลำ · แตะเพื่อสั่งเคลื่อน/โจมตี", vw / 2f, by + bh * 0.62f, s(12f),
                Palette.accent, Paint.Align.CENTER)

        drawSpeedControls(p)
        if (paused) p.text("⏸ หยุดชั่วคราว", vw / 2f, s(66f), s(16f), Palette.warn, Paint.Align.CENTER, true)
    }

    private fun drawSpeedControls(p: Painter) {
        val by = s(50f); val bh = s(30f); var bx = s(12f)
        btnPause.set(bx, by, s(40f), bh); btnPause.label = if (paused) "▶" else "⏸"
        btnPause.draw(p, primary = paused); bx += s(46f)
        btnSp05.set(bx, by, s(44f), bh); btnSp05.draw(p, primary = !paused && speed == 0.5f); bx += s(50f)
        btnSp1.set(bx, by, s(44f), bh); btnSp1.draw(p, primary = !paused && speed == 1f); bx += s(50f)
        btnSp2.set(bx, by, s(44f), bh); btnSp2.draw(p, primary = !paused && speed == 2f)
    }

    private fun drawOutcome(p: Painter) {
        p.fillRect(0f, 0f, vw, vh, Palette.withAlpha(0x000000, 180))
        val win = outcome == BattleResult.PLAYER_WIN
        p.glow(vw / 2f, vh * 0.38f, s(120f), if (win) Palette.good else Palette.bad, 30)
        val title = if (win) "ชัยชนะ!" else "พ่ายแพ้"
        p.text(title, vw / 2f, vh * 0.4f, s(40f), if (win) Palette.good else Palette.bad,
            Paint.Align.CENTER, true)
        val sub = if (win) "รางวัล:  +₡$gainedCredits    +${world.researchReward} วิจัย"
            else "กองยานถูกทำลายในสมรภูมิ"
        p.text(sub, vw / 2f, vh * 0.4f + s(36f), s(16f), Palette.accentWarm, Paint.Align.CENTER, true)
        if (win) {
            val drops = buildString {
                gainedWeapon?.let { append("อาวุธ: $it") }
                if (gainedWeapon != null && gainedModule != null) append("    ")
                gainedModule?.let { append("อุปกรณ์: $it") }
            }
            if (drops.isNotBlank())
                p.text("ได้รับไอเทม → $drops", vw / 2f, vh * 0.4f + s(58f), s(14f),
                    Palette.good, Paint.Align.CENTER)
            else
                p.text("(นำเครดิตไปซื้อยาน/อาวุธที่สถานีพันธมิตร)", vw / 2f, vh * 0.4f + s(58f),
                    s(12f), Palette.textDim, Paint.Align.CENTER)
        }
        if (win && system.isBoss)
            p.text("ทำลายยานแม่ศัตรูสำเร็จ! กำลังเข้าสู่เซกเตอร์ถัดไป",
                vw / 2f, vh * 0.4f + s(80f), s(14f), Palette.accentWarm, Paint.Align.CENTER, true)
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
        // Speed / pause controls (top-left).
        if (btnPause.hit(x, y)) { paused = !paused; return }
        if (btnSp05.hit(x, y)) { speed = 0.5f; paused = false; return }
        if (btnSp1.hit(x, y)) { speed = 1f; paused = false; return }
        if (btnSp2.hit(x, y)) { speed = 2f; paused = false; return }

        // Ignore taps on the top header.
        if (y < s(44f)) return
        if (y > vh - s(60f)) {
            if (btnAll.hit(x, y)) { world.selectAll(); return }
            if (btnAuto.hit(x, y)) { world.setAutoEngageAll(); world.clearSelection(); game.toast("โหมดออโต้"); return }
            if (btnRetreat.hit(x, y)) { retreat(); return }
            return
        }
        // Minimap tap -> recenter camera.
        val mw = s(150f); val mh = mw * (world.worldH / world.worldW)
        val mx = vw - mw - s(12f); val my = vh - mh - s(62f)
        if (x in mx..(mx + mw) && y in my..(my + mh)) {
            cam.cx = (x - mx) / mw * world.worldW
            cam.cy = (y - my) / mh * world.worldH
            cam.clampTo(world.worldW, world.worldH)
            return
        }
        // Selected-info panel tap -> ignore.
        if (x < s(202f) && y > vh - s(136f)) return

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
            val mult = state.difficulty.rewardMult
            gainedCredits = ((world.salvageCredits + world.victoryBonus) * mult).toInt()
            state.earn(gainedCredits)
            state.earnResearch(world.researchReward)
            gainedResearch = world.researchReward
            rollLoot()
            // Enemies cleared; node is resolved.
            system.patrolFleet.clear()
            system.garrisonFleet.clear()
            system.resolved = true
            if (invasion || system.hasStation) {
                when {
                    world.stationCaptured -> {
                        system.stationFaction = Faction.ALLY; system.owner = Faction.ALLY
                        system.stationTroops = 0
                        game.toast("ยึดสถานีสำเร็จ! +₡$gainedCredits")
                    }
                    world.stationDestroyed -> {
                        system.hasStation = false
                        system.stationFaction = Faction.NEUTRAL; system.owner = Faction.NEUTRAL
                        game.toast("ทำลายสถานีศัตรู! +₡$gainedCredits")
                    }
                    else -> game.toast("ชนะการรบ! +₡$gainedCredits")
                }
            } else {
                game.toast("ชนะการรบ! +₡$gainedCredits +${world.researchReward} วิจัย")
            }
            state.checkMissionProgress()
        } else {
            game.toast("กองยานพ่ายแพ้ ($destroyedCount ลำถูกทำลาย)")
        }
        resolved = true
        game.persist()
        finishBattleNavigation()
    }

    private fun rollLoot() {
        val danger = system.danger
        if (lootRng.nextFloat() < 0.55f + danger * 0.05f) {
            val w = weightedPick(Catalog.weapons.map { it.id to it.cost })
            state.addWeapon(w); gainedWeapon = Catalog.weapon(w)?.name
        }
        if (lootRng.nextFloat() < 0.4f + danger * 0.05f) {
            val m = weightedPick(Catalog.modules.map { it.id to it.cost })
            state.addModule(m); gainedModule = Catalog.module(m)?.name
        }
        if (system.isBoss) {
            // guaranteed premium drop
            val w = Catalog.weapons.maxByOrNull { it.cost }!!.id
            state.addWeapon(w); gainedWeapon = Catalog.weapon(w)?.name
        }
    }

    /** Cheaper items drop more often (weight = 1 / cost). */
    private fun weightedPick(items: List<Pair<String, Int>>): String {
        val weights = items.map { 1f / it.second.coerceAtLeast(1) }
        val total = weights.sum()
        var r = lootRng.nextFloat() * total
        for (i in items.indices) { r -= weights[i]; if (r <= 0f) return items[i].first }
        return items.last().first
    }

    private fun finishBattleNavigation() {
        // Campaign ends if the flagship is lost or the whole fleet is destroyed.
        if (state.fleet.isEmpty() || !state.flagshipAlive()) {
            game.save.delete()
            val why = if (state.fleet.isEmpty()) "กองยานถูกทำลายทั้งหมด" else "เรือธงถูกทำลาย"
            game.toast("จบเกม: $why")
            game.setRoot(MainMenuScreen(game))
            return
        }
        // Clearing the boss advances to the next sector.
        if (outcome == BattleResult.PLAYER_WIN && system.isBoss) {
            state.advanceSector()
            game.toast("เข้าสู่เซกเตอร์ ${state.galaxy.sectorNumber}!")
            game.persist()
        }
        game.pop()
    }
}
