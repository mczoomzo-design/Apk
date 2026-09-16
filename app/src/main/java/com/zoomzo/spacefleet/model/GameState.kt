package com.zoomzo.spacefleet.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.random.Random

/**
 * The full persistent game world: player credits, fleet, gear inventory,
 * the galaxy, reputation and mission log. Screens read and mutate this directly.
 */
class GameState private constructor() {

    var credits: Int = 0
    var research: Int = 0
    var day: Int = 1
    lateinit var galaxy: Galaxy

    val fleet: MutableList<Ship> = mutableListOf()
    private var nextShipId = 1

    /** The command ship. If it is lost, the campaign ends. */
    var flagshipId: Int = -1
    var formation: Formation = Formation.WEDGE
    var difficulty: Difficulty = Difficulty.NORMAL
    val techLevels: MutableMap<TechType, Int> = mutableMapOf()

    /** Unequipped gear the player owns, keyed by def id -> count. */
    val ownedWeapons: MutableMap<String, Int> = mutableMapOf()
    val ownedModules: MutableMap<String, Int> = mutableMapOf()

    val reputation: MutableMap<Faction, Int> = mutableMapOf(
        Faction.ALLY to 0, Faction.ENEMY to 0, Faction.PIRATE to 0
    )

    val missions: MutableList<Mission> = mutableListOf()
    private var nextMissionId = 1

    val currentSystem: StarSystem get() = galaxy.current

    // ---------- Economy ----------

    fun canAfford(cost: Int) = credits >= cost
    fun spend(cost: Int): Boolean { if (credits < cost) return false; credits -= cost; return true }
    fun earn(amount: Int) { credits += amount }
    fun earnResearch(amount: Int) { research += amount }

    // ---------- Technology ----------

    fun techLevel(t: TechType): Int = techLevels[t] ?: 0
    fun techCost(t: TechType): Int = t.baseCost * (techLevel(t) + 1)
    fun canUpgradeTech(t: TechType): Boolean =
        techLevel(t) < t.maxLevel && research >= techCost(t)

    fun buyTech(t: TechType): Boolean {
        if (!canUpgradeTech(t)) return false
        research -= techCost(t)
        techLevels[t] = techLevel(t) + 1
        return true
    }

    fun techBonus(): TechBonus = TechBonus(
        damageMult = 1f + 0.12f * techLevel(TechType.TURRET_DAMAGE),
        fireRateMult = 1f + 0.08f * techLevel(TechType.FIRE_RATE),
        shieldMult = 1f + 0.15f * techLevel(TechType.SHIELD_TECH),
        hullMult = 1f + 0.12f * techLevel(TechType.HULL_TECH),
        engineMult = 1f + 0.10f * techLevel(TechType.ENGINE_TECH),
        repairFlat = 2f * techLevel(TechType.REPAIR_TECH)
    )

    // ---------- Flagship & sector ----------

    fun flagshipAlive(): Boolean = fleet.any { it.id == flagshipId }
    val flagship: Ship? get() = fleet.firstOrNull { it.id == flagshipId }

    /** Reward on entering a non-combat resource/unknown node. */
    fun grantNodeReward(node: StarSystem) {
        if (node.rewardCredits > 0) earn(node.rewardCredits)
        if (node.rewardResearch > 0) earnResearch(node.rewardResearch)
    }

    fun jumpAmbushChance(): Float = (0.16f + 0.05f * galaxy.sectorNumber).coerceAtMost(0.6f)

    fun advanceSector(rng: Random = Random(System.nanoTime())) {
        val next = galaxy.sectorNumber + 1
        galaxy = Galaxy.generate(next, rng)
        earn(500 + next * 200)
        earnResearch(4 + next)
    }

    // ---------- Fleet ----------

    fun buyShip(defId: String): Ship? {
        val def = Catalog.shipOrNull(defId) ?: return null
        if (!spend(def.cost)) return null
        val ship = Ship(defId, def.name, nextShipId++)
        fleet.add(ship)
        return ship
    }

    fun sellShip(ship: Ship): Int {
        val refund = (ship.value * 0.6f).toInt()
        if (fleet.remove(ship)) { earn(refund); return refund }
        return 0
    }

    fun fleetPower(): Int = fleet.sumOf { (it.maxHull + it.maxShield + it.dps * 6f).toInt() }

    fun repairFleetCost(): Int =
        fleet.sumOf { (it.hullDamage * 0.4f).toInt() }

    fun repairFleet(): Int {
        val cost = repairFleetCost()
        if (cost == 0) return 0
        if (!spend(cost)) return -1
        fleet.forEach { it.repairFully() }
        return cost
    }

    // ---------- Inventory / fitting ----------

