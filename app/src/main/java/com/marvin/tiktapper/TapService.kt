package com.marvin.tiktapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

/**
 * Accessibility service that shows a floating control panel and a draggable
 * target ring. While running, it taps at the ring's position.
 */
class TapService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var panel: View? = null
    private var target: View? = null
    private lateinit var panelParams: LP
    private lateinit var targetParams: LP
    private lateinit var startBtn: Button
    private lateinit var counter: TextView

    private var running = false
    private var stopAt = 0L
    private var tapsPerSecond = 5
    private var count = 0

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        addTarget()
        addPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() = stop()

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        panel?.let { wm.removeView(it) }
        target?.let { wm.removeView(it) }
        panel = null; target = null
        return super.onUnbind(intent)
    }

    // ---------- UI ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun overlayParams(x: Int, y: Int) = LP(
        LP.WRAP_CONTENT, LP.WRAP_CONTENT,
        LP.TYPE_ACCESSIBILITY_OVERLAY,
        LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_NO_LIMITS or LP.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

    private fun addTarget() {
        val size = dp(60)
        val ring = TextView(this).apply {
            text = "+"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.RED)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x33FF0000)
                setStroke(dp(3), Color.RED)
            }
        }
        val m = resources.displayMetrics
        targetParams = overlayParams(m.widthPixels / 2 - size / 2, m.heightPixels / 2 - size / 2)
            .apply { width = size; height = size }
        ring.setOnTouchListener(dragger(ring, targetParams))
        wm.addView(ring, targetParams)
        target = ring
    }

    private fun addPanel() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xE6202020.toInt())
            }
        }
        val handle = TextView(this).apply {
            text = "⠿"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(8), 0, dp(8), 0)
        }
        startBtn = Button(this).apply {
            text = "START"
            setOnClickListener { if (running) stop() else start() }
        }
        counter = TextView(this).apply {
            text = "0"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, 0, 0)
        }
        box.addView(handle); box.addView(startBtn); box.addView(counter)

        panelParams = overlayParams(dp(12), dp(160))
        handle.setOnTouchListener(dragger(box, panelParams))
        wm.addView(box, panelParams)
        panel = box
    }

    /** Lets the user drag [window] around by touching the view the listener is attached to. */
    private fun dragger(window: View, params: LP) = object : View.OnTouchListener {
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y; touchX = e.rawX; touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    wm.updateViewLayout(window, params)
                }
            }
            return true
        }
    }

    // ---------- Tapping ----------

    private fun start() {
        val prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
        tapsPerSecond = prefs.getInt(Prefs.TPS, 5).coerceIn(1, 10)
        val minutes = prefs.getInt(Prefs.MINUTES, 10).coerceIn(1, 60)
        stopAt = System.currentTimeMillis() + minutes * 60_000L
        count = 0
        counter.text = "0"
        running = true
        startBtn.text = "STOP"
        setTargetPassThrough(true)
        handler.postDelayed(tick, 300)
    }

    private fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        startBtn.text = "START"
        setTargetPassThrough(false)
    }

    /** While running, the ring must let taps fall through to TikTok underneath it. */
    private fun setTargetPassThrough(on: Boolean) {
        val t = target ?: return
        targetParams.flags = if (on) targetParams.flags or LP.FLAG_NOT_TOUCHABLE
        else targetParams.flags and LP.FLAG_NOT_TOUCHABLE.inv()
        t.alpha = if (on) 0.45f else 1f
        wm.updateViewLayout(t, targetParams)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (System.currentTimeMillis() >= stopAt) { stop(); return }
            tap()
            val base = 1000.0 / tapsPerSecond
            val delay = (base * Random.nextDouble(0.7, 1.3)).toLong().coerceAtLeast(90)
            handler.postDelayed(this, delay)
        }
    }

    private fun tap() {
        val t = target ?: return
        val loc = IntArray(2)
        t.getLocationOnScreen(loc)
        val spread = dp(12)
        val x = loc[0] + t.width / 2f + Random.nextInt(-spread, spread + 1)
        val y = loc[1] + t.height / 2f + Random.nextInt(-spread, spread + 1)
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, Random.nextLong(30, 70))
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
        count++
        counter.text = count.toString()
    }
}
