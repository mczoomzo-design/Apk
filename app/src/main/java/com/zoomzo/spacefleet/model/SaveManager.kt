package com.zoomzo.spacefleet.model

import android.content.Context
import org.json.JSONObject

/** Persists [GameState] to a JSON file in the app's private storage. */
class SaveManager(private val context: Context) {

    private val file get() = context.filesDir.resolve(SAVE_NAME)

    fun exists(): Boolean = file.exists()

    fun save(state: GameState) {
        try {
            file.writeText(state.toJson().toString())
        } catch (e: Exception) {
            // Saving is best-effort; never crash the game over a failed write.
        }
    }

    fun load(): GameState? {
        return try {
            if (!file.exists()) return null
            val text = file.readText()
            if (text.isBlank()) return null
            GameState.fromJson(JSONObject(text))
        } catch (e: Exception) {
            null
        }
    }

    fun delete() {
        try { if (file.exists()) file.delete() } catch (_: Exception) {}
    }

    companion object { private const val SAVE_NAME = "savegame.json" }
}
