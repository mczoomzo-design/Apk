package com.zoomzo.spacefleet.screens

import com.zoomzo.spacefleet.engine.Button
import com.zoomzo.spacefleet.engine.Game
import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.engine.Screen
import com.zoomzo.spacefleet.engine.Ui
import com.zoomzo.spacefleet.model.Catalog
import com.zoomzo.spacefleet.model.Ship

/** Fit weapons and modules from inventory onto each ship in the fleet. */
class LoadoutScreen(game: Game) : Screen(game) {

    private val state get() = game.state
    private var selShip: Ship? = null
    // Slot selection: type 0 = weapon, 1 = module; index within that ship.
    private var slotType = -1
    private var slotIndex = -1

    private val btnBack = Button(label = "ออก")
    private val fleetButtons = mutableListOf<Pair<Ship, Button>>()
    private val slotButtons = mutableListOf<Triple<Int, Int, Button>>() // type, index, button
    private val invButtons = mutableListOf<Pair<() -> Unit, Button>>()

    override fun onEnter() { if (selShip == null || selShip !in state.fleet) selShip = state.fleet.firstOrNull() }

    override fun update(dt: Float) {}

    override fun draw(p: Painter) {
        sync(p)
        p.clear(Palette.bgDeep)
        Hud.topBar(p, state, "ปรับแต่งยาน")
        val pad = s(14f); val top = s(56f)
        btnBack.set(vw - s(84f), s(8f), s(74f), s(32f)); btnBack.draw(p)

        drawFleetColumn(p, pad, top)
        drawShipDetail(p, pad, top)
        drawInventoryColumn(p, pad, top)
    }

    private fun drawFleetColumn(p: Painter, pad: Float, top: Float) {
        val w = vw * 0.26f
        Ui.panel(p, pad, top, w, vh - top - pad, "กองยาน")
        fleetButtons.clear()
        var y = top + s(46f)
        for (ship in state.fleet) {
            val b = Button(pad + s(10f), y, w - s(20f), s(46f), ship.name)
            b.draw(p, primary = ship == selShip)
            fleetButtons.add(ship to b)
            y += s(54f)
            if (y > vh - s(50f)) break
        }
    }

    private fun drawShipDetail(p: Painter, pad: Float, top: Float) {
        val x = pad * 2 + vw * 0.26f
        val w = vw * 0.40f
        Ui.panel(p, x, top, w, vh - top - pad, null)
        slotButtons.clear()
        val ship = selShip ?: run {
            p.text("เลือกยานทางซ้าย", x + s(16f), top + s(40f), s(15f), Palette.textDim); return
        }
        var y = top + s(30f)
        p.text("${ship.name} · ${ship.def.cls.displayName}", x + s(14f), y, s(17f), Palette.accent, bold = true)
        y += s(24f)
        p.text("HP ${ship.maxHull.toInt()} · โล่ ${ship.maxShield.toInt()} · เร็ว ${ship.speed.toInt()} · DPS ${ship.dps.toInt()}",
            x + s(14f), y, s(12.5f), Palette.textPrimary)
        y += s(18f)
        val caps = buildString {
            if (ship.fighterCapacity > 0) append("ฝูงบิน ${ship.fighterCapacity}  ")
            if (ship.troopCapacity > 0) append("พลรบ ${ship.troopCapacity}")
        }
        if (caps.isNotBlank()) { p.text(caps, x + s(14f), y, s(12.5f), Palette.accentWarm); y += s(18f) }
        y += s(8f)

        p.text("ช่องอาวุธ (Hardpoints)", x + s(14f), y, s(13f), Palette.textDim, bold = true); y += s(10f)
        for (i in ship.hardpoints.indices) {
            val id = ship.hardpoints[i]
            val name = id?.let { Catalog.weapon(it)?.name } ?: "— ว่าง —"
            val b = Button(x + s(14f), y, w - s(28f), s(38f), "อาวุธ ${i + 1}: $name")
            val selected = slotType == 0 && slotIndex == i
            b.accent = if (id != null) Palette.accent else Palette.strokeSoft
            b.draw(p, primary = selected)
            slotButtons.add(Triple(0, i, b))
            y += s(44f)
        }
        y += s(8f)
        p.text("ช่องอุปกรณ์ (Modules)", x + s(14f), y, s(13f), Palette.textDim, bold = true); y += s(10f)
        for (i in ship.modules.indices) {
            val id = ship.modules[i]
            val name = id?.let { Catalog.module(it)?.name } ?: "— ว่าง —"
            val b = Button(x + s(14f), y, w - s(28f), s(38f), "อุปกรณ์ ${i + 1}: $name")
            val selected = slotType == 1 && slotIndex == i
            b.accent = if (id != null) Palette.good else Palette.strokeSoft
            b.draw(p, primary = selected)
            slotButtons.add(Triple(1, i, b))
            y += s(44f)
        }
    }

