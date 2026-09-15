package com.zoomzo.spacefleet.engine

import com.zoomzo.spacefleet.util.MathUtil

/** 2D camera for the combat world: pan (world center) + zoom. */
class Camera {
    var cx = 0f
    var cy = 0f
    var zoom = 1f
    var minZoom = 0.35f
    var maxZoom = 2.2f
    var viewW = 0f
    var viewH = 0f

    fun setViewport(w: Float, h: Float) { viewW = w; viewH = h }

    fun applyPinch(factor: Float) {
        zoom = MathUtil.clamp(zoom * factor, minZoom, maxZoom)
    }

    /** Pan by a screen-space delta (accounts for zoom). */
    fun panScreen(dxScreen: Float, dyScreen: Float) {
        cx -= dxScreen / zoom
        cy -= dyScreen / zoom
    }

    fun worldToScreenX(wx: Float): Float = (wx - cx) * zoom + viewW / 2f
    fun worldToScreenY(wy: Float): Float = (wy - cy) * zoom + viewH / 2f

    fun screenToWorldX(sx: Float): Float = (sx - viewW / 2f) / zoom + cx
    fun screenToWorldY(sy: Float): Float = (sy - viewH / 2f) / zoom + cy

    fun clampTo(worldW: Float, worldH: Float) {
        cx = MathUtil.clamp(cx, 0f, worldW)
        cy = MathUtil.clamp(cy, 0f, worldH)
    }
}
