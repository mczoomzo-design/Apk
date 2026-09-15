package com.zoomzo.spacefleet.model

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.random.Random

/** A star system node on the galaxy map. */
class StarSystem(
    val id: Int,
    var name: String,
    val gridX: Int,
    val gridY: Int,
    var worldX: Float,
    var worldY: Float,
    var owner: Faction,
    var hasStation: Boolean,
    var stationFaction: Faction,
    var stationHealth: Int,
    var danger: Int,
    val starColor: Int
) {
    var explored: Boolean = false
    /** Hostile ships patrolling in open space; fought on arrival. */
    val patrolFleet: MutableList<String> = mutableListOf()
    /** Ships defending the station during an invasion. */
    val garrisonFleet: MutableList<String> = mutableListOf()
    /** Troops that must be overcome to capture the station. */
    var stationTroops: Int = 0

    val hasHostilePatrol: Boolean get() = patrolFleet.isNotEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("gx", gridX); put("gy", gridY)
        put("wx", worldX.toDouble()); put("wy", worldY.toDouble())
        put("owner", owner.name); put("station", hasStation)
        put("sf", stationFaction.name); put("sh", stationHealth)
        put("danger", danger); put("color", starColor); put("explored", explored)
        put("patrol", JSONArray(patrolFleet)); put("garrison", JSONArray(garrisonFleet))
        put("troops", stationTroops)
    }

    companion object {
        fun fromJson(o: JSONObject): StarSystem {
            val s = StarSystem(
                o.getInt("id"), o.getString("name"), o.getInt("gx"), o.getInt("gy"),
                o.getDouble("wx").toFloat(), o.getDouble("wy").toFloat(),
                Faction.valueOf(o.getString("owner")), o.getBoolean("station"),
                Faction.valueOf(o.getString("sf")), o.getInt("sh"),
                o.getInt("danger"), o.getInt("color")
            )
            s.explored = o.optBoolean("explored", false)
            s.stationTroops = o.optInt("troops", 0)
            o.optJSONArray("patrol")?.let { for (i in 0 until it.length()) s.patrolFleet.add(it.getString(i)) }
            o.optJSONArray("garrison")?.let { for (i in 0 until it.length()) s.garrisonFleet.add(it.getString(i)) }
            return s
        }
    }
}