    private fun drawInventoryColumn(p: Painter, pad: Float, top: Float) {
        val x = pad * 3 + vw * 0.26f + vw * 0.40f
        val w = vw - x - pad
        Ui.panel(p, x, top, w, vh - top - pad, "คลังอุปกรณ์")
        invButtons.clear()
        val ship = selShip ?: return
        var y = top + s(50f)

        if (slotType < 0) {
            p.text("แตะช่องอาวุธหรืออุปกรณ์", x + s(14f), y, s(13f), Palette.textDim)
            p.text("เพื่อเลือกติดตั้ง", x + s(14f), y + s(20f), s(13f), Palette.textDim)
            return
        }
        // Unequip option
        val ub = Button(x + s(12f), y, w - s(24f), s(34f), "ถอดออก (คืนคลัง)", accent = Palette.warn)
        ub.draw(p)
        invButtons.add({
            if (slotType == 0) state.fitWeapon(ship, slotIndex, null)
            else state.fitModule(ship, slotIndex, null)
            game.persist()
        } to ub)
        y += s(42f)

        if (slotType == 0) {
            for (wd in Catalog.weapons) {
                val stock = state.weaponStock(wd.id)
                if (stock <= 0) continue
                val b = Button(x + s(12f), y, w - s(24f), s(38f), "${wd.name} x$stock")
                b.draw(p)
                invButtons.add({
                    if (state.fitWeapon(ship, slotIndex, wd.id)) { game.toast("ติดตั้ง ${wd.name}"); game.persist() }
                } to b)
                y += s(44f)
                if (y > vh - s(46f)) break
            }
        } else {
            for (md in Catalog.modules) {
                val stock = state.moduleStock(md.id)
                if (stock <= 0) continue
                val b = Button(x + s(12f), y, w - s(24f), s(38f), "${md.name} x$stock")
                b.draw(p)
                invButtons.add({
                    if (state.fitModule(ship, slotIndex, md.id)) { game.toast("ติดตั้ง ${md.name}"); game.persist() }
                } to b)
                y += s(44f)
                if (y > vh - s(46f)) break
            }
        }
        if (invButtons.size <= 1)
            p.text("ไม่มีของในคลัง — ซื้อที่อู่ต่อยาน", x + s(14f), y + s(6f), s(12f), Palette.textMuted)
    }

    override fun onTap(x: Float, y: Float) {
        if (btnBack.hit(x, y)) { game.pop(); return }
        for ((ship, b) in fleetButtons) if (b.hit(x, y)) {
            selShip = ship; slotType = -1; slotIndex = -1; return
        }
        for ((type, idx, b) in slotButtons) if (b.hit(x, y)) {
            slotType = type; slotIndex = idx; return
        }
        for ((action, b) in invButtons) if (b.hit(x, y)) { action(); return }
    }

    override fun onBack(): Boolean { game.pop(); return true }
}
