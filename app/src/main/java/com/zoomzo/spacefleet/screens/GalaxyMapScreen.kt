package com.zoomzo.spacefleet.screens

import android.graphics.Paint
import com.zoomzo.spacefleet.engine.Backdrop
import com.zoomzo.spacefleet.engine.Button
import com.zoomzo.spacefleet.engine.Game
import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.engine.Screen
import com.zoomzo.spacefleet.model.EncounterType
import com.zoomzo.spacefleet.model.Faction
import com.zoomzo.spacefleet.model.StarSystem
import com.zoomzo.spacefleet.util.MathUtil
import kotlin.math.hypot
import kotlin.random.Random

/** Sector jump-map: jump forward node-to-node toward the boss; ambushes lurk. */
class GalaxyMapScreen(game: Game) : Screen(game) {

    private val state get() = game.state
    private val galaxy get() = state.galaxy
    private val rng = Random(System.nanoTime())

    private var mox = 0f
    private var moy = 0f
    private var mzoom = 1f
    private var needsFit = true
    private var lastSector = -1
    private var t = 0f
    private var selectedId = -1
    private val backdrop = Backdrop(99L)

    private val btnJump = Button(label = "JUMP")
    private val btnStation = Button(label = "เข้าสถานี")
    private val btnLoadout = Button(label = "ปรับแต่งยาน")
    private val btnTech = Button(label = "เทคโนโลยี")
    private val btnFormation = Button(label = "รูปแบบ")
    private val btnMenu = Button(label = "เมนู")

    override fun onEnter() {
        state.checkMissionProgress()
        if (galaxy.sectorNumber != lastSector) { needsFit = true; lastSector = galaxy.sectorNumber; selectedId = galaxy.currentId }
        if (galaxy.systems.none { it.id == selectedId }) selectedId = galaxy.currentId
        game.persist()
    }

    override fun update(dt: Float) { t += dt }

    private fun fit(p: Painter) {
        val minX = galaxy.systems.minOf { it.worldX }; val maxX = galaxy.systems.maxOf { it.worldX }
        val minY = galaxy.systems.minOf { it.worldY }; val maxY = galaxy.systems.maxOf { it.worldY }
        val gw = (maxX - minX).coerceAtLeast(1f); val gh = (maxY - minY).coerceAtLeast(1f)
        val topPad = p.s(56f); val botPad = p.s(150f)
        val zx = (p.width - p.s(60f)) / gw
        val zy = (p.height - topPad - botPad) / gh
        mzoom = minOf(zx, zy).coerceIn(0.35f, 2.2f)
        mox = (p.width - gw * mzoom) / 2f - minX * mzoom
        moy = topPad + (p.height - topPad - botPad - gh * mzoom) / 2f - minY * mzoom
        needsFit = false
    }

    private fun sx(wx: Float) = wx * mzoom + mox
    private fun sy(wy: Float) = wy * mzoom + moy

    override fun draw(p: Painter) {
        sync(p)
        if (needsFit) fit(p)
        backdrop.draw(p, t, t * 3f)

        // Links.
        for (s in galaxy.systems) for (linkId in s.links) {
            val to = galaxy.system(linkId)
            val reachableNow = s.id == galaxy.currentId
            val col = if (reachableNow) Palette.withAlpha(Palette.accent, 150) else Palette.withAlpha(Palette.strokeSoft, 90)
            p.line(sx(s.worldX), sy(s.worldY), sx(to.worldX), sy(to.worldY), col, p.s(if (reachableNow) 2f else 1.2f))
        }

        for (s in galaxy.systems) drawNode(p, s)

        Hud.topBar(p, state, "เซกเตอร์ ${galaxy.sectorNumber} · ${galaxy.current.name}")
        drawBottomPanel(p)
    }

    private fun encounterColor(s: StarSystem): Int = when (s.encounter) {
        EncounterType.START -> Palette.player
        EncounterType.COMBAT -> Palette.enemy
        EncounterType.ELITE -> Palette.pirate
        EncounterType.STATION -> Palette.ally
        EncounterType.RESOURCE -> Palette.accentWarm
        EncounterType.UNKNOWN -> Palette.neutral
        EncounterType.BOSS -> Palette.bad
    }

    private fun encounterGlyph(s: StarSystem): String = when (s.encounter) {
        EncounterType.START -> "◉"
        EncounterType.COMBAT -> "⚔"
        EncounterType.ELITE -> "☠"
        EncounterType.STATION -> "⌂"
        EncounterType.RESOURCE -> "◆"
        EncounterType.UNKNOWN -> "?"
        EncounterType.BOSS -> "✷"
    }

