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

enum class MissionType(val displayName: String) {
    PATROL("ลาดตระเวน"),
    ASSAULT("โจมตีกองยานศัตรู"),
    INVADE("บุกยึดสถานี"),
    DEFEND("ป้องกันสถานี"),
    BOUNTY("ล่าค่าหัวโจรสลัด"),
    DELIVERY("ขนส่งสินค้า")
}
