package com.zoomzo.spacefleet.engine

import android.content.Context
import com.zoomzo.spacefleet.model.GameState
import com.zoomzo.spacefleet.model.SaveManager

/**
 * Top-level game controller: owns the persistent [GameState], the active [Screen],
 * a small navigation stack for the Back gesture, and a transient toast overlay.
 */
class Game(val appContext: Context) {

    lateinit var state: GameState
    val save = SaveManager(appContext)

    /** Serializes loop-thread update/draw against UI-thread input. */
    val lock = Any()

    private val stack = ArrayDeque<Screen>()
    val current: Screen? get() = stack.lastOrNull()

    private var pendingAction: (() -> Unit)? = null

    private var toastMsg: String? = null
    private var toastTimer = 0f

    fun startNewGame(difficulty: com.zoomzo.spacefleet.model.Difficulty =
                         com.zoomzo.spacefleet.model.Difficulty.NORMAL) {
        state = GameState.newGame(difficulty)
        save.save(state)
    }

    fun loadOrNew() {
        state = save.load() ?: GameState.newGame()
    }

    fun hasSave(): Boolean = save.exists()

    fun persist() {
        if (::state.isInitialized) save.save(state)
    }

    // ---- Navigation (deferred so we never mutate the stack mid-frame) ----

    fun setRoot(screen: Screen) = defer {
        while (stack.isNotEmpty()) stack.removeLast().onExit()
        stack.addLast(screen); screen.onEnter()
    }

    fun push(screen: Screen) = defer {
        stack.addLast(screen); screen.onEnter()
    }

    fun pop() = defer {
        if (stack.size > 1) {
            stack.removeLast().onExit()
            // Re-entering the revealed screen lets it refresh from state.
            stack.last().onEnter()
        }
    }

    /** Replace the top screen (used for map <-> combat transitions). */
    fun replace(screen: Screen) = defer {
        if (stack.isNotEmpty()) stack.removeLast().onExit()
        stack.addLast(screen); screen.onEnter()
    }

    private fun defer(action: () -> Unit) { pendingAction = action }

    fun handleBack(): Boolean {
        val c = current ?: return false
        if (c.onBack()) return true
        if (stack.size > 1) { pop(); return true }
        return false // let the OS close the app
    }

    // ---- Toast ----

    fun toast(msg: String) { toastMsg = msg; toastTimer = 2.6f }

    // ---- Frame ----

    fun update(dt: Float) {
        pendingAction?.let { it(); pendingAction = null }
        current?.update(dt)
        if (toastTimer > 0f) toastTimer -= dt
    }

    fun draw(p: Painter) {
        current?.draw(p)
        val t = toastMsg
        if (t != null && toastTimer > 0f) {
            val a = (toastTimer / 0.5f).coerceAtMost(1f)
            Ui.toast(p, t, a)
        }
    }

    // ---- Input passthrough to current screen ----
    fun onTap(x: Float, y: Float) { current?.onTap(x, y) }
    fun onDrag(x: Float, y: Float, dx: Float, dy: Float) { current?.onDrag(x, y, dx, dy) }
    fun onPinch(factor: Float, fx: Float, fy: Float) { current?.onPinch(factor, fx, fy) }
}