    private fun drawNode(p: Painter, s: StarSystem) {
        val x = sx(s.worldX); val y = sy(s.worldY)
        val reachable = galaxy.isReachable(galaxy.currentId, s.id)
        val isCurrent = s.id == galaxy.currentId
        val r = p.s(if (s.isBoss) 18f else 13f)

        if (s.id == selectedId) p.ringStroke(x, y, r + p.s(9f), Palette.accent, p.s(2f))
        if (isCurrent) {
            val pulse = r + p.s(10f) + ((t * 2f) % 1f) * p.s(6f)
            p.ringStroke(x, y, pulse, Palette.player, p.s(2f))
        } else if (reachable) {
            p.ringStroke(x, y, r + p.s(6f), Palette.withAlpha(Palette.accent, 180), p.s(1.6f))
        }

        val col = encounterColor(s)
        val dim = s.resolved && !isCurrent
        if (!dim) p.glow(x, y, r * 1.5f, col, if (isCurrent || reachable) 60 else 34)
        p.circle(x, y, r, if (dim) Palette.withAlpha(col, 90) else col)
        p.circle(x, y, r * 0.62f, if (dim) Palette.withAlpha(Palette.bgDeep, 160) else Palette.darken(col, 0.35f))
        p.text(encounterGlyph(s), x, y + p.s(5f), p.s(14f),
            if (dim) Palette.textMuted else Palette.lighten(col, 0.6f), Paint.Align.CENTER, true)

        if (s.resolved && !isCurrent)
            p.text("✓", x + r, y - r, p.s(11f), Palette.good, Paint.Align.CENTER, true)
        if (s.isAmbush && !s.resolved)
            p.text("!", x + r, y - r, p.s(13f), Palette.bad, Paint.Align.CENTER, true)

        val label = if (s.isBoss) "BOSS" else s.encounter.displayName
        p.text(label, x, y - r - p.s(6f), p.s(10f),
            if (isCurrent) Palette.player else Palette.textDim, Paint.Align.CENTER)
    }

    private fun drawBottomPanel(p: Painter) {
        val ph = p.s(150f); val py = p.height - ph
        p.fillRect(0f, py, p.width, ph, Palette.withAlpha(Palette.bgPanel, 245))
        p.line(0f, py, p.width, py, Palette.strokeSoft, p.s(1.5f))
        val pad = p.s(16f)

        val sel = galaxy.systems.firstOrNull { it.id == selectedId }
        if (sel != null) {
            p.text(sel.name, pad, py + p.s(26f), p.s(19f), Palette.textPrimary, bold = true)
            p.text("ประเภท: ${sel.encounter.displayName}", pad, py + p.s(48f), p.s(13f), encounterColor(sel))
            p.text("อันตราย: ${"★".repeat(sel.danger.coerceIn(0,6))}", pad, py + p.s(68f), p.s(13f), Palette.warn)
            val status = when {
                sel.resolved -> "เคลียร์แล้ว ✓"
                sel.hasHostilePatrol -> "ศัตรู ${sel.patrolFleet.size} ลำ${if (sel.isAmbush) " · เสี่ยงซุ่มโจมตี" else ""}"
                sel.encounter == EncounterType.RESOURCE -> "ทรัพยากร +₡${sel.rewardCredits}"
                sel.encounter == EncounterType.STATION -> "สถานีพันธมิตร"
                sel.encounter == EncounterType.UNKNOWN -> "ไม่ทราบสิ่งที่รออยู่"
                else -> "-"
            }
            p.text("สถานะ: $status", pad, py + p.s(88f), p.s(13f),
                if (sel.hasHostilePatrol) Palette.bad else Palette.good)
            if (!galaxy.isReachable(galaxy.currentId, sel.id) && sel.id != galaxy.currentId)
                p.text("(ต้องอยู่ในระยะ JUMP จากโหนดปัจจุบัน)", pad, py + p.s(108f), p.s(12f), Palette.textMuted)
        }

        // Buttons.
        val bw = p.s(150f); val bh = p.s(40f)
        val col1 = p.width - bw - pad
        val col2 = p.width - bw * 2 - pad * 1.5f
        val row1 = py + p.s(16f); val row2 = row1 + bh + p.s(9f); val row3 = row2 + bh + p.s(9f)

        val cur = galaxy.current
        val canJump = sel != null && galaxy.isReachable(cur.id, sel.id)
        btnJump.set(col1, row1, bw, bh); btnJump.enabled = canJump
        btnJump.label = if (sel != null && sel.hasHostilePatrol) "JUMP & สู้" else "JUMP"
        btnJump.draw(p, primary = canJump)

        val atAlly = cur.hasStation && cur.stationFaction == Faction.ALLY
        if (atAlly) { btnStation.set(col1, row2, bw, bh); btnStation.draw(p, primary = true) }

        btnLoadout.set(col2, row1, bw, bh); btnLoadout.draw(p)
        btnTech.set(col2, row2, bw, bh)
        btnTech.label = "เทคโนโลยี (⚙${state.research})"
        btnTech.draw(p, primary = state.research > 0)
        btnFormation.set(col2, row3, bw, bh)
        btnFormation.label = "รูปแบบ: ${formationShort()}"
        btnFormation.draw(p)
        btnMenu.set(col1, row3, bw, bh); btnMenu.draw(p)
    }

