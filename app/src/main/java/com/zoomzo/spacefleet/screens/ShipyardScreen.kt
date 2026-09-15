package com.zoomzo.spacefleet.screens

import android.graphics.Paint
import com.zoomzo.spacefleet.engine.Button
import com.zoomzo.spacefleet.engine.Game
import com.zoomzo.spacefleet.engine.Painter
import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.engine.Screen
import com.zoomzo.spacefleet.model.Catalog
import com.zoomzo.spacefleet.model.Ship

/** Market: buy hulls, weapons and modules; sell ships from the fleet. */
class ShipyardScreen(game: Game) : Screen(game) {

    private val state get() = game.state
    private enum class Tab(val label: String) {
        SHIPS("ยานรบ"), WEAPONS("อาวุธ"), MODULES("อุปกรณ์"), FLEET("ขายยาน")
    }
    private var tab = Tab.SHIPS
    private val scroll = FloatArray(Tab.values().size)

    private val tabButtons = Array(Tab.values().size) { Button() }
    private val btnBack = Button(label = "ออก")
    private val actions = mutableListOf<Pair<() -> Unit, Button>>()

    override fun update(dt: Float) {}

    override fun draw(p: Painter) {
        sync(p)
        p.clear(Palette.bgDeep)
        Hud.topBar(p, state, "อู่ต่อยาน")

        // Tab bar
        val top = s(52f); val th = s(40f); val pad = s(12f)
        val tw = (vw - pad * 2 - s(90f)) / Tab.values().size
        Tab.values().forEachIndexed { i, tb ->
            val b = tabButtons[i].set(pad + i * tw, top, tw - s(6f), th)
            b.label = tb.label
            b.draw(p, primary = tb == tab)
        }
        btnBack.set(vw - s(84f), top, s(74f), th); btnBack.draw(p)

        val listTop = top + th + s(10f)
        val listBottom = vh - s(10f)
        p.save()
        p.clipRect(0f, listTop, vw, listBottom - listTop)
        actions.clear()
        when (tab) {
            Tab.SHIPS -> drawShips(p, listTop)
            Tab.WEAPONS -> drawWeapons(p, listTop)
            Tab.MODULES -> drawModules(p, listTop)
            Tab.FLEET -> drawFleet(p, listTop)
        }
        p.restore()
    }

    private fun card(p: Painter, y: Float, h: Float): Float {
        val x = s(14f); val w = vw - s(28f)
        p.fillRound(x, y, w, h, s(9f), Palette.bgPanel)
        p.strokeRound(x, y, w, h, s(9f), Palette.strokeSoft, s(1f))
        return w
    }

    private fun drawShips(p: Painter, listTop: Float) {
        var y = listTop - scroll[tab.ordinal] + s(6f)
        val h = s(96f)
        for (def in Catalog.ships) {
            val w = card(p, y, h)
            p.text("${def.name}  ·  ${def.cls.displayName}", s(26f), y + s(24f), s(16f), Palette.accent, bold = true)
            p.text(def.desc, s(26f), y + s(44f), s(11.5f), Palette.textDim)
            p.text("HP ${def.hull.toInt()} · เร็ว ${def.speed.toInt()} · ปืน ${def.hardpoints} · ช่อง ${def.moduleSlots}",
                s(26f), y + s(64f), s(12f), Palette.textPrimary)
            val extra = buildString {
                if (def.fighterBays > 0) append("โรงบิน ${def.fighterBays} ")
                if (def.troopBase > 0) append("พลรบ ${def.troopBase}")
            }
            if (extra.isNotBlank()) p.text(extra, s(26f), y + s(82f), s(12f), Palette.accentWarm)

            val b = Button(vw - s(150f), y + h - s(40f), s(120f), s(30f), "ซื้อ ₡${def.cost}")
            b.enabled = state.canAfford(def.cost)
            b.draw(p, primary = b.enabled)
            actions.add({ buyShip(def.id) } to b)
            y += h + s(10f)
        }
        clampScroll(y, listTop)
    }

    private fun drawWeapons(p: Painter, listTop: Float) {
        var y = listTop - scroll[tab.ordinal] + s(6f)
        val h = s(74f)
        for (wd in Catalog.weapons) {
            card(p, y, h)
            p.text(wd.name, s(26f), y + s(24f), s(16f), Palette.accent, bold = true)
            p.text("ชนิด ${wd.type.displayName} · ดาเมจ ${wd.damage.toInt()} · ระยะ ${wd.range.toInt()} · DPS ${wd.dps.toInt()}",
                s(26f), y + s(46f), s(12f), Palette.textPrimary)
            p.text("มีในคลัง: ${state.weaponStock(wd.id)}", s(26f), y + s(64f), s(11.5f), Palette.textDim)
            val b = Button(vw - s(150f), y + h - s(38f), s(120f), s(28f), "ซื้อ ₡${wd.cost}")
            b.enabled = state.canAfford(wd.cost)
            b.draw(p, primary = b.enabled)
            actions.add({ if (state.buyWeapon(wd.id)) { game.toast("ซื้อ ${wd.name}"); game.persist() } } to b)
            y += h + s(10f)
        }
        clampScroll(y, listTop)
    }

