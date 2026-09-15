package com.zoomzo.spacefleet.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** A node on the sector jump-map (Harbinger/FTL style). */
class StarSystem(
    val id: Int,
    var name: String,
    val col: Int,
    val row: Int,
    var worldX: Float,
    var worldY: Float,
    var owner: Faction,
    var hasStation: Boolean,
    var stationFaction: Faction,
    var stationHealth: Int,
    var danger: Int,
    val starColor: Int,
    var encounter: EncounterType
) {
    // Backward-compatible grid accessors (used by a few helpers).
    val gridX: Int get() = col
    val gridY: Int get() = row

    var explored: Boolean = false
    var resolved: Boolean = false      // encounter finished
    var isAmbush: Boolean = false      // known/elite ambush -> extra warp-in waves
    var rewardCredits: Int = 0
    var rewardResearch: Int = 0

    /** Forward jump links to node ids in the next column. */
    val links: MutableList<Int> = mutableListOf()

    /** Hostile ships already present; fought on arrival. */
    val patrolFleet: MutableList<String> = mutableListOf()
    val garrisonFleet: MutableList<String> = mutableListOf()
    var stationTroops: Int = 0

    val hasHostilePatrol: Boolean get() = patrolFleet.isNotEmpty()
    val isBoss: Boolean get() = encounter == EncounterType.BOSS

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("col", col); put("row", row)
        put("wx", worldX.toDouble()); put("wy", worldY.toDouble())
        put("owner", owner.name); put("station", hasStation)
        put("sf", stationFaction.name); put("sh", stationHealth)
        put("danger", danger); put("color", starColor); put("enc", encounter.name)
        put("explored", explored); put("resolved", resolved); put("ambush", isAmbush)
        put("rc", rewardCredits); put("rr", rewardResearch)
        put("links", JSONArray(links)); put("troops", stationTroops)
        put("patrol", JSONArray(patrolFleet)); put("garrison", JSONArray(garrisonFleet))
    }

    companion object {
        fun fromJson(o: JSONObject): StarSystem {
            val s = StarSystem(
                o.getInt("id"), o.getString("name"), o.getInt("col"), o.getInt("row"),
                o.getDouble("wx").toFloat(), o.getDouble("wy").toFloat(),
                Faction.valueOf(o.getString("owner")), o.getBoolean("station"),
                Faction.valueOf(o.getString("sf")), o.getInt("sh"),
                o.getInt("danger"), o.getInt("color"),
                EncounterType.valueOf(o.optString("enc", "COMBAT"))
            )
            s.explored = o.optBoolean("explored", false)
            s.resolved = o.optBoolean("resolved", false)
            s.isAmbush = o.optBoolean("ambush", false)
            s.rewardCredits = o.optInt("rc", 0)
            s.rewardResearch = o.optInt("rr", 0)
            s.stationTroops = o.optInt("troops", 0)
            o.optJSONArray("links")?.let { for (i in 0 until it.length()) s.links.add(it.getInt(i)) }
            o.optJSONArray("patrol")?.let { for (i in 0 until it.length()) s.patrolFleet.add(it.getString(i)) }
            o.optJSONArray("garrison")?.let { for (i in 0 until it.length()) s.garrisonFleet.add(it.getString(i)) }
            return s
        }
    }
}

