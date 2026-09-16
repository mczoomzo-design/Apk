package com.zoomzo.spacefleet.model

import com.zoomzo.spacefleet.engine.Palette

enum class Faction(val displayName: String, val color: Int) {
    PLAYER("กองยานของคุณ", Palette.player),
    ALLY("พันธมิตร", Palette.ally),
    ENEMY("จักรวรรดิศัตรู", Palette.enemy),
    PIRATE("โจรสลัดอวกาศ", Palette.pirate),
    NEUTRAL("เป็นกลาง", Palette.neutral);

    fun isHostileTo(other: Faction): Boolean = when (this) {
        PLAYER, ALLY -> other == ENEMY || other == PIRATE
        ENEMY -> other == PLAYER || other == ALLY
        PIRATE -> other != PIRATE
        NEUTRAL -> false
    }
}

/** Ship hull classes with a role. */
enum class ShipClass(val displayName: String) {
    SCOUT("ยานลาดตระเวน"),
    INTERCEPTOR("ยานเร็ว"),
    FRIGATE("ยานฟริเกต"),
    DESTROYER("ยานพิฆาต"),
    BATTLESHIP("ยานประจัญบาน"),
    CARRIER("ยานบรรทุกเครื่องบิน"),
    TRANSPORT("ยานยกพลขึ้นบก")
}

enum class WeaponType(val displayName: String, val pointDefense: Boolean = false) {
    LASER("เลเซอร์"),
    AUTOCANNON("ปืนกลอัตโนมัติ"),
    PLASMA("พลาสมา"),
    RAILGUN("เรลกัน"),
    MISSILE("มิสไซล์"),
    FLAK("ปืนป้องกันระยะประชิด", pointDefense = true)
}

enum class ModuleType(val displayName: String) {
    SHIELD("เกราะพลังงาน"),
    ARMOR("เกราะหนา"),
    ENGINE("เครื่องยนต์"),
    REACTOR("เตาปฏิกรณ์"),
    FIGHTER_BAY("โรงเก็บเครื่องบิน"),
    TROOP_BAY("ห้องพลรบ"),
    CARGO("ห้องเก็บสัมภาระ"),
    SENSOR("เซนเซอร์"),
    REPAIR("หน่วยซ่อมบำรุง")
}

/** Campaign difficulty, chosen when starting a new game. */
enum class Difficulty(
    val displayName: String,
    val subtitle: String,
    val enemyHpMult: Float,
    val enemyDmgMult: Float,
    val ambushMult: Float,
    val rewardMult: Float,
    val startCredits: Int,
    val startResearch: Int
) {
    EASY("CADET", "ง่าย · เหมาะกับผู้เริ่มต้น", 0.8f, 0.7f, 0.5f, 1.25f, 8000, 6),
    NORMAL("CAPTAIN", "ปกติ · สมดุล", 1.0f, 1.0f, 1.0f, 1.0f, 6000, 4),
    HARD("COMMANDER", "ยาก · ศัตรูดุดัน ซุ่มโจมตีบ่อย", 1.3f, 1.3f, 1.5f, 0.9f, 5000, 3),
    INSANE("ADMIRAL", "โหด · เอาชีวิตรอดสุดขีด", 1.6f, 1.55f, 2.0f, 0.8f, 4000, 2)
}

/** Type of encounter waiting at a sector node (Harbinger-style jump map). */
enum class EncounterType(val displayName: String) {
    START("จุดเริ่มต้น"),
    COMBAT("การรบ"),
    ELITE("ศัตรูชั้นสูง"),
    STATION("สถานีพันธมิตร"),
    RESOURCE("แหล่งทรัพยากร"),
    UNKNOWN("พื้นที่ไม่ทราบ"),
    BOSS("ยานแม่ศัตรู")
}

/** Global technology upgrades bought with research points. */
enum class TechType(val displayName: String, val desc: String, val maxLevel: Int, val baseCost: Int) {
    TURRET_DAMAGE("อัปเกรดป้อมปืน", "เพิ่มความเสียหายอาวุธทุกลำ +12%/ระดับ", 6, 3),
    FIRE_RATE("ระบบเล็งอัตโนมัติ", "เพิ่มอัตราการยิง +8%/ระดับ", 6, 3),
    SHIELD_TECH("เทคโนโลยีโล่", "เพิ่มพลังโล่ +15%/ระดับ", 6, 3),
    HULL_TECH("โครงสร้างเสริม", "เพิ่มพลังเกราะตัวถัง +12%/ระดับ", 6, 3),
    ENGINE_TECH("ขับเคลื่อนขั้นสูง", "เพิ่มความเร็ว/การเลี้ยว +10%/ระดับ", 5, 3),
    REPAIR_TECH("นาโนซ่อมบำรุง", "ซ่อมตัวถังระหว่างรบ +2/วินาที/ระดับ", 5, 4)
}

/** Fleet battle formation, chosen before engagements. */
enum class Formation(val displayName: String) {
    LINE("แนวเส้น (Line)"),
    WEDGE("หัวลูกศร (Wedge)"),
    WALL("กำแพง (Wall)"),
    ECHELON("เฉียง (Echelon)")
}

enum class MissionType(val displayName: String) {
    PATROL("ลาดตระเวน"),
    ASSAULT("โจมตีกองยานศัตรู"),
    INVADE("บุกยึดสถานี"),
    DEFEND("ป้องกันสถานี"),
    BOUNTY("ล่าค่าหัวโจรสลัด"),
    DELIVERY("ขนส่งสินค้า")
}
