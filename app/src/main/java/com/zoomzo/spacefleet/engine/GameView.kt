package com.zoomzo.spacefleet.engine

import android.content.Context
import android.graphics.Canvas
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * SurfaceView that runs a fixed-ish timestep game loop on its own thread and
 * translates raw touch input into tap / drag / pinch gestures for the active screen.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    val game = Game(context.applicationContext)
    private val painter = Painter()
    private var thread: LoopThread? = null
    private var densityScale = 1f

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                synchronized(game.lock) { game.onPinch(d.scaleFactor, d.focusX, d.focusY) }
                return true
            }
        })

    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                synchronized(game.lock) { game.onTap(e.x, e.y) }; return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent,
                                  distanceX: Float, distanceY: Float): Boolean {
                if (scaleDetector.isInProgress) return false
                // distanceX/Y are old-minus-new; negate for movement delta.
                synchronized(game.lock) { game.onDrag(e2.x, e2.y, -distanceX, -distanceY) }
                return true
            }
        })

    init {
        holder.addCallback(this)
        densityScale = resources.displayMetrics.density
        isFocusable = true
    }

    fun onGameCreate(bootstrap: (Game) -> Unit) = bootstrap(game)

    override fun surfaceCreated(holder: SurfaceHolder) {
        thread = LoopThread(holder).also { it.running = true; it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        thread?.let {
            it.running = false
            var retry = true
            while (retry) {
                try { it.join(); retry = false } catch (_: InterruptedException) {}
            }
        }
        thread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    fun onPause() { game.persist() }

    /** Called by the Activity on hardware back. */
    fun onBackPressedConsumed(): Boolean = synchronized(game.lock) { game.handleBack() }

    private inner class LoopThread(private val holder: SurfaceHolder) : Thread() {
        @Volatile var running = false
        private var last = System.nanoTime()

        override fun run() {
            while (running) {
                val now = System.nanoTime()
                var dt = (now - last) / 1_000_000_000f
                last = now
                if (dt > 0.05f) dt = 0.05f // clamp after stalls / GC pauses

                synchronized(game.lock) { game.update(dt) }

                var canvas: Canvas? = null
                try {
                    canvas = holder.lockCanvas()
                    if (canvas != null) {
                        synchronized(game.lock) {
                            painter.begin(canvas, canvas.width.toFloat(),
                                canvas.height.toFloat(), densityScale)
                            painter.clear(Palette.bgDeep)
                            game.draw(painter)
                        }
                    }
                } finally {
                    if (canvas != null) holder.unlockCanvasAndPost(canvas)
                }

                // Aim ~60fps; yield the rest of the frame.
                val frameMs = (System.nanoTime() - now) / 1_000_000
                val sleep = 16 - frameMs
                if (sleep > 1) try { sleep(sleep) } catch (_: InterruptedException) {}
            }
        }
    }
}