    fun addWeapon(id: String, n: Int = 1) { ownedWeapons[id] = (ownedWeapons[id] ?: 0) + n }
    fun addModule(id: String, n: Int = 1) { ownedModules[id] = (ownedModules[id] ?: 0) + n }
    fun weaponStock(id: String): Int = ownedWeapons[id] ?: 0
    fun moduleStock(id: String): Int = ownedModules[id] ?: 0

    fun buyWeapon(id: String): Boolean {
        val w = Catalog.weapon(id) ?: return false
        if (!spend(w.cost)) return false
        addWeapon(id); return true
    }

    fun buyModule(id: String): Boolean {
        val m = Catalog.module(id) ?: return false
        if (!spend(m.cost)) return false
        addModule(id); return true
    }

    /** Fit [weaponId] (or null to clear) into a hardpoint, moving gear via inventory. */
    fun fitWeapon(ship: Ship, slot: Int, weaponId: String?): Boolean {
        val existing = ship.hardpoints.getOrNull(slot)
        if (weaponId != null) {
            if (weaponStock(weaponId) <= 0) return false
            ownedWeapons[weaponId] = weaponStock(weaponId) - 1
        }
        if (existing != null) addWeapon(existing)
        ship.equipWeapon(slot, weaponId)
        return true
    }

    fun fitModule(ship: Ship, slot: Int, moduleId: String?): Boolean {
        val existing = ship.modules.getOrNull(slot)
        if (moduleId != null) {
            if (moduleStock(moduleId) <= 0) return false
            ownedModules[moduleId] = moduleStock(moduleId) - 1
        }
        if (existing != null) addModule(existing)
        ship.equipModule(slot, moduleId)
        return true
    }

    // ---------- Reputation ----------

    fun addRep(faction: Faction, amt: Int) {
        reputation[faction] = (reputation[faction] ?: 0) + amt
    }

    // ---------- Travel & missions ----------

    fun warpTo(systemId: Int) {
        galaxy.currentId = systemId
        galaxy.current.explored = true
        day++
        checkMissionProgress()
    }

    /** Ensure allied stations always have a few contracts to pick up. */
    fun refreshMissions(rng: Random = Random(System.nanoTime())) {
        for (station in galaxy.systems.filter { it.hasStation && it.stationFaction == Faction.ALLY }) {
            val offered = missions.count { it.giverSystemId == station.id && it.status == MissionStatus.OFFERED }
            var toAdd = (2 - offered).coerceAtLeast(0)
            val candidates = galaxy.systems.filter { it.id != station.id }
                .sortedBy { hypot((it.gridX - station.gridX).toFloat(), (it.gridY - station.gridY).toFloat()) }
            var idx = 0
            while (toAdd > 0 && idx < candidates.size) {
                val target = candidates[idx++]
                val m = makeMissionFor(station, target, rng) ?: continue
                if (missions.any { it.targetSystemId == m.targetSystemId && it.type == m.type &&
                            it.status != MissionStatus.COMPLETE }) continue
                missions.add(m); toAdd--
            }
        }
    }

    private fun makeMissionFor(giver: StarSystem, target: StarSystem, rng: Random): Mission? {
        val reward = 400 + target.danger * 350 + rng.nextInt(300)
        return when {
            target.stationFaction == Faction.ENEMY && target.hasStation ->
                Mission(nextMissionId++, MissionType.INVADE,
                    "บุกยึด: ${target.name}",
                    "ส่งยานยกพลบุกยึดสถานีศัตรูที่ ${target.name} (พลรบ ${target.stationTroops})",
                    target.id, giver.id, reward + 800, 15)
            target.owner == Faction.PIRATE || (target.stationFaction == Faction.PIRATE) ->
                Mission(nextMissionId++, MissionType.BOUNTY,
                    "ล่าค่าหัว: ${target.name}",
                    "กวาดล้างโจรสลัดในระบบ ${target.name}",
                    target.id, giver.id, reward, 8)
            target.hasHostilePatrol && target.owner == Faction.ENEMY ->
                Mission(nextMissionId++, MissionType.ASSAULT,
                    "โจมตี: ${target.name}",
                    "ทำลายกองลาดตระเวนศัตรูที่ ${target.name}",
                    target.id, giver.id, reward, 10)
            target.hasStation && target.stationFaction == Faction.ALLY ->
                Mission(nextMissionId++, MissionType.DELIVERY,
                    "ขนส่ง: ${target.name}",
                    "นำเสบียงไปส่งยังสถานีพันธมิตร ${target.name}",
                    target.id, giver.id, (reward * 0.6f).toInt(), 5)
            else ->
                Mission(nextMissionId++, MissionType.PATROL,
                    "ลาดตระเวน: ${target.name}",
                    "สำรวจและลาดตระเวนระบบ ${target.name}",
                    target.id, giver.id, (reward * 0.5f).toInt() + 200, 4)
        }
    }

    fun acceptMission(m: Mission) { if (m.status == MissionStatus.OFFERED) m.status = MissionStatus.ACTIVE }