/** A sector: columns of nodes with forward jump links toward a boss node. */
class Galaxy(
    val systems: MutableList<StarSystem>,
    var currentId: Int,
    var sectorNumber: Int
) {
    fun system(id: Int): StarSystem = systems.first { it.id == id }
    val current: StarSystem get() = system(currentId)
    val lastCol: Int get() = systems.maxOf { it.col }
    val bossNode: StarSystem get() = systems.first { it.isBoss }

    /** Nodes reachable by a single forward jump from [id]. */
    fun reachableFrom(id: Int): List<StarSystem> = system(id).links.map { system(it) }
    fun isReachable(fromId: Int, toId: Int): Boolean = system(fromId).links.contains(toId)

    fun toJson(): JSONObject = JSONObject().apply {
        put("current", currentId); put("sector", sectorNumber)
        put("systems", JSONArray().also { arr -> systems.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Galaxy {
            val list = mutableListOf<StarSystem>()
            val arr = o.getJSONArray("systems")
            for (i in 0 until arr.length()) list.add(StarSystem.fromJson(arr.getJSONObject(i)))
            return Galaxy(list, o.getInt("current"), o.optInt("sector", 1))
        }

        private val PREFIX = listOf("Al", "Bel", "Cy", "Dra", "Eri", "Fen", "Gor", "Hy", "Ix",
            "Ju", "Kro", "Ly", "My", "Ne", "Or", "Pol", "Qua", "Ry", "Sol", "Tor", "Ur", "Vex", "Zan")
        private val SUFFIX = listOf("dara", "nix", "gon", "tara", "mos", "lith", "var", "pex",
            "rune", "dis", "phon", "kar", "wex", "lor", "tis", "vok")
        private val STAR_COLORS = listOf(
            0xFFFFE9A8.toInt(), 0xFFFFC97A.toInt(), 0xFFFF8E6B.toInt(),
            0xFFAEE0FF.toInt(), 0xFFE0AEFF.toInt(), 0xFFFFFFFF.toInt()
        )

        /** Build a fresh sector. Difficulty scales with [sector]. */
        fun generate(sector: Int, rng: Random = Random(System.nanoTime())): Galaxy {
            val cols = 7                 // col 0 = start, col 6 = boss
            val systems = mutableListOf<StarSystem>()
            var idc = 0

            // Column node counts: start=1, boss=1, middle 2..3.
            val nodesByCol = IntArray(cols)
            nodesByCol[0] = 1
            nodesByCol[cols - 1] = 1
            for (c in 1 until cols - 1) nodesByCol[c] = 2 + rng.nextInt(2)

            val byCol = Array(cols) { mutableListOf<StarSystem>() }
            for (c in 0 until cols) {
                val k = nodesByCol[c]
                for (j in 0 until k) {
                    val enc = when {
                        c == 0 -> EncounterType.START
                        c == cols - 1 -> EncounterType.BOSS
                        else -> rollEncounter(rng)
                    }
                    val name = if (c == 0) "จุดเริ่มต้น"
                    else if (c == cols - 1) "ยานแม่ศัตรู"
                    else PREFIX.random(rng) + SUFFIX.random(rng) + " " + ('A' + rng.nextInt(9)) + rng.nextInt(9)
                    val sys = StarSystem(
                        id = idc++, name = name, col = c, row = j,
                        worldX = 120f + c * 240f,
                        worldY = 420f + (j - (k - 1) / 2f) * 190f,
                        owner = Faction.NEUTRAL, hasStation = false,
                        stationFaction = Faction.NEUTRAL, stationHealth = 0,
                        danger = 1, starColor = STAR_COLORS.random(rng), encounter = enc
                    )
                    byCol[c].add(sys); systems.add(sys)
                }
            }

            // Forward links: each node links to 1-2 nodes in the next column.
            for (c in 0 until cols - 1) {
                val next = byCol[c + 1]
                for (node in byCol[c]) {
                    val sorted = next.sortedBy { kotlin.math.abs(it.row - node.row) }
                    val count = if (next.size == 1) 1 else 1 + rng.nextInt(2)
                    sorted.take(count).forEach { if (!node.links.contains(it.id)) node.links.add(it.id) }
                }
                // Guarantee every next-column node has an incoming link.
                for (nn in next) {
                    if (byCol[c].none { it.links.contains(nn.id) }) {
                        byCol[c].minByOrNull { kotlin.math.abs(it.row - nn.row) }?.links?.add(nn.id)
                    }
                }
            }

            // Fill encounter content.
            val start = byCol[0][0]
            start.explored = true; start.resolved = true; start.owner = Faction.ALLY
            for (s in systems) {
                if (s.encounter == EncounterType.START) continue
                s.danger = (s.col / 2 + sector).coerceIn(1, 6)
                fillEncounter(s, sector, rng)
            }
            return Galaxy(systems, start.id, sector)
        }

        private fun rollEncounter(rng: Random): EncounterType {
            val r = rng.nextFloat()
            return when {
                r < 0.42f -> EncounterType.COMBAT
                r < 0.58f -> EncounterType.ELITE
                r < 0.74f -> EncounterType.STATION
                r < 0.88f -> EncounterType.RESOURCE
                else -> EncounterType.UNKNOWN
            }
        }

        private fun fillEncounter(s: StarSystem, sector: Int, rng: Random) {
            when (s.encounter) {
                EncounterType.COMBAT -> {
                    repeat(1 + s.danger) { s.patrolFleet.add(enemyShipForDanger(s.danger, rng)) }
                    s.owner = Faction.ENEMY
                }
                EncounterType.ELITE -> {
                    s.isAmbush = true
                    repeat(2 + s.danger) { s.patrolFleet.add(enemyShipForDanger(s.danger + 1, rng)) }
                    s.owner = Faction.ENEMY
                    if (rng.nextFloat() < 0.5f) {
                        s.hasStation = true; s.stationFaction = Faction.ENEMY
                        s.stationHealth = 300 + s.danger * 80; s.stationTroops = 2 + s.danger
                        repeat(1) { s.garrisonFleet.add(enemyShipForDanger(s.danger, rng)) }
                    }
                }
                EncounterType.STATION -> {
                    s.owner = Faction.ALLY; s.hasStation = true
                    s.stationFaction = Faction.ALLY; s.stationHealth = 400 + s.danger * 50
                }
                EncounterType.RESOURCE -> {
                    s.rewardCredits = 400 + s.danger * 250 + rng.nextInt(300)
                    s.rewardResearch = 2 + s.danger
                    s.owner = Faction.NEUTRAL
                }
                EncounterType.UNKNOWN -> {
                    s.owner = Faction.NEUTRAL
                }
                EncounterType.BOSS -> {
                    s.owner = Faction.ENEMY
                    s.hasStation = true; s.stationFaction = Faction.ENEMY
                    s.stationHealth = 600 + sector * 200
                    s.stationTroops = 5 + sector * 2
                    s.isAmbush = true
                    // Boss mothership + escorts.
                    s.patrolFleet.add("battleship")
                    repeat(2 + sector) { s.patrolFleet.add(enemyShipForDanger(5, rng)) }
                    repeat(2) { s.garrisonFleet.add(enemyShipForDanger(4, rng)) }
                }
                EncounterType.START -> {}
            }
        }

        private fun enemyShipForDanger(danger: Int, rng: Random): String = when {
            danger >= 5 && rng.nextFloat() < 0.4f -> "battleship"
            danger >= 4 && rng.nextFloat() < 0.5f -> "destroyer"
            danger >= 3 && rng.nextFloat() < 0.5f -> "carrier"
            danger >= 2 -> if (rng.nextBoolean()) "frigate" else "destroyer"
            else -> if (rng.nextBoolean()) "interceptor" else "frigate"
        }
    }
}
