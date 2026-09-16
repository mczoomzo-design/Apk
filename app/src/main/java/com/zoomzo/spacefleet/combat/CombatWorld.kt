package com.zoomzo.spacefleet.combat

import com.zoomzo.spacefleet.engine.Palette
import com.zoomzo.spacefleet.model.Catalog
import com.zoomzo.spacefleet.model.Difficulty
import com.zoomzo.spacefleet.model.Faction
import com.zoomzo.spacefleet.model.Formation
import com.zoomzo.spacefleet.model.Ship
import com.zoomzo.spacefleet.model.ShipClass
import com.zoomzo.spacefleet.model.StarSystem
import com.zoomzo.spacefleet.model.TechBonus
import com.zoomzo.spacefleet.model.WeaponDef
import com.zoomzo.spacefleet.model.WeaponType
import com.zoomzo.spacefleet.util.MathUtil
import com.zoomzo.spacefleet.util.Vec2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class BattleResult { RUNNING, PLAYER_WIN, PLAYER_LOSS }

/** Short-lived visual line (flak tracer, railgun beam, warp flash). */
class Tracer(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: Int) {
    var life = 0.12f
}

/**
 * A hostile reinforcement wave that warps in mid-battle. While [timer] is below
 * [lead] a warp portal is telegraphed at [pos]; when it hits zero the ships emerge.
 */
class WarpWave(
    var timer: Float,
    val lead: Float,
    val pos: Vec2,
    val defIds: List<String>
) {
    val portalVisible: Boolean get() = timer <= lead
    /** 0..1 growth of the portal telegraph. */
    val progress: Float get() = if (!portalVisible) 0f else (1f - (timer / lead)).coerceIn(0f, 1f)
}

/**
 * The RTS battle simulation with Battlevoid-style warp-in ambushes, applied
 * player technology bonuses and fleet formations.
 */
