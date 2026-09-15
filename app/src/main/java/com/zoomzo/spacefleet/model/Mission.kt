package com.zoomzo.spacefleet.model

import org.json.JSONObject

enum class MissionStatus { OFFERED, ACTIVE, COMPLETE, FAILED }

/** A contract accepted at a station, resolved by acting in a target system. */
class Mission(
    val id: Int,
    val type: MissionType,
    val title: String,
    val desc: String,
    val targetSystemId: Int,
    val giverSystemId: Int,
    val rewardCredits: Int,
    val rewardRep: Int,
    var status: MissionStatus = MissionStatus.OFFERED
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("type", type.name); put("title", title); put("desc", desc)
        put("target", targetSystemId); put("giver", giverSystemId)
        put("reward", rewardCredits); put("rep", rewardRep); put("status", status.name)
    }

    companion object {
        fun fromJson(o: JSONObject) = Mission(
            o.getInt("id"),
            MissionType.valueOf(o.getString("type")),
            o.getString("title"),
            o.getString("desc"),
            o.getInt("target"),
            o.getInt("giver"),
            o.getInt("reward"),
            o.getInt("rep"),
            MissionStatus.valueOf(o.optString("status", "OFFERED"))
        )
    }
}
