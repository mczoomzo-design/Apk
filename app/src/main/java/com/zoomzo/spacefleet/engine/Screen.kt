package com.zoomzo.spacefleet.engine

/** Base class for every full-screen game state (menu, map, combat, ...). */
abstract class Screen(val game: Game) {

    // View metrics captured each frame so input handlers can use screen size / density.
    protected var vw = 0f
    protected var vh = 0f
    protected var dens = 1f

    /** Call at the top of draw() to record the current viewport. */
    protected fun sync(p: Painter) { vw = p.width; vh = p.height; dens = p.scale }

    /** Scale a design unit (mdpi) to device pixels, usable outside draw(). */
    protected fun s(v: Float): Float = v * dens
    protected fun s(v: Int): Float = v * dens

    open fun onEnter() {}
    open fun onExit() {}

    abstract fun update(dt: Float)
    abstract fun draw(p: Painter)

    /** A quick tap with no significant drag. */
    open fun onTap(x: Float, y: Float) {}

    /** Continuous drag/scroll; dx/dy is movement since last event (screen pixels). */
    open fun onDrag(x: Float, y: Float, dx: Float, dy: Float) {}

    /** Pinch zoom; factor > 1 means fingers spreading (zoom in). */
    open fun onPinch(factor: Float, focusX: Float, focusY: Float) {}

    /** Hardware/gesture back. Return true to consume, false to let Game handle it. */
    open fun onBack(): Boolean = false
}