    private fun formationShort(): String = when (state.formation) {
        com.zoomzo.spacefleet.model.Formation.LINE -> "Line"
        com.zoomzo.spacefleet.model.Formation.WEDGE -> "Wedge"
        com.zoomzo.spacefleet.model.Formation.WALL -> "Wall"
        com.zoomzo.spacefleet.model.Formation.ECHELON -> "Echelon"
    }

    // ---- input ----

    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) { mox += dx; moy += dy }

    override fun onPinch(factor: Float, focusX: Float, focusY: Float) {
        val nz = MathUtil.clamp(mzoom * factor, 0.3f, 3f)
        val wx = (focusX - mox) / mzoom; val wy = (focusY - moy) / mzoom
        mzoom = nz; mox = focusX - wx * mzoom; moy = focusY - wy * mzoom
    }

    override fun onTap(x: Float, y: Float) {
        if (btnJump.hit(x, y)) { doJump(); return }
        if (btnStation.hit(x, y)) { game.push(StationScreen(game)); return }
        if (btnLoadout.hit(x, y)) { game.push(LoadoutScreen(game)); return }
        if (btnTech.hit(x, y)) { game.push(TechScreen(game)); return }
        if (btnFormation.hit(x, y)) { cycleFormation(); return }
        if (btnMenu.hit(x, y)) { game.persist(); game.setRoot(MainMenuScreen(game)); return }

        if (y > vh - s(150f) || y < s(46f)) return
        var best: StarSystem? = null; var bestD = Float.MAX_VALUE
        for (node in galaxy.systems) {
            val d = hypot(x - sx(node.worldX), y - sy(node.worldY))
            if (d < s(26f) && d < bestD) { bestD = d; best = node }
        }
        if (best != null) selectedId = best.id
    }

    private fun cycleFormation() {
        val vals = com.zoomzo.spacefleet.model.Formation.values()
        state.formation = vals[(state.formation.ordinal + 1) % vals.size]
        game.toast("รูปแบบกองยาน: ${state.formation.displayName}")
        game.persist()
    }

    private fun doJump() {
        val selId = selectedId
        if (!galaxy.isReachable(galaxy.currentId, selId)) return
        if (state.fleet.isEmpty()) { game.toast("ไม่มียานในกองเรือ!"); return }
        state.warpTo(selId)
        selectedId = selId
        resolveArrival(galaxy.current)
    }

    private fun resolveArrival(node: StarSystem) {
        val combatNode = node.hasHostilePatrol
        val ambushRoll = !node.resolved && !combatNode && rng.nextFloat() < state.jumpAmbushChance()
        when {
            combatNode -> {
                val invasion = node.hasStation && node.stationFaction == Faction.ENEMY
                startCombat(node.id, invasion, node.isAmbush)
            }
            ambushRoll -> {
                injectAmbush(node)
                game.toast("⚠ ถูกซุ่มโจมตี! ศัตรูวาร์ปเข้ามา")
                startCombat(node.id, invasion = false, ambush = true)
            }
            node.encounter == EncounterType.RESOURCE && !node.resolved -> {
                state.grantNodeReward(node); node.resolved = true
                game.toast("เก็บทรัพยากร +₡${node.rewardCredits} +${node.rewardResearch}⚙")
                game.persist()
            }
            node.encounter == EncounterType.UNKNOWN && !node.resolved -> resolveUnknown(node)
            node.encounter == EncounterType.STATION -> {
                node.resolved = true; game.toast("ถึงสถานีพันธมิตร ${node.name}"); game.persist()
            }
            else -> { game.toast("JUMP ถึง ${node.name}"); game.persist() }
        }
    }

    private fun injectAmbush(node: StarSystem) {
        node.isAmbush = true
        val danger = node.danger + galaxy.sectorNumber
        val size = 2 + rng.nextInt(2)
        repeat(size) {
            node.patrolFleet.add(
                if (danger >= 4 && rng.nextBoolean()) "destroyer"
                else if (danger >= 2) "frigate" else "interceptor"
            )
        }
    }

    private fun resolveUnknown(node: StarSystem) {
        val r = rng.nextFloat()
        when {
            r < 0.4f -> {
                val cr = 300 + node.danger * 200
                state.earn(cr); state.earnResearch(1 + node.danger); node.resolved = true
                game.toast("พบซากยาน! +₡$cr +${1 + node.danger}⚙"); game.persist()
            }
            r < 0.7f -> {
                injectAmbush(node)
                game.toast("⚠ กับดัก! ศัตรูซุ่มโจมตี")
                startCombat(node.id, invasion = false, ambush = true)
            }
            else -> { node.resolved = true; game.toast("พื้นที่ว่างเปล่า"); game.persist() }
        }
    }

    private fun startCombat(systemId: Int, invasion: Boolean, ambush: Boolean) {
        game.push(CombatScreen(game, galaxy.system(systemId), invasion, ambush))
    }
}
