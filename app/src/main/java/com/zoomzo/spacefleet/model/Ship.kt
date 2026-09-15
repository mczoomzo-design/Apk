package com.zoomzo.spacefleet.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * A concrete ship the player owns: a hull [defId] plus the weapons and modules
 * fitted into its slots. Combat damage persists via [hullDamage] until repaired.
 */
class Ship(
    val defId: String,
    var name: String,
    val id: Int
) {
    val def: ShipDef get() = Catalog.ship(defId)

    val hardpoints: MutableList<String?> =
        MutableList(def.hardpoints) { null }
    val modules: MutableList<String?> =
        MutableList(def.moduleSlots) { null }

    /** Accumulated hull damage carried between battles (0 == pristine). */
    var hullDamage: Float = 0f

    // ---- Derived stats ----

    private fun modules(): List<ModuleDef> = modules.mapNotNull { it?.let(Catalog::module) }
    fun weaponDefs(): List<WeaponDef> = hardpoints.mapNotNull { it?.let(Catalog::weapon) }

    val maxHull: Float get() = def.hull + modules().sumOf { it.hull.toDouble() }.toFloat()
    val maxShield: Float get() = modules().sumOf { it.shield.toDouble() }.toFloat()
    val shieldRegen: Float get() = modules().sumOf { it.shieldRegen.toDouble() }.toFloat()
    val speed: Float get() = def.speed + modules().sumOf { it.speed.toDouble() }.toFloat()
    val turnRate: Float get() = def.turn + modules().sumOf { it.turn.toDouble() }.toFloat()
    val fighterCapacity: Int get() = def.fighterBays + modules().sumOf { it.fighterCap }
    val troopCapacity: Int get() = def.troopBase + modules().sumOf { it.troopCap }
    val repairRate: Float get() = modules().sumOf { it.repairRate.toDouble() }.toFloat()
    val sensorRange: Float get() = 600f + modules().sumOf { it.sensorBonus.toDouble() }.toFloat()
    val dps: Float get() = weaponDefs().sumOf { it.dps.toDouble() }.toFloat()

    val currentHull: Float get() = (maxHull - hullDamage).coerceAtLeast(0f)
    val hullFraction: Float get() = if (maxHull <= 0f) 0f else currentHull / maxHull
    val isDestroyed: Boolean get() = currentHull <= 0f

    /** Resale/insurance value including fitted gear. */
    val value: Int get() = def.cost +
        hardpoints.mapNotNull { it?.let(Catalog::weapon) }.sumOf { it.cost } +
        modules().sumOf { it.cost }

    fun repairFully() { hullDamage = 0f }

    fun applyRepairFraction(f: Float) {
        hullDamage = (hullDamage - maxHull * f).coerceAtLeast(0f)
    }

    fun equipWeapon(slot: Int, weaponId: String?) {
        if (slot in hardpoints.indices) hardpoints[slot] = weaponId
    }

    fun equipModule(slot: Int, moduleId: String?) {
        if (slot in modules.indices) modules[slot] = moduleId
    }

    fun classColorRole(): Faction = Faction.PLAYER

    fun toJson(): JSONObject = JSONObject().apply {
        put("defId", defId)
        put("name", name)
        put("id", id)
        put("hullDamage", hullDamage.toDouble())
        put("hardpoints", JSONArray().also { arr -> hardpoints.forEach { arr.put(it ?: JSONObject.NULL) } })
        put("modules", JSONArray().also { arr -> modules.forEach { arr.put(it ?: JSONObject.NULL) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Ship {
            val s = Ship(o.getString("defId"), o.getString("name"), o.getInt("id"))
            s.hullDamage = o.optDouble("hullDamage", 0.0).toFloat()
            o.optJSONArray("hardpoints")?.let { arr ->
                for (i in 0 until minOf(arr.length(), s.hardpoints.size)) {
                    s.hardpoints[i] = if (arr.isNull(i)) null else arr.getString(i)
                }
            }
            o.optJSONArray("modules")?.let { arr ->
                for (i in 0 until minOf(arr.length(), s.modules.size)) {
                    s.modules[i] = if (arr.isNull(i)) null else arr.getString(i)
                }
            }
            return s
        }
    }
}