class CombatWorld(
    playerFleet: List<Ship>,
    private val system: StarSystem,
    val isInvasion: Boolean,
    private val tech: TechBonus,
    private val formation: Formation,
    private val ambush: Boolean,
    private val difficulty: Difficulty = Difficulty.NORMAL,
    private val rng: Random = Random(System.nanoTime())
) {
    val worldW = 2600f
    val worldH = 1500f

    val ships = mutableListOf<CombatShip>()
    val projectiles = mutableListOf<Projectile>()
    val fighters = mutableListOf<Fighter>()
    val tracers = mutableListOf<Tracer>()
    val waves = mutableListOf<WarpWave>()
    val particles = mutableListOf<Particle>()

    var station: CombatShip? = null
    var stationCaptured = false
    var stationDestroyed = false
    var salvageCredits = 0
    var researchReward = 0
    var lastWarpWarning = 0f      // seconds since a portal opened (for UI banner)
    private var stationTroopFloat = 0f

    private val enemyFaction: Faction =
        if (system.owner == Faction.PIRATE) Faction.PIRATE else Faction.ENEMY
    private val enemyDmgMult = (1f + 0.06f * system.danger) * difficulty.enemyDmgMult

    init {
        // Player ships in formation on the left.
        placeFormation(playerFleet)

        // Enemy patrol on the right.
        val enemyDefs = system.patrolFleet + if (isInvasion) system.garrisonFleet else emptyList()
        val m = enemyDefs.size.coerceAtLeast(1)
        enemyDefs.forEachIndexed { i, defId ->
            val cs = buildEnemy(defId)
            cs.pos.set(worldW * 0.82f, worldH * (0.5f + (i - (m - 1) / 2f) * 0.10f))
            cs.angle = MathUtil.TAU / 2f
            ships.add(cs)
        }

        if ((isInvasion || system.isBoss) && system.hasStation) {
            val st = buildStation(system)
            st.pos.set(worldW * 0.9f, worldH * 0.5f)
            station = st
            ships.add(st)
            stationTroopFloat = st.stationTroops.toFloat()
        }

        scheduleWaves()
    }

    // ---------------- Formation ----------------

    private fun placeFormation(fleet: List<Ship>) {
        val cx = worldW * 0.20f; val cy = worldH * 0.5f
        val sp = 95f
        val n = fleet.size
        fleet.forEachIndexed { i, ship ->
            val cs = buildPlayer(ship)
            val mid = (n - 1) / 2f
            when (formation) {
                Formation.LINE -> cs.pos.set(cx, cy + (i - mid) * sp)
                Formation.WALL -> cs.pos.set(cx - (i % 2) * 70f, cy + ((i / 2) - mid / 2f) * sp)
                Formation.WEDGE -> {
                    if (i == 0) cs.pos.set(cx + 90f, cy)
                    else {
                        val side = (i + 1) / 2
                        val dir = if (i % 2 == 1) -1 else 1
                        cs.pos.set(cx - side * 70f, cy + dir * side * 80f)
                    }
                }
                Formation.ECHELON -> cs.pos.set(cx - i * 55f, cy + i * 72f)
            }
            cs.angle = 0f
            ships.add(cs)
        }
    }

    // ---------------- Warp waves ----------------

    private fun scheduleWaves() {
        var count = 0
        if (ambush) count += 1 + rng.nextInt(2)
        if (system.isBoss) count += 2
        if (rng.nextFloat() < 0.28f * difficulty.ambushMult) count += 1  // random surprise
        count = kotlin.math.ceil(count * difficulty.ambushMult).toInt()
        for (i in 0 until count) {
            val timer = 4.5f + i * 3.5f + rng.nextFloat() * 2.5f
            val danger = (system.danger + if (system.isBoss) 1 else 0)
            val size = 1 + rng.nextInt(2) + danger / 2
            val defs = (0 until size).map { enemyShipForDanger(danger, rng) }
            waves.add(WarpWave(timer, 2.6f, pickWarpPoint(), defs))
        }
    }

    /** Choose where a wave warps in — including behind the player for real ambushes. */
    private fun pickWarpPoint(): Vec2 = when (rng.nextInt(5)) {
        0 -> Vec2(worldW * 0.85f, worldH * (0.2f + rng.nextFloat() * 0.6f)) // right
        1 -> Vec2(worldW * (0.3f + rng.nextFloat() * 0.5f), worldH * 0.14f) // top
        2 -> Vec2(worldW * (0.3f + rng.nextFloat() * 0.5f), worldH * 0.86f) // bottom
        3 -> Vec2(worldW * 0.14f, worldH * (0.2f + rng.nextFloat() * 0.6f)) // BEHIND the fleet
        else -> Vec2(worldW * (0.5f + rng.nextFloat() * 0.35f), worldH * (0.2f + rng.nextFloat() * 0.6f))
    }

    private fun processWaves(dt: Float) {
        if (lastWarpWarning > 0f) lastWarpWarning -= dt
        val it = waves.iterator()
        while (it.hasNext()) {
            val w = it.next()
            w.timer -= dt
            if (w.portalVisible && lastWarpWarning <= 0f && w.progress < 0.15f) lastWarpWarning = 3f
            if (w.timer <= 0f) {
                w.defIds.forEachIndexed { i, defId ->
                    val cs = buildEnemy(defId)
                    cs.pos.set(w.pos.x + (i - w.defIds.size / 2f) * 60f, w.pos.y + rng.nextFloat() * 40f)
                    cs.angle = rng.nextFloat() * MathUtil.TAU
                    ships.add(cs)
                }
                // warp-in flash
                spawnExplosion(w.pos.x, w.pos.y, 1.6f, Palette.shieldBar)
                tracers.add(Tracer(w.pos.x - 60f, w.pos.y, w.pos.x + 60f, w.pos.y, Palette.lighten(Palette.accent, 0.4f)))
                tracers.add(Tracer(w.pos.x, w.pos.y - 60f, w.pos.x, w.pos.y + 60f, Palette.lighten(Palette.accent, 0.4f)))
                it.remove()
            }
        }
    }

    val pendingWaves: Boolean get() = waves.isNotEmpty()

    // ---------------- Construction ----------------

    private fun buildPlayer(ship: Ship): CombatShip {
        val cs = CombatShip(0, ship.defId, ship.def.cls, ship.name, Palette.player, ship)
        cs.maxHull = ship.maxHull * tech.hullMult
        cs.hull = (ship.currentHull * tech.hullMult).coerceAtLeast(cs.maxHull * 0.05f)
        cs.maxShield = ship.maxShield * tech.shieldMult
        cs.shield = cs.maxShield
        cs.shieldRegen = ship.shieldRegen * tech.shieldMult
        cs.speed = ship.speed * tech.engineMult
        cs.turnRate = ship.turnRate * tech.engineMult
        cs.size = ship.def.size
        cs.repairRate = ship.repairRate + tech.repairFlat
        cs.fighterCapacity = ship.fighterCapacity
        cs.troopCapacity = ship.troopCapacity
        ship.weaponDefs().forEach {
            cs.mounts.add(WeaponMount(it).also { m -> m.dmgMult = tech.damageMult; m.rateMult = tech.fireRateMult })
        }
        if (cs.mounts.isEmpty() && cs.cls != ShipClass.TRANSPORT)
            cs.mounts.add(WeaponMount(Catalog.weapon("laser_mk1")!!))
        return cs
    }

    private fun buildEnemy(defId: String): CombatShip {
        val def = Catalog.ship(defId)
        val cs = CombatShip(1, defId, def.cls, def.name, enemyFaction.color, null)
        val fit = enemyFit(def.cls)
        cs.maxHull = (def.hull + fit.hullBonus) * difficulty.enemyHpMult
        cs.hull = cs.maxHull
        cs.maxShield = fit.shield
        cs.shield = fit.shield
        cs.shieldRegen = fit.shieldRegen
        cs.speed = def.speed
        cs.turnRate = def.turn
        cs.size = def.size
        cs.fighterCapacity = def.fighterBays
        fit.weapons.forEach { cs.mounts.add(WeaponMount(it).also { m -> m.dmgMult = enemyDmgMult }) }
        return cs
    }

    private fun buildStation(sys: StarSystem): CombatShip {
        val cs = CombatShip(1, "station", ShipClass.BATTLESHIP, "สถานีอวกาศ", enemyFaction.color, null)
        cs.isStation = true
        cs.maxHull = sys.stationHealth * 2.2f * difficulty.enemyHpMult
        cs.hull = cs.maxHull
        cs.maxShield = sys.stationHealth * 1.1f * difficulty.enemyHpMult
        cs.shield = cs.maxShield
        cs.shieldRegen = 10f
        cs.speed = 0f
        cs.turnRate = 0.4f
        cs.size = 60f
        cs.stationTroops = sys.stationTroops.coerceAtLeast(1)
        val heavy = Catalog.weapon("plasma")!!
        val pd = Catalog.weapon("flak")!!
        val rail = Catalog.weapon("railgun")!!
        repeat(2 + sys.danger / 2) { cs.mounts.add(WeaponMount(heavy).also { it.dmgMult = enemyDmgMult }) }
        repeat(2) { cs.mounts.add(WeaponMount(pd)) }
        cs.mounts.add(WeaponMount(rail).also { it.dmgMult = enemyDmgMult })
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
        processWaves(dt)
        for (s in ships) {
            if (!s.alive) continue
            s.shieldFlash = (s.shieldFlash - dt).coerceAtLeast(0f)
            s.hullFlash = (s.hullFlash - dt).coerceAtLeast(0f)
            if (s.shield < s.maxShield) s.shield = (s.shield + s.shieldRegen * dt).coerceAtMost(s.maxShield)
            if (s.repairRate > 0f && s.hull < s.maxHull)
                s.hull = (s.hull + s.repairRate * dt).coerceAtMost(s.maxHull)
            updateAi(s)
            steer(s, dt)
            fireWeapons(s, dt)
            if (s.fighterCapacity > 0) updateCarrier(s, dt)
        }
        updateProjectiles(dt)
        updateFighters(dt)
        updateBoarding(dt)
        val pi = particles.iterator()
        while (pi.hasNext()) { val pp = pi.next(); pp.update(dt); if (!pp.alive) pi.remove() }
        val ti = tracers.iterator()
        while (ti.hasNext()) { val t = ti.next(); t.life -= dt; if (t.life <= 0f) ti.remove() }
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

    private fun updateAi(s: CombatShip) {
        s.attackTarget?.let { if (!it.alive || it.team == s.team) s.attackTarget = null }
        if (s.isStation) { if (s.attackTarget == null) s.attackTarget = nearestHostile(s); return }
        if (s.team == 0 && s.moveGoal != null) {
            // player move order in effect
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
            goal.set(move); haveGoal = true
            if (Vec2.dist(s.pos.x, s.pos.y, move.x, move.y) < s.size + 12f) { s.moveGoal = null; haveGoal = false }
        }
        if (!haveGoal && target != null) {
            val standoff = (s.minEngageRange * 0.8f).coerceAtLeast(80f)
            val dx = s.pos.x - target.pos.x; val dy = s.pos.y - target.pos.y
            val d = Vec2.dist(0f, 0f, dx, dy).coerceAtLeast(1f)
            goal.set(target.pos.x + dx / d * standoff, target.pos.y + dy / d * standoff)
            haveGoal = true
        }
        if (!haveGoal) { s.vel.scale(0.9f); s.pos.addScaled(s.vel, dt); return }
        val desired = atan2(goal.y - s.pos.y, goal.x - s.pos.x)
        s.angle = MathUtil.turnToward(s.angle, desired, s.turnRate * dt)
        val dist = Vec2.dist(s.pos.x, s.pos.y, goal.x, goal.y)
        val throttle = (dist / 120f).coerceIn(0f, 1f)
        s.vel.set(cos(s.angle) * s.speed * throttle, sin(s.angle) * s.speed * throttle)
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
            if (def.type == WeaponType.FLAK && tryPointDefense(s, mount)) { mount.cooldown = mount.interval; continue }
            val d = Vec2.dist(s.pos.x, s.pos.y, target.pos.x, target.pos.y)
            if (d > def.range) continue
            fireAt(s, mount, target)
            mount.cooldown = mount.interval
        }
    }

    private fun tryPointDefense(s: CombatShip, mount: WeaponMount): Boolean {
        var best: Projectile? = null
        var bestD = mount.def.range * mount.def.range
        for (p in projectiles) {
            if (!p.alive || p.team == s.team || !p.interceptable) continue
            val d = Vec2.distSq(s.pos.x, s.pos.y, p.pos.x, p.pos.y)
            if (d < bestD) { bestD = d; best = p }
        }
        val mm = best ?: return false
        mm.hp -= mount.def.damage
        tracers.add(Tracer(s.pos.x, s.pos.y, mm.pos.x, mm.pos.y, Palette.accentWarm))
        if (mm.hp <= 0f) mm.alive = false
        return true
    }

    private fun fireAt(s: CombatShip, mount: WeaponMount, target: CombatShip) {
        val def = mount.def
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
        projectiles.add(Projectile(
            team = s.team,
            pos = Vec2(s.pos.x + cos(s.angle) * s.size, s.pos.y + sin(s.angle) * s.size),
            vel = vel, damage = def.damage * mount.dmgMult, range = def.range, speed = def.projSpeed,
            type = def.type, shieldFactor = def.shieldFactor, armorFactor = def.armorFactor,
            target = if (def.tracking > 0f) target else null, tracking = def.tracking, color = color
        ))
        if (def.type == WeaponType.RAILGUN)
            tracers.add(Tracer(s.pos.x, s.pos.y, px, py, Palette.withAlpha(Palette.textPrimary, 120)))
    }

    private fun updateCarrier(s: CombatShip, dt: Float) {
        s.fighterLaunchCd -= dt
        if (s.activeFighters >= s.fighterCapacity * 3) return
        if (s.fighterLaunchCd > 0f) return
        if (nearestHostile(s) == null) return
        val f = Fighter(s.team, if (s.team == 0) Palette.player else s.color, s)
        f.pos.set(s.pos.x, s.pos.y); f.angle = s.angle
        fighters.add(f); s.activeFighters++; s.fighterLaunchCd = 1.4f
    }

    private fun updateProjectiles(dt: Float) {
        val it = projectiles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (!p.alive) { it.remove(); continue }
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
                    onShipMaybeKilled(s); hit = true; break
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
                    projectiles.add(Projectile(f.team, Vec2(f.pos.x, f.pos.y),
                        Vec2(cos(ang) * 800f, sin(ang) * 800f),
                        5f, 380f, 800f, WeaponType.AUTOCANNON, 1f, 1f, null, 0f,
                        if (f.team == 0) Palette.player else f.color))
                    f.fireCd = 0.5f
                }
            }
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
        if (st.hull <= 0f) {
            st.alive = false; stationDestroyed = true; salvageCredits += 600; researchReward += 3
            spawnExplosion(st.pos.x, st.pos.y, 4.5f, Palette.engineEnemy)
            repeat(6) {
                val a = rng.nextFloat() * MathUtil.TAU
                spawnExplosion(st.pos.x + cos(a) * st.size * 0.6f, st.pos.y + sin(a) * st.size * 0.6f,
                    1.6f, Palette.engineEnemy)
            }
            return
        }
        if (st.shield > 0f || st.hullFraction > 0.4f) return
        var assault = 0
        for (s in ships) {
            if (s.team != 0 || !s.alive || s.troopCapacity <= 0) continue
            if (Vec2.dist(s.pos.x, s.pos.y, st.pos.x, st.pos.y) < st.size + 150f) assault += s.troopCapacity
        }
        if (assault > 0) {
            stationTroopFloat -= dt * assault * 0.7f
            st.stationTroops = kotlin.math.ceil(stationTroopFloat.coerceAtLeast(0f)).toInt()
            if (stationTroopFloat <= 0f) {
                st.team = 0; stationCaptured = true; st.attackTarget = null
                salvageCredits += 400; researchReward += 4
            }
        }
    }

    private fun onShipMaybeKilled(s: CombatShip) {
        if (s.alive) return
        val col = if (s.team == 0) Palette.enginePlayer else Palette.engineEnemy
        spawnExplosion(s.pos.x, s.pos.y, (s.size / 20f).coerceIn(0.7f, 3.5f), col)
        if (s.team == 1 && !s.isStation) {
            salvageCredits += (Catalog.shipOrNull(s.defId)?.cost ?: 400) / 10
            researchReward += 1
        }
    }

    fun spawnExplosion(x: Float, y: Float, scale: Float, color: Int) {
        particles.add(Particle(Vec2(x, y), Vec2(0f, 0f), 0.18f, 0.18f,
            26f * scale, Palette.lighten(color, 0.7f), ParticleKind.FLASH))
        val sparks = (10 * scale).toInt().coerceIn(6, 26)
        repeat(sparks) {
            val a = rng.nextFloat() * MathUtil.TAU
            val sp = (120f + rng.nextFloat() * 240f) * scale
            particles.add(Particle(Vec2(x, y), Vec2(cos(a) * sp, sin(a) * sp),
                0.3f + rng.nextFloat() * 0.4f, 0.7f, (2f + rng.nextFloat() * 3f),
                Palette.lighten(color, 0.4f), ParticleKind.SPARK))
        }
        repeat((sparks / 2).coerceAtLeast(3)) {
            val a = rng.nextFloat() * MathUtil.TAU
            val sp = (50f + rng.nextFloat() * 130f) * scale
            particles.add(Particle(Vec2(x, y), Vec2(cos(a) * sp, sin(a) * sp),
                0.6f + rng.nextFloat() * 0.6f, 1.2f, (3f + rng.nextFloat() * 4f) * scale,
                Palette.darken(color, 0.25f), ParticleKind.DEBRIS))
        }
        repeat(3) {
            val a = rng.nextFloat() * MathUtil.TAU
            particles.add(Particle(Vec2(x, y), Vec2(cos(a) * 30f, sin(a) * 30f),
                0.9f + rng.nextFloat() * 0.6f, 1.5f, 10f * scale,
                Palette.withAlpha(Palette.strokeSoft, 120), ParticleKind.SMOKE))
        }
    }

    // ---------------- Player orders ----------------

    fun selectAt(wx: Float, wy: Float): Boolean {
        var picked: CombatShip? = null; var bestD = Float.MAX_VALUE
        for (s in ships) {
            if (s.team != 0 || !s.alive) continue
            val d = Vec2.distSq(wx, wy, s.pos.x, s.pos.y)
            val r = (s.size + 30f) * (s.size + 30f)
            if (d < r && d < bestD) { bestD = d; picked = s }
        }
        if (picked != null) { ships.forEach { it.selected = false }; picked.selected = true; return true }
        return false
    }

    fun selectAll() { ships.forEach { if (it.team == 0 && it.alive) it.selected = true } }
    fun clearSelection() { ships.forEach { it.selected = false } }
    val selectedCount: Int get() = ships.count { it.selected && it.team == 0 && it.alive }

    fun orderAt(wx: Float, wy: Float) {
        var enemy: CombatShip? = null; var bestD = Float.MAX_VALUE
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

    fun setAutoEngageAll() { for (s in ships) if (s.team == 0 && s.alive) { s.moveGoal = null; s.attackTarget = null } }

    // ---------------- Outcome ----------------

    fun playerShipsAlive(): Int = ships.count { it.team == 0 && it.alive && !it.isStation }
    fun hostilesRemain(): Boolean = ships.any { it.team == 1 && it.alive }

    fun result(): BattleResult = when {
        playerShipsAlive() == 0 -> BattleResult.PLAYER_LOSS
        !hostilesRemain() && !pendingWaves -> BattleResult.PLAYER_WIN
        else -> BattleResult.RUNNING
    }

    fun survivingPlayerShips(): List<CombatShip> = ships.filter { it.team == 0 && it.source != null }

    private fun enemyShipForDanger(danger: Int, rng: Random): String = when {
        danger >= 5 && rng.nextFloat() < 0.4f -> "battleship"
        danger >= 4 && rng.nextFloat() < 0.5f -> "destroyer"
        danger >= 3 && rng.nextFloat() < 0.5f -> "carrier"
        danger >= 2 -> if (rng.nextBoolean()) "frigate" else "destroyer"
        else -> if (rng.nextBoolean()) "interceptor" else "frigate"
    }
}
