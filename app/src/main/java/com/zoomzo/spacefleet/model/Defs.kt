package com.zoomzo.spacefleet.model

/** Static blueprint for a weapon that can be mounted on a hardpoint. */
data class WeaponDef(
    val id: String,
    val name: String,
    val type: WeaponType,
    val damage: Float,
    val range: Float,
    val fireRate: Float,       // shots per second
    val projSpeed: Float,      // world units per second
    val cost: Int,
    val tracking: Float = 0f,  // rad/s homing turn rate (missiles); 0 = dumb-fire
    val shieldFactor: Float = 1f, // damage multiplier vs shields
    val armorFactor: Float = 1f   // damage multiplier vs hull/armor
) {
    val dps: Float get() = damage * fireRate
}

/** Static blueprint for an equippable module (utility slot). */
data class ModuleDef(
    val id: String,
    val name: String,
    val type: ModuleType,
    val cost: Int,
    val hull: Float = 0f,
    val shield: Float = 0f,
    val shieldRegen: Float = 0f,
    val speed: Float = 0f,       // additive world units/sec
    val turn: Float = 0f,        // additive rad/sec
    val fighterCap: Int = 0,
    val troopCap: Int = 0,
    val cargo: Int = 0,
    val repairRate: Float = 0f,  // hull/sec regen in combat
    val sensorBonus: Float = 0f
)

/** Static blueprint for a ship hull. */
data class ShipDef(
    val id: String,
    val name: String,
    val cls: ShipClass,
    val cost: Int,
    val hull: Float,
    val speed: Float,        // world units/sec
    val turn: Float,         // rad/sec
    val size: Float,         // draw/collision radius (world units)
    val hardpoints: Int,
    val moduleSlots: Int,
    val fighterBays: Int = 0,   // intrinsic squadrons a carrier can field
    val troopBase: Int = 0,     // intrinsic boarding troops
    val desc: String
)