    /** Recompute completion for active missions after travel/combat/invasion. */
    fun checkMissionProgress() {
        for (m in missions.filter { it.status == MissionStatus.ACTIVE }) {
            val target = galaxy.systems.firstOrNull { it.id == m.targetSystemId } ?: continue
            val done = when (m.type) {
                MissionType.ASSAULT, MissionType.BOUNTY -> !target.hasHostilePatrol
                MissionType.INVADE -> target.stationFaction != Faction.ENEMY && target.stationFaction != Faction.PIRATE
                MissionType.PATROL, MissionType.DELIVERY -> galaxy.currentId == target.id
                MissionType.DEFEND -> galaxy.currentId == target.id && !target.hasHostilePatrol
            }
            if (done) completeMission(m)
        }
    }

    private fun completeMission(m: Mission) {
        m.status = MissionStatus.COMPLETE
        earn(m.rewardCredits)
        addRep(Faction.ALLY, m.rewardRep)
    }

    val activeMissions: List<Mission> get() = missions.filter { it.status == MissionStatus.ACTIVE }

    // ---------- Serialization ----------

    fun toJson(): JSONObject = JSONObject().apply {
        put("credits", credits); put("research", research); put("day", day)
        put("nextShipId", nextShipId); put("nextMissionId", nextMissionId)
        put("flagshipId", flagshipId); put("formation", formation.name)
        put("difficulty", difficulty.name)
        put("galaxy", galaxy.toJson())
        put("fleet", JSONArray().also { arr -> fleet.forEach { arr.put(it.toJson()) } })
        put("weapons", JSONObject(ownedWeapons as Map<*, *>))
        put("modules", JSONObject(ownedModules as Map<*, *>))
        put("tech", JSONObject().also { r -> techLevels.forEach { (k, v) -> r.put(k.name, v) } })
        put("rep", JSONObject().also { r -> reputation.forEach { (k, v) -> r.put(k.name, v) } })
        put("missions", JSONArray().also { arr -> missions.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        fun newGame(difficulty: Difficulty = Difficulty.NORMAL,
                    rng: Random = Random(System.nanoTime())): GameState {
            val gs = GameState()
            gs.difficulty = difficulty
            gs.credits = difficulty.startCredits
            gs.research = difficulty.startResearch
            gs.galaxy = Galaxy.generate(1, rng)
            gs.formation = Formation.WEDGE

            // Free starter flagship so the player can move and fight immediately.
            val flag = Ship("frigate", "เรือธง (Flagship)", gs.nextShipId++)
            flag.equipWeapon(0, "laser_mk1")
            flag.equipWeapon(1, "autocannon")
            flag.equipModule(0, "shield_s")
            gs.fleet.add(flag)
            gs.flagshipId = flag.id

            // A light escort so the fleet feels like a fleet from the start.
            val escort = Ship("interceptor", "คุ้มกัน-1", gs.nextShipId++)
            escort.equipWeapon(0, "autocannon")
            gs.fleet.add(escort)

            gs.addWeapon("laser_mk1"); gs.addModule("armor_plate")
            return gs
        }

        fun fromJson(o: JSONObject): GameState {
            val gs = GameState()
            gs.credits = o.getInt("credits")
            gs.research = o.optInt("research", 0)
            gs.day = o.optInt("day", 1)
            gs.nextShipId = o.optInt("nextShipId", 1)
            gs.nextMissionId = o.optInt("nextMissionId", 1)
            gs.flagshipId = o.optInt("flagshipId", -1)
            gs.formation = Formation.valueOf(o.optString("formation", "WEDGE"))
            gs.difficulty = Difficulty.valueOf(o.optString("difficulty", "NORMAL"))
            gs.galaxy = Galaxy.fromJson(o.getJSONObject("galaxy"))
            o.getJSONArray("fleet").let { arr ->
                for (i in 0 until arr.length()) gs.fleet.add(Ship.fromJson(arr.getJSONObject(i)))
            }
            if (gs.flagshipId == -1) gs.flagshipId = gs.fleet.firstOrNull()?.id ?: -1
            o.optJSONObject("weapons")?.let { w -> w.keys().forEach { gs.ownedWeapons[it] = w.getInt(it) } }
            o.optJSONObject("modules")?.let { m -> m.keys().forEach { gs.ownedModules[it] = m.getInt(it) } }
            o.optJSONObject("tech")?.let { tj ->
                tj.keys().forEach { k -> gs.techLevels[TechType.valueOf(k)] = tj.getInt(k) }
            }
            o.optJSONObject("rep")?.let { r ->
                r.keys().forEach { k -> gs.reputation[Faction.valueOf(k)] = r.getInt(k) }
            }
            o.optJSONArray("missions")?.let { arr ->
                for (i in 0 until arr.length()) gs.missions.add(Mission.fromJson(arr.getJSONObject(i)))
            }
            return gs
        }
    }
}
