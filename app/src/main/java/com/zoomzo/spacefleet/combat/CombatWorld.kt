package com.zoomzo.spacefleet.combat

import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.model.Catalog
import com.zoomzo.spacefleet.model.Faction
import com.zoomzo.spacefleet.model.Ship
import com.zoomzo.spacefleet.model.ShipClass
import com.zoomzo.spacefleet.model.StarSystem
import com.zoomzo.spacefleet.model.WeaponDef
import com.zoomzo.spacefleet.model.WeaponType
import com.zoomzo.spacefleet.util.MathUtil
import com.zoomzo.spacefleet.util.Vec2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class BattleResult { RUNNING, PLAYER_WIN, PLAYER_LOSS }

/** Short-lived visual line (flak tracer, railgun beam). */
class Tracer(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int) {
    var life = 0.12f
}

/**
 * The RTS battle simulation. Holds all combatants, steps the physics/AI each
 * frame, resolves damage, and reports the outcome plus spoils.
 */
class CombatWorld(
    playerFleet: List<Ship>,
    private val system: StarSystem,
    /** true when the objective includes assaulting/capturing the system station. */
    val isInvasion: Boolean
) {
    val worldW = 2600f
    val worldH = 1500f

    val ships = mutableListOf<CombatShip>()
    val projectiles = mutableListOf<Projectile>()
    val fighters = mutableListOf<Fighter>()
    val tracers = mutableListOf<Tracer>()

    var station: CombatShip? = null
    var stationCaptured = false
    var stationDestroyed = false
    var salvageCredits = 0
    private var enemyFaction: Faction = if (system.owner == Faction.PIRATE) Faction.PIRATE else Faction.ENEMY

    init {
        // Player ships along the left, spread vertically.
        val n = playerFleet.size.coerceAtLeast(1)
        playerFleet.forEachIndexed { i, ship ->
            val cs = buildPlayer(ship)
            cs.pos.set(worldW * 0.16f, worldH * (0.5f + (i - (n - 1) / 2f) * 0.12f))
            cs.angle = 0f
            ships.add(cs)
        }

        // Enemy patrol on the right.
        val enemyDefs = system.patrolFleet + if (isInvasion) system.garrisonFleet else emptyList()
        val m = enemyDefs.size.coerceAtLeast(1)
        enemyDefs.forEachIndexed { i, defId ->
            val cs = buildEnemy(defId)
            cs.pos.set(worldW * 0.82f, worldH * (0.5f + (i - (m - 1) / 2f) * 0.11f))
            cs.angle = MathUtil.TAU / 2f
            ships.add(cs)
        }

        // Station objective.
        if (isInvasion && system.hasStation) {
            val st = buildStation(system)
            st.pos.set(worldW * 0.9f, worldH * 0.5f)
            station = st
            ships.add(st)
        }
    }

    // ---------------- Construction ----------------

    private fun buildPlayer(ship: Ship): CombatShip {
        val cs = CombatShip(0, ship.defId, ship.def.cls, ship.name, Palette.player, ship)
        cs.maxHull = ship.maxHull
        cs.hull = ship.currentHull.coerceAtLeast(ship.maxHull * 0.05f)
        cs.maxShield = ship.maxShield
        cs.shield = ship.maxShield
        cs.shieldRegen = ship.shieldRegen
        cs.speed = ship.speed
        cs.turnRate = ship.turnRate
        cs.size = ship.def.size
        cs.repairRate = ship.repairRate
        cs.fighterCapacity = ship.fighterCapacity
        cs.troopCapacity = ship.troopCapacity
        ship.weaponDefs().forEach { cs.mounts.add(WeaponMount(it)) }
        // A ship with no weapons still gets a weak defensive laser so it can fight back.
        if (cs.mounts.isEmpty() && cs.cls != ShipClass.TRANSPORT)
            cs.mounts.add(WeaponMount(Catalog.weapon("laser_mk1")!!))
        return cs
    }

    private fun buildEnemy(defId: String): CombatShip {
        val def = Catalog.ship(defId)
        val color = enemyFaction.color
        val cs = CombatShip(1, defId, def.cls, def.name, color, null)
        val fit = enemyFit(def.cls)
        cs.maxHull = def.hull + fit.hullBonus
        cs.hull = cs.maxHull
        cs.maxShield = fit.shield
        cs.shield = fit.shield
        cs.shieldRegen = fit.shieldRegen
        cs.speed = def.speed
        cs.turnRate = def.turn
        cs.size = def.size
        cs.fighterCapacity = def.fighterBays
        fit.weapons.forEach { cs.mounts.add(WeaponMount(it)) }
        return cs
    }

    private fun buildStation(sys: StarSystem): CombatShip {
        val cs = CombatShip(1, "station", ShipClass.BATTLESHIP, "สถานีอวกาศ",
            enemyFaction.color, null)
        cs.isStation = true
        cs.maxHull = (sys.stationHealth * 2.2f)
        cs.hull = cs.maxHull
        cs.maxShield = sys.stationHealth * 1.1f
        cs.shield = cs.maxShield
        cs.shieldRegen = 10f
        cs.speed = 0f
        cs.turnRate = 0.4f
        cs.size = 60f
        cs.stationTroops = sys.stationTroops.coerceAtLeast(1)
        val heavy = Catalog.weapon("plasma")!!
        val pd = Catalog.weapon("flak")!!
        val rail = Catalog.weapon("railgun")!!
        repeat(2 + sys.danger / 2) { cs.mounts.add(WeaponMount(heavy)) }
        repeat(2) { cs.mounts.add(WeaponMount(pd)) }
        cs.mounts.add(WeaponMount(rail))
        return cs
    }

    private class Fit(
        val shield: Float, val shieldRegen: Float,
        val weapons: List<WeaponDef>, val hullBonus: Float
    )

    private fun w(id: String) = Catalog.weapon(id)!!

    private fun enemyFit(cls: ShipClass): Fit = when (cls) {
        ShipClass.SCOUT -> Fit(30f, 6f, listOf(w("laser_mk1")), 0f)
        ShipClass.INTERCEPTOR -> Fit(20f, 5f, listOf(w("autocannon"), w("autocannon")), 0f)
        ShipClass.FRIGATE -> Fit(90f, 12f, listOf(w("laser_mk2"), w("autocannon"), w("flak")), 40f)
        ShipClass.DESTROYER -> Fit(180f, 16f, listOf(w("laser_mk2"), w("plasma"), w("railgun"), w("flak")), 80f)
        ShipClass.BATTLESHIP -> Fit(320f, 24f,
            listOf(w("railgun"), w("railgun"), w("plasma"), w("plasma"), w("missile"), w("flak")), 200f)
        ShipClass.CARRIER -> Fit(200f, 18f, listOf(w("flak"), w("flak"), w("laser_mk1")), 60f)
        ShipClass.TRANSPORT -> Fit(80f, 8f, listOf(w("autocannon"), w("autocannon")), 40f)
    }

    // ---------------- Simulation ----------------

    fun update(dt: Float) {
        for (s in ships) {
            if (!s.alive) continue
            s.shieldFlash = (s.shieldFlash - dt).coerceAtLeast(0f)
            s.hullFlash = (s.hullFlash - dt).coerceAtLeast(0f)
            if (s.shield < s.maxShield) s.shield = (s.shield + s.shieldRegen * dt).coerceAtMost(s.maxShield)
            if (s.repairRate > 0f && s.hull < s.maxHull)
                s.hull = (s.hull + s.repairRate * dt).coerceAtMost(s.maxHull)
            updateAi(s, dt)
            steer(s, dt)
            fireWeapons(s, dt)
            if (s.fighterCapacity > 0) updateCarrier(s, dt)
        }
        updateProjectiles(dt)
        updateFighters(dt)
        updateBoarding(dt)
        val ti = tracers.iterator()
        while (ti.hasNext()) { val t = ti.next(); t.life -= dt; if (t.life <= 0f) ti.remove() }
        // Remove dead hostiles; keep dead player hulls so results can be written back.
        ships.removeAll { !it.alive && it.team == 1 && !it.isStation }
    }

    private fun nearestHostile(s: CombatShip): CombatShip? {
        var best: CombatShip? = null
        var bestD = Float.MAX_VALUE
        for (o in ships) {
            if (!o.alive || o.team == s.team) continue
            val d = Vec2.distSq(s.pos.x, s.pos.y, o.pos.x, o.pos.y)
            if (d < bestD) { bestD = d; best = o }
        }
        return best
    }

    private fun updateAi(s: CombatShip, dt: Float) {
        // Drop dead/captured targets.
        s.attackTarget?.let { if (!it.alive || it.team == s.team) s.attackTarget = null }
        if (s.isStation) { if (s.attackTarget == null) s.attackTarget = nearestHostile(s); return }

        // Transports prefer to run at the station; others auto-engage.
        if (s.team == 0 && s.moveGoal != null) {
            // Player issued a move order: still auto-fire but chase only if also given a target.
        } else if (s.attackTarget == null) {
            s.attackTarget = nearestHostile(s)
        }
    }

    private fun steer(s: CombatShip, dt: Float) {
        if (s.isStation || s.speed <= 0f) return

        val goal = Vec2()
        val target = s.attackTarget
        val move = s.moveGoal

        var haveGoal = false
        if (move != null && (target == null || s.team == 0)) {
            goal.set(move)
            haveGoal = true
            if (Vec2.dist(s.pos.x, s.pos.y, move.x, move.y) < s.size + 12f) {
                s.moveGoal = null
                haveGoal = false
            }
        }
        if (!haveGoal && target != null) {
            // Approach to just inside our shortest weapon range (standoff).
            val standoff = (s.minEngageRange * 0.8f).coerceAtLeast(80f)
            val dx = s.pos.x - target.pos.x
            val dy = s.pos.y - target.pos.y
            val d = Vec2.dist(0f, 0f, dx, dy).coerceAtLeast(1f)
            goal.set(target.pos.x + dx / d * standoff, target.pos.y + dy / d * standoff)
            haveGoal = true
        }
        if (!haveGoal) { s.vel.scale(0.9f); s.pos.addScaled(s.vel, dt); return }

        val desired = atan2(goal.y - s.pos.y, goal.x - s.pos.x)
        s.angle = MathUtil.turnToward(s.angle, desired, s.turnRate * dt)
        val dist = Vec2.dist(s.pos.x, s.pos.y, goal.x, goal.y)
        val throttle = (dist / 120f).coerceIn(0f, 1f)
        val vx = cos(s.angle) * s.speed * throttle
        val vy = sin(s.angle) * s.speed * throttle
        s.vel.set(vx, vy)
        s.pos.addScaled(s.vel, dt)
        s.pos.x = MathUtil.clamp(s.pos.x, 20f, worldW - 20f)
        s.pos.y = MathUtil.clamp(s.pos.y, 20f, worldH - 20f)
    }

    private fun fireWeapons(s: CombatShip, dt: Float) {
        val target = s.attackTarget ?: nearestHostile(s) ?: return
        for (mount in s.mounts) {
            mount.cooldown -= dt
            if (mount.cooldown > 0f) continue
            val def = mount.def
            if (def.type == WeaponType.FLAK) {
                if (tryPointDefense(s, mount)) { mount.cooldown = mount.interval; continue }
            }
            val d = Vec2.dist(s.pos.x, s.pos.y, target.pos.x, target.pos.y)
            if (d > def.range) continue
            fireAt(s, def, target)
            mount.cooldown = mount.interval
        }
    }

    /** FLAK first tries to shoot down an incoming enemy missile. */
    private fun tryPointDefense(s: CombatShip, mount: WeaponMount): Boolean {
        var best: Projectile? = null
        var bestD = mount.def.range * mount.def.range
        for (p in projectiles) {
            if (!p.alive || p.team == s.team || !p.interceptable) continue
            val d = Vec2.distSq(s.pos.x, s.pos.y, p.pos.x, p.pos.y)
            if (d < bestD) { bestD = d; best = p }
        }
        val m = best ?: return false
        m.hp -= mount.def.damage
        tracers.add(Tracer(s.pos.x, s.pos.y, m.pos.x, m.pos.y, Palette.accentWarm))
        if (m.hp <= 0f) m.alive = false
        return true
    }

    private fun fireAt(s: CombatShip, def: WeaponDef, target: CombatShip) {
        // Lead the target based on projectile travel time.
        val dist = Vec2.dist(s.pos.x, s.pos.y, target.pos.x, target.pos.y)
        val t = dist / def.projSpeed
        val px = target.pos.x + target.vel.x * t
        val py = target.pos.y + target.vel.y * t
        val ang = atan2(py - s.pos.y, px - s.pos.x)
        val vel = Vec2(cos(ang) * def.projSpeed, sin(ang) * def.projSpeed)
        val color = when (def.type) {
            WeaponType.LASER -> Palette.accent
            WeaponType.PLASMA -> Palette.pirate
            WeaponType.RAILGUN -> Palette.textPrimary
            WeaponType.MISSILE -> Palette.warn
            WeaponType.AUTOCANNON -> Palette.accentWarm
            WeaponType.FLAK -> Palette.accentWarm
        }
        val proj = Projectile(
            team = s.team,
            pos = Vec2(s.pos.x + cos(s.angle) * s.size, s.pos.y + sin(s.angle) * s.size),
            vel = vel, damage = def.damage, range = def.range, speed = def.projSpeed,
            type = def.type, shieldFactor = def.shieldFactor, armorFactor = def.armorFactor,
            target = if (def.tracking > 0f) target else null, tracking = def.tracking, color = color
        )
        projectiles.add(proj)
        if (def.type == WeaponType.RAILGUN)
            tracers.add(Tracer(s.pos.x, s.pos.y, px, py, Palette.withAlpha(Palette.textPrimary, 120)))
    }

    private fun updateCarrier(s: CombatShip, dt: Float) {
        s.fighterLaunchCd -= dt
        if (s.activeFighters >= s.fighterCapacity * 3) return // 3 craft per bay
        if (s.fighterLaunchCd > 0f) return
        if (nearestHostile(s) == null) return
        val f = Fighter(s.team, if (s.team == 0) Palette.player else s.color, s)
        f.pos.set(s.pos.x, s.pos.y)
        f.angle = s.angle
        fighters.add(f)
        s.activeFighters++
        s.fighterLaunchCd = 1.4f
    }

    private fun updateProjectiles(dt: Float) {
        val it = projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (!p.alive) { it.remove(); continue }
            // Homing.
            if (p.target != null && p.target!!.alive && p.tracking > 0f) {
                val desired = atan2(p.target!!.pos.y - p.pos.y, p.target!!.pos.x - p.pos.x)
                val cur = atan2(p.vel.y, p.vel.x)
                val na = MathUtil.turnToward(cur, desired, p.tracking * dt)
                p.vel.set(cos(na) * p.speed, sin(na) * p.speed)
            }
            p.pos.addScaled(p.vel, dt)
            p.traveled += p.speed * dt
            if (p.traveled > p.range * 1.3f) { it.remove(); continue }

            var hit = false
            for (s in ships) {
                if (!s.alive || s.team == p.team) continue
                val rr = s.size + p.radius
                if (Vec2.distSq(p.pos.x, p.pos.y, s.pos.x, s.pos.y) <= rr * rr) {
                    s.takeDamage(p.damage, p.shieldFactor, p.armorFactor)
                    onShipMaybeKilled(s)
                    hit = true
                    break
                }
            }
            if (hit) it.remove()
        }
    }

    private fun updateFighters(dt: Float) {
        val it = fighters.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.lifetime += dt
            if (!f.alive) { f.parent.activeFighters = (f.parent.activeFighters - 1).coerceAtLeast(0); it.remove(); continue }
            if (f.target == null || !f.target!!.alive) f.target = nearestHostileFor(f)
            val tgt = f.target
            if (tgt != null) {
                val desired = atan2(tgt.pos.y - f.pos.y, tgt.pos.x - f.pos.x)
                f.angle = MathUtil.turnToward(f.angle, desired, 5f * dt)
                f.pos.x += cos(f.angle) * f.speed * dt
                f.pos.y += sin(f.angle) * f.speed * dt
                f.fireCd -= dt
                val d = Vec2.dist(f.pos.x, f.pos.y, tgt.pos.x, tgt.pos.y)
                if (d < 360f && f.fireCd <= 0f) {
                    val ang = atan2(tgt.pos.y - f.pos.y, tgt.pos.x - f.pos.x)
                    projectiles.add(Projectile(f.team,
                        Vec2(f.pos.x, f.pos.y),
                        Vec2(cos(ang) * 800f, sin(ang) * 800f),
                        5f, 380f, 800f, WeaponType.AUTOCANNON, 1f, 1f, null, 0f,
                        if (f.team == 0) Palette.player else f.color))
                    f.fireCd = 0.5f
                }
            }
            // Fighters can be caught by enemy fire (handled as ships? they aren't ships) —
            // simple attrition so battles end: expire after a while.
            if (f.lifetime > 26f) f.alive = false
        }
    }

    private fun nearestHostileFor(f: Fighter): CombatShip? {
        var best: CombatShip? = null; var bestD = Float.MAX_VALUE
        for (o in ships) {
            if (!o.alive || o.team == f.team) continue
            val d = Vec2.distSq(f.pos.x, f.pos.y, o.pos.x, o.pos.y)
            if (d < bestD) { bestD = d; best = o }
        }
        return best
    }

    private fun updateBoarding(dt: Float) {
        val st = station ?: return
        if (!st.alive || st.team == 0) return
        // Destruction path.
        if (st.hull <= 0f) {
            st.alive = false; stationDestroyed = true
            salvageCredits += 600
            return
        }
        // Capture path: a player transport docked while defenses are down.
        if (st.shield > 0f || st.hullFraction > 0.4f) return
        var assault = 0
        for (s in ships) {
            if (s.team != 0 || !s.alive || s.troopCapacity <= 0) continue
            if (Vec2.dist(s.pos.x, s.pos.y, st.pos.x, st.pos.y) < st.size + 150f)
                assault += s.troopCapacity
        }
        if (assault > 0) {
            stationTroopFloat -= dt * assault * 0.7f
            st.stationTroops = kotlin.math.ceil(stationTroopFloat.coerceAtLeast(0f)).toInt()
            if (stationTroopFloat <= 0f) {
                st.team = 0; stationCaptured = true
                st.attackTarget = null
                salvageCredits += 400
            }
        }
    }

    private var stationTroopFloat: Float = (station?.stationTroops ?: 0).toFloat()

    private fun onShipMaybeKilled(s: CombatShip) {
        if (!s.alive && s.team == 1 && !s.isStation) {
            salvageCredits += (Catalog.shipOrNull(s.defId)?.cost ?: 400) / 10
        }
    }

    // ---------------- Player orders ----------------

    fun selectAt(wx: Float, wy: Float): Boolean {
        var picked: CombatShip? = null
        var bestD = Float.MAX_VALUE
        for (s in ships) {
            if (s.team != 0 || !s.alive) continue
            val d = Vec2.distSq(wx, wy, s.pos.x, s.pos.y)
            val r = (s.size + 30f) * (s.size + 30f)
            if (d < r && d < bestD) { bestD = d; picked = s }
        }
        if (picked != null) {
            ships.forEach { it.selected = false }
            picked.selected = true
            return true
        }
        return false
    }

    fun selectAll() {
        ships.forEach { if (it.team == 0 && it.alive) it.selected = true }
    }

    fun clearSelection() { ships.forEach { it.selected = false } }

    val selectedCount: Int get() = ships.count { it.selected && it.team == 0 && it.alive }

    /** Issue an order at a world point for all selected ships. */
    fun orderAt(wx: Float, wy: Float) {
        // Enemy under the point? -> attack it. Else -> move.
        var enemy: CombatShip? = null
        var bestD = Float.MAX_VALUE
        for (s in ships) {
            if (s.team != 1 || !s.alive) continue
            val d = Vec2.distSq(wx, wy, s.pos.x, s.pos.y)
            val r = (s.size + 30f) * (s.size + 30f)
            if (d < r && d < bestD) { bestD = d; enemy = s }
        }
        for (s in ships) {
            if (!s.selected || s.team != 0 || !s.alive) continue
            if (enemy != null) { s.attackTarget = enemy; s.moveGoal = null }
            else { s.moveGoal = Vec2(wx, wy); s.attackTarget = null }
        }
    }

    fun setAutoEngageAll() {
        for (s in ships) if (s.team == 0 && s.alive) { s.moveGoal = null; s.attackTarget = null }
    }

    // ---------------- Outcome ----------------

    fun playerShipsAlive(): Int = ships.count { it.team == 0 && it.alive && !it.isStation }
    fun hostilesRemain(): Boolean = ships.any { it.team == 1 && it.alive }

    fun result(): BattleResult = when {
        playerShipsAlive() == 0 -> BattleResult.PLAYER_LOSS
        !hostilesRemain() -> BattleResult.PLAYER_WIN
        else -> BattleResult.RUNNING
    }

    /** Player ships still alive, for writing damage back to the fleet. */
    fun survivingPlayerShips(): List<CombatShip> = ships.filter { it.team == 0 && it.source != null }
}
