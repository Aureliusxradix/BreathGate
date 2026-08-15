package dev.breathgate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The watcher.
 *
 * WHY UsageStatsManager AND NOT AccessibilityService — do not "simplify" this:
 * Play tightened AccessibilityService review with enforcement from 2026-01-28, and Android's
 * Advanced Protection Mode revokes that API from non-accessibility apps outright. Usage Access
 * does this job, needs no accessibility permission, and is not the API under pressure.
 */
class GateService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var pal: Palette
    private lateinit var usage: UsageStatsManager
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var overlay: View? = null
    private var lastForeground: String? = null

    /** package -> uptimeMillis when it was last let through. Drives re-intervention. */
    private val enteredAt = HashMap<String, Long>()

    private val tick = object : Runnable {
        override fun run() {
            try { check() } catch (_: Throwable) { /* a bad poll must never kill the service */ }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        pal = Palette.current(prefs)
        usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, notification())
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    // ── the loop ────────────────────────────────────────────────────────────
    private fun check() {
        if (!prefs.enabled || overlay != null) return

        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - LOOKBACK_MS, now)
        var current: String? = null
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) current = e.packageName
        }
        if (current == null || current == packageName) return

        val entering = current != lastForeground
        lastForeground = current
        if (current !in prefs.watched) return

        val since = enteredAt[current]

        if (entering) {
            // Coming in from outside: gate unless we are still inside the window we granted.
            if (since != null && !windowExpired(current, since)) return
            showGate(current)
            return
        }

        // ── RE-INTERVENTION ────────────────────────────────────────────────
        // Still inside the app. The old grace window only ever fired on re-entry, so a long
        // session was never met at all — which is exactly what "it stays open indefinitely"
        // was describing. Now the door closes behind you on its own schedule.
        if (since != null && windowExpired(current, since)) showGate(current)
    }

    private fun windowExpired(pkg: String, since: Long): Boolean {
        val minutes = prefs.reinterventionFor(pkg)
        if (minutes <= 0) return true                       // 0 = ask every time
        return SystemClock.uptimeMillis() - since >= minutes * 60_000L
    }

    // ── the gate ────────────────────────────────────────────────────────────
    private fun showGate(pkg: String) {
        if (!Settings.canDrawOverlays(this)) return
        // fail open, never crash; and settings may have changed since the service started
        pal = Palette.current(prefs)

        val container = FrameLayout(this)
        val breath = BreathView(this, prefs) {
            if (prefs.confirmEntry) showChoice(container, pkg) else letIn(container, pkg)
        }
        container.addView(breath)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // NO_LIMITS + LAYOUT_IN_SCREEN so the gate covers status and navigation bars too —
            // the old overlay stopped at the insets and leaked the app around the edges.
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // draw into the notch/cutout as well, or it shows as a bright bar
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        try {
            wm.addView(container, lp)
            container.setBackgroundColor(pal.bg)
            overlay = container
        } catch (_: Throwable) {
            overlay = null
        }
    }

    /**
     * The breath is done; now the choice is put in his own words — and in [Pill], which is where
     * the reasoning about these two buttons lives. They were bare text on a transparent
     * background: the two words carrying the entire decision this app exists for, styled like a
     * menu. Now they are real, stacked, palette-aware targets, with the abstaining choice sitting
     * where a thumb reaches without moving.
     */
    private fun showChoice(container: FrameLayout, pkg: String) {
        // 🐛 "here" and "Still want to go in?" were both on screen, at the same coordinates.
        // The word leaves as the question arrives.
        (container.getChildAt(0) as? BreathView)?.releaseTheWord()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val side = Pill.dp(this@GateService, 28f).toInt()
            setPadding(side, 0, side, 0)
        }
        col.addView(TextView(this).apply {
            text = prefs.askStatement
            textSize = 20f
            setTextColor(pal.text)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, Pill.dp(this@GateService, 22f).toInt())
            alpha = 0f
            animate().alpha(1f).setDuration(420).start()
        })

        fun fadeIn(v: View, delay: Long) {
            v.alpha = 0f
            v.animate().alpha(1f).setStartDelay(delay).setDuration(420).start()
        }

        val enter = Pill.make(this, pal, "go in", Pill.ENTER_IS_PRIMARY) { letIn(container, pkg) }
        val away = Pill.make(this, pal, "not now", !Pill.ENTER_IS_PRIMARY) { turnAway() }
        col.addView(enter, Pill.row(this))
        col.addView(away, Pill.row(this, 0f))
        fadeIn(enter, 150)
        fadeIn(away, 260)

        container.addView(
            col,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = Pill.dp(this@GateService, 72f).toInt()
            }
        )
    }

    /** Opening the door: the circle swallows the screen, then the gate lifts. */
    private fun letIn(container: FrameLayout, pkg: String) {
        enteredAt[pkg] = SystemClock.uptimeMillis()
        val breath = container.getChildAt(0) as? BreathView
        // fade the words/buttons out first so they don't sit on top of the expanding circle
        for (i in 1 until container.childCount) {
            container.getChildAt(i).animate().alpha(0f).setDuration(180).start()
        }
        if (breath == null) { removeOverlay(); return }
        breath.openAndThen {
            container.animate().alpha(0f).setDuration(220).withEndAction { removeOverlay() }.start()
        }
    }

    /** Chose not to. Close the gate and put them back on the home screen. */
    private fun turnAway() {
        removeOverlay()
        lastForeground = null
        runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
    }

    // ── the notification the OS requires of a foreground service ────────────
    private fun notification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Gate", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("BreathGate")
            .setContentText("One breath before the door opens.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "gate"
        private const val NOTIF_ID = 1
        private const val POLL_MS = 350L
        private const val LOOKBACK_MS = 10_000L

        fun start(ctx: Context) {
            val i = Intent(ctx, GateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }
}