/** The galaxy: a scatter of star systems with warp reachability by proximity. */
class Galaxy(
    val cols: Int,
    val rows: Int,
    val systems: MutableList<StarSystem>,
    var currentId: Int
) {
    val cellSize = 190f
    val warpRangeGrid = 2.4f // systems within this grid distance are reachable

    fun system(id: Int): StarSystem = systems.first { it.id == id }
    val current: StarSystem get() = system(currentId)

    fun reachableFrom(id: Int): List<StarSystem> {
        val from = system(id)
        return systems.filter { it.id != id && gridDist(from, it) <= warpRangeGrid }
    }

    fun isReachable(fromId: Int, toId: Int): Boolean =
        reachableFrom(fromId).any { it.id == toId }

    private fun gridDist(a: StarSystem, b: StarSystem): Float =
        hypot((a.gridX - b.gridX).toFloat(), (a.gridY - b.gridY).toFloat())

    fun toJson(): JSONObject = JSONObject().apply {
        put("cols", cols); put("rows", rows); put("current", currentId)
        put("systems", JSONArray().also { arr -> systems.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Galaxy {
            val list = mutableListOf<StarSystem>()
            val arr = o.getJSONArray("systems")
            for (i in 0 until arr.length()) list.add(StarSystem.fromJson(arr.getJSONObject(i)))
            return Galaxy(o.getInt("cols"), o.getInt("rows"), list, o.getInt("current"))
        }

        private val PREFIX = listOf("Al", "Bel", "Cy", "Dra", "Eri", "Fen", "Gor", "Hy", "Ix",
            "Ju", "Kro", "Ly", "My", "Ne", "Or", "Pol", "Qua", "Ry", "Sol", "Tor", "Ur", "Vex", "Zan")
        private val SUFFIX = listOf("dara", "nix", "gon", "tara", "mos", "lith", "var", "pex",
            "rune", "dis", "phon", "kar", "wex", "lor", "tis", "vok")
        private val STAR_COLORS = listOf(
            0xFFFFE9A8.toInt(), 0xFFFFC97A.toInt(), 0xFFFF8E6B.toInt(),
            0xFFAEE0FF.toInt(), 0xFFE0AEFF.toInt(), 0xFFFFFFFF.toInt()
        )

        fun generate(rng: Random = Random(System.nanoTime())): Galaxy {
            val cols = 8; val rows = 6
            val systems = mutableListOf<StarSystem>()
            var idCounter = 0
            // Choose which grid cells host a star (~60% density), always keep home corner.
            for (gy in 0 until rows) {
                for (gx in 0 until cols) {
                    val isHome = gx == 0 && gy == rows - 1
                    if (!isHome && rng.nextFloat() > 0.62f) continue
                    val jitterX = (rng.nextFloat() - 0.5f) * 60f
                    val jitterY = (rng.nextFloat() - 0.5f) * 60f
                    val name = PREFIX.random(rng) + SUFFIX.random(rng) + " " +
                        ('A' + rng.nextInt(9)) + rng.nextInt(9)
                    val sys = StarSystem(
                        id = idCounter++,
                        name = name,
                        gridX = gx, gridY = gy,
                        worldX = 120f + gx * 190f + jitterX,
                        worldY = 120f + gy * 190f + jitterY,
                        owner = Faction.NEUTRAL,
                        hasStation = false,
                        stationFaction = Faction.NEUTRAL,
                        stationHealth = 0,
                        danger = 0,
                        starColor = STAR_COLORS.random(rng)
                    )
                    systems.add(sys)
                }
            }

            val home = systems.first { it.gridX == 0 && it.gridY == rows - 1 }
            home.name = "โซลาริส (ฐานทัพ)"
            home.owner = Faction.ALLY
            home.hasStation = true
            home.stationFaction = Faction.ALLY
            home.stationHealth = 600
            home.explored = true
            home.danger = 0

            // Danger rises with distance from home; assign factions & content.
            val maxDist = systems.maxOf { hypot((it.gridX).toFloat(), (rows - 1 - it.gridY).toFloat()) }
            for (s in systems) {
                if (s.id == home.id) continue
                val d = hypot((s.gridX).toFloat(), (rows - 1 - s.gridY).toFloat())
                s.danger = ((d / maxDist) * 5f).toInt().coerceIn(1, 5)
                val roll = rng.nextFloat()
                when {
                    roll < 0.22f -> { // allied station
                        s.owner = Faction.ALLY; s.hasStation = true
                        s.stationFaction = Faction.ALLY; s.stationHealth = 400 + s.danger * 60
                    }
                    roll < 0.5f -> { // enemy station (invasion targets)
                        s.owner = Faction.ENEMY; s.hasStation = true
                        s.stationFaction = Faction.ENEMY
                        s.stationHealth = 350 + s.danger * 90
                        s.stationTroops = 3 + s.danger * 2
                        repeat(1 + s.danger / 2) { s.garrisonFleet.add(enemyShipForDanger(s.danger, rng)) }
                        repeat(s.danger) { s.patrolFleet.add(enemyShipForDanger(s.danger, rng)) }
                    }
                    roll < 0.68f -> { // pirate den
                        s.owner = Faction.PIRATE
                        repeat(1 + s.danger) { s.patrolFleet.add(pirateShipForDanger(s.danger, rng)) }
                        if (rng.nextFloat() < 0.4f) {
                            s.hasStation = true; s.stationFaction = Faction.PIRATE
                            s.stationHealth = 250 + s.danger * 60; s.stationTroops = 2 + s.danger
                        }
                    }
                    else -> { // neutral / open space, sometimes light patrol
                        s.owner = Faction.NEUTRAL
                        if (rng.nextFloat() < 0.35f) repeat(s.danger) {
                            s.patrolFleet.add(pirateShipForDanger(s.danger - 1, rng))
                        }
                    }
                }
            }
            return Galaxy(cols, rows, systems, home.id)
        }

        private fun enemyShipForDanger(danger: Int, rng: Random): String = when {
            danger >= 5 && rng.nextFloat() < 0.4f -> "battleship"
            danger >= 4 && rng.nextFloat() < 0.5f -> "destroyer"
            danger >= 3 && rng.nextFloat() < 0.5f -> "carrier"
            danger >= 2 -> if (rng.nextBoolean()) "frigate" else "destroyer"
            else -> if (rng.nextBoolean()) "interceptor" else "frigate"
        }

        private fun pirateShipForDanger(danger: Int, rng: Random): String = when {
            danger >= 4 && rng.nextFloat() < 0.35f -> "destroyer"
            danger >= 3 -> if (rng.nextBoolean()) "frigate" else "interceptor"
            danger >= 1 -> if (rng.nextBoolean()) "interceptor" else "scout"
            else -> "scout"
        }
    }
}