    private fun drawModules(p: Painter, listTop: Float) {
        var y = listTop - scroll[tab.ordinal] + s(6f)
        val h = s(74f)
        for (md in Catalog.modules) {
            card(p, y, h)
            p.text(md.name, s(26f), y + s(24f), s(16f), Palette.accent, bold = true)
            val stats = buildString {
                if (md.hull > 0) append("HP+${md.hull.toInt()} ")
                if (md.shield > 0) append("โล่+${md.shield.toInt()} ")
                if (md.shieldRegen > 0) append("รีเจน+${md.shieldRegen.toInt()} ")
                if (md.speed > 0) append("เร็ว+${md.speed.toInt()} ")
                if (md.fighterCap > 0) append("โรงบิน+${md.fighterCap} ")
                if (md.troopCap > 0) append("พลรบ+${md.troopCap} ")
                if (md.repairRate > 0) append("ซ่อม+${md.repairRate.toInt()} ")
                if (md.sensorBonus > 0) append("เซนเซอร์+${md.sensorBonus.toInt()} ")
            }
            p.text(stats.ifBlank { md.type.displayName }, s(26f), y + s(46f), s(12f), Palette.textPrimary)
            p.text("มีในคลัง: ${state.moduleStock(md.id)}", s(26f), y + s(64f), s(11.5f), Palette.textDim)
            val b = Button(vw - s(150f), y + h - s(38f), s(120f), s(28f), "ซื้อ ₡${md.cost}")
            b.enabled = state.canAfford(md.cost)
            b.draw(p, primary = b.enabled)
            actions.add({ if (state.buyModule(md.id)) { game.toast("ซื้อ ${md.name}"); game.persist() } } to b)
            y += h + s(10f)
        }
        clampScroll(y, listTop)
    }

    private fun drawFleet(p: Painter, listTop: Float) {
        var y = listTop - scroll[tab.ordinal] + s(6f)
        val h = s(74f)
        for (ship in state.fleet.toList()) {
            card(p, y, h)
            p.text("${ship.name}  ·  ${ship.def.cls.displayName}", s(26f), y + s(24f), s(15f), Palette.accent, bold = true)
            p.text("HP ${ship.maxHull.toInt()} · โล่ ${ship.maxShield.toInt()} · DPS ${ship.dps.toInt()}",
                s(26f), y + s(46f), s(12f), Palette.textPrimary)
            p.text("สภาพ ${(ship.hullFraction * 100).toInt()}%", s(26f), y + s(64f), s(11.5f),
                if (ship.hullFraction < 0.6f) Palette.warn else Palette.textDim)
            val refund = (ship.value * 0.6f).toInt()
            val b = Button(vw - s(150f), y + h - s(38f), s(120f), s(28f), "ขาย ₡$refund", accent = Palette.warn)
            b.enabled = state.fleet.size > 1
            b.draw(p)
            actions.add({
                if (state.fleet.size > 1) {
                    val r = state.sellShip(ship); game.toast("ขายยาน (+₡$r)"); game.persist()
                } else game.toast("ต้องมียานอย่างน้อย 1 ลำ")
            } to b)
            y += h + s(10f)
        }
        clampScroll(y, listTop)
    }

    private fun clampScroll(contentBottom: Float, listTop: Float) {
        val contentH = contentBottom + scroll[tab.ordinal] - listTop
        val maxScroll = (contentH - (vh - listTop - s(10f))).coerceAtLeast(0f)
        scroll[tab.ordinal] = scroll[tab.ordinal].coerceIn(0f, maxScroll)
    }

    private fun buyShip(defId: String) {
        val s = state.buyShip(defId)
        if (s != null) { game.toast("ต่อยานใหม่: ${s.name}"); game.persist() }
        else game.toast("เครดิตไม่พอ")
    }

    override fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {
        scroll[tab.ordinal] -= dy
    }

    override fun onTap(x: Float, y: Float) {
        if (btnBack.hit(x, y)) { game.pop(); return }
        tabButtons.forEachIndexed { i, b ->
            if (b.hit(x, y)) { tab = Tab.values()[i]; return }
        }
        for ((action, b) in actions) if (b.hit(x, y)) { action(); return }
    }

    override fun onBack(): Boolean { game.pop(); return true }
}
