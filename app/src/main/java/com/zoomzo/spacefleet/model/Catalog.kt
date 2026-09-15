package com.zoomzo.spacefleet.model

/** Central registry of all ship hulls, weapons and modules in the game. */
object Catalog {

    val weapons: List<WeaponDef> = listOf(
        WeaponDef("laser_mk1", "เลเซอร์ Mk.I", WeaponType.LASER, 6f, 520f, 3f, 900f, 300),
        WeaponDef("laser_mk2", "เลเซอร์ Mk.II", WeaponType.LASER, 10f, 560f, 3f, 950f, 700),
        WeaponDef("autocannon", "ปืนกล Vulcan", WeaponType.AUTOCANNON, 4f, 420f, 6f, 820f, 400),
        WeaponDef("plasma", "ปืนพลาสมา", WeaponType.PLASMA, 22f, 480f, 1.2f, 600f, 1200, shieldFactor = 1.6f),
        WeaponDef("railgun", "เรลกันเจาะเกราะ", WeaponType.RAILGUN, 40f, 760f, 0.6f, 1600f, 1800, armorFactor = 1.6f),
        WeaponDef("missile", "มิสไซล์นำวิถี", WeaponType.MISSILE, 30f, 900f, 0.7f, 440f, 1500, tracking = 2.4f),
        WeaponDef("flak", "ปืนแฟล็คป้องกัน", WeaponType.FLAK, 3f, 260f, 8f, 700f, 600)
    )

    val modules: List<ModuleDef> = listOf(
        ModuleDef("shield_s", "โล่พลังงาน S", ModuleType.SHIELD, 900, shield = 80f, shieldRegen = 12f),
        ModuleDef("shield_l", "โล่พลังงาน L", ModuleType.SHIELD, 2200, shield = 200f, shieldRegen = 20f),
        ModuleDef("armor_plate", "แผ่นเกราะหนา", ModuleType.ARMOR, 800, hull = 150f),
        ModuleDef("engine_boost", "เครื่องยนต์เสริม", ModuleType.ENGINE, 1000, speed = 60f, turn = 0.6f),
        ModuleDef("reactor", "เตาปฏิกรณ์", ModuleType.REACTOR, 1200, shield = 20f, shieldRegen = 8f),
        ModuleDef("fighter_bay", "โรงเก็บเครื่องบิน", ModuleType.FIGHTER_BAY, 2500, fighterCap = 2),
        ModuleDef("troop_bay", "ห้องพลรบ", ModuleType.TROOP_BAY, 1600, troopCap = 3),
        ModuleDef("cargo", "ห้องเก็บสัมภาระ", ModuleType.CARGO, 500, cargo = 40),
        ModuleDef("sensor", "อาเรย์เซนเซอร์", ModuleType.SENSOR, 700, sensorBonus = 300f),
        ModuleDef("repair", "หน่วยซ่อมนาไนต์", ModuleType.REPAIR, 1500, repairRate = 6f)
    )

    val ships: List<ShipDef> = listOf(
        ShipDef("scout", "เหยี่ยวสำรวจ", ShipClass.SCOUT, 800,
            hull = 120f, speed = 240f, turn = 3.2f, size = 14f,
            hardpoints = 1, moduleSlots = 2,
            desc = "ยานเบา รวดเร็ว เซนเซอร์ไกล เหมาะกับการสำรวจและลาดตระเวน"),
        ShipDef("interceptor", "มีดสั้น", ShipClass.INTERCEPTOR, 1200,
            hull = 90f, speed = 270f, turn = 3.6f, size = 15f,
            hardpoints = 2, moduleSlots = 2,
            desc = "ยานเร็วโจมตี ปราดเปรียว เหมาะไล่ล่ายานเล็กและมิสไซล์"),
        ShipDef("frigate", "ทวนหอก", ShipClass.FRIGATE, 3200,
            hull = 260f, speed = 175f, turn = 2.2f, size = 20f,
            hardpoints = 3, moduleSlots = 3,
            desc = "ยานอเนกประสงค์ สมดุลระหว่างเกราะ อาวุธ และความเร็ว"),
        ShipDef("destroyer", "ค้อนสงคราม", ShipClass.DESTROYER, 6500,
            hull = 430f, speed = 150f, turn = 1.8f, size = 26f,
            hardpoints = 4, moduleSlots = 4,
            desc = "เรือรบหนัก อาวุธแน่น เหมาะเป็นแกนหลักของกองยาน"),
        ShipDef("battleship", "ป้อมปราการ", ShipClass.BATTLESHIP, 18000,
            hull = 900f, speed = 110f, turn = 1.0f, size = 38f,
            hardpoints = 6, moduleSlots = 5,
            desc = "ยานประจัญบานขนาดยักษ์ พลังทำลายล้างสูงสุด แต่เชื่องช้า"),
        ShipDef("carrier", "รังอินทรี", ShipClass.CARRIER, 14000,
            hull = 520f, speed = 120f, turn = 1.2f, size = 34f,
            hardpoints = 2, moduleSlots = 4, fighterBays = 3,
            desc = "ยานบรรทุกเครื่องบิน ปล่อยฝูงบินขับไล่ออกโจมตีจากระยะไกล"),
        ShipDef("transport", "หมูป่า", ShipClass.TRANSPORT, 5000,
            hull = 340f, speed = 130f, turn = 1.4f, size = 28f,
            hardpoints = 2, moduleSlots = 4, troopBase = 4,
            desc = "ยานยกพลขึ้นบก บรรทุกพลรบเพื่อบุกยึดสถานีอวกาศของศัตรู")
    )

    fun weapon(id: String): WeaponDef? = weapons.firstOrNull { it.id == id }
    fun module(id: String): ModuleDef? = modules.firstOrNull { it.id == id }
    fun ship(id: String): ShipDef = ships.first { it.id == id }
    fun shipOrNull(id: String): ShipDef? = ships.firstOrNull { it.id == id }
}
