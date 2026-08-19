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
import android.os.PowerManager
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
    private lateinit var power: PowerManager

    private var overlay: View? = null
    private var lastForeground: String? = null

    /**
     * package -> [SystemClock.elapsedRealtime] when it was let through. Drives re-intervention.
     *
     * ⚠ NOT `uptimeMillis()`, which is what this was and which **stops counting in deep sleep**.
     * A 30-minute grace window measured on that clock stays open across a night of standby,
     * because the phone advanced its uptime by a couple of minutes. The gate would simply not
     * appear the next morning, and nothing anywhere would look wrong. `elapsedRealtime()` counts
     * the time the world actually spent.
     */
    private val enteredAt = HashMap<String, Long>()

    /** Inside an app we have already let through — the next decision is minutes away, not frames. */
    private var relaxed = false

    /** Last problem reported in the notification, so it is only rewritten when it changes. */
    private var lastProblem: String? = null
    private var lastHealthAt = 0L

    /**
     * 🐛 2026-08-20, his report: *"not doing the breath check even though it is theoretically on,
     * stopped all of a sudden."* Silent, permanent, and the notification still said healthy.
     *
     * ⚠ THE LOOP MUST NEVER BE ABLE TO STOP. 0.14.0 made it conditional — it only re-posted itself
     * `if (screenOn)`, and `screenOn` was a cached boolean set by a BroadcastReceiver. That gave
     * the entire watcher **one single ignition path**: miss one `ACTION_SCREEN_ON` — a missed
     * broadcast, a stale flag, any cause at all — and the loop is dead until the service is, with
     * every outward sign still reading normal. Before 0.14.0 the loop re-posted unconditionally and
     * therefore *could not* fail this way.
     *
     * Now it always re-posts, and asks [PowerManager] for the screen state at the moment it runs
     * rather than trusting a flag somebody else maintains. The battery win is untouched: the cost
     * was never the empty tick, it was `queryEvents` — and that is what gets skipped while dark.
     *
     * ⚠ Every `post(tick)` MUST be preceded by `removeCallbacks(tick)`. An unconditional re-post is
     * not self-limiting: a second chain would double the poll rate for the life of the process.
     */
    private val tick = object : Runnable {
        override fun run() {
            val awake = runCatching { power.isInteractive }.getOrDefault(true)
            if (awake) {
                try { check() } catch (_: Throwable) { /* a bad poll must never kill the service */ }
                try { health() } catch (_: Throwable) { }
            }
            handler.postDelayed(this, when {
                !awake  -> POLL_DARK
                relaxed -> POLL_RELAXED
                else    -> POLL_ACTIVE
            })
        }
    }

    /**
     * The receiver is now an **accelerator, never the ignition**. Waking straight into a watched
     * app is where a whole poll interval of delay is most visible, so a screen coming on re-checks
     * immediately instead of waiting for the next tick. But the loop no longer depends on it: if
     * every one of these broadcasts were dropped, the watcher would still run. A receiver that
     * cannot be wrong beats a receiver with a correct guard.
     */
    private val screenWatcher = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            // the foreground app may have changed while the screen was dark
            lastForeground = null
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        pal = Palette.current(prefs)
        usage = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        power = getSystemService(Context.POWER_SERVICE) as PowerManager
        startForeground(NOTIF_ID, notification())

        val f = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // These are protected system broadcasts, so NOT_EXPORTED is both correct and required
        // from Android 13 onward.
        androidx.core.content.ContextCompat.registerReceiver(
            this, screenWatcher, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        handler.removeCallbacks(tick)   // invariant: every post pairs with a remove
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenWatcher) }
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
        if (current !in prefs.watched) { relaxed = false; return }

        val since = enteredAt[current]
        // Already inside something we let through: the next decision is minutes away, so stop
        // spending a tight poll on it. Back to the fast rate the moment anything else comes forward.
        relaxed = since != null && !windowExpired(current, since)

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

    /**
     * ⭐ THE SERVICE MUST BE ABLE TO SAY WHY IT IS SILENT.
     *
     * This app has no network, no telemetry and no crash reporter — by design, and that is not
     * changing. The consequence is that **the notification is its only voice**, and on 2026-08-20
     * that voice was saying "One breath before the door opens" while the watcher was doing
     * nothing at all. A gate that fails open and says nothing is indistinguishable from a gate
     * that is working and simply has not been needed yet.
     *
     * Usage Access can be withdrawn — by the user, by a settings sweep, by an OEM cleanup — and
     * `queryEvents` then returns an empty stream rather than throwing. Silence reads exactly like
     * "nothing came to the foreground". So it gets asked directly.
     */
    private fun health() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastHealthAt < HEALTH_EVERY_MS) return
        lastHealthAt = now
        val problem = when {
            !hasUsageAccess()             -> "Usage access is off — tap to fix"
            !Settings.canDrawOverlays(this) -> "Display over other apps is off — tap to fix"
            !prefs.enabled                -> "Paused"
            prefs.watched.isEmpty()       -> "No apps chosen yet"
            else                          -> null
        }
        if (problem == lastProblem) return
        lastProblem = problem
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(problem))
        }
    }

    /** AppOps, not a try/catch around `queryEvents` — a revoked grant returns empty, never throws. */
    private fun hasUsageAccess(): Boolean = runCatching {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val op = android.app.AppOpsManager.OPSTR_GET_USAGE_STATS
        val uid = android.os.Process.myUid()
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ops.unsafeCheckOpNoThrow(op, uid, packageName)
        else
            @Suppress("DEPRECATION") ops.checkOpNoThrow(op, uid, packageName)
        mode == android.app.AppOpsManager.MODE_ALLOWED
    }.getOrDefault(true)   // never let a diagnostic be the thing that breaks the gate

    private fun windowExpired(pkg: String, since: Long): Boolean {
        val minutes = prefs.reinterventionFor(pkg)
        if (minutes <= 0) return true                       // 0 = ask every time
        return SystemClock.elapsedRealtime() - since >= minutes * 60_000L
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

        // ⚠ BACKGROUND BEFORE `addView`, NOT AFTER — this was the second half of the glimpse.
        // Setting it afterwards means the window's first composited frame can be drawn with
        // nothing in it, and a transparent full-screen overlay shows the app underneath. One
        // frame is enough to see, and it lands at the exact instant attention is on the screen.
        container.setBackgroundColor(pal.bg)
        try {
            wm.addView(container, lp)
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
        enteredAt[pkg] = SystemClock.elapsedRealtime()
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
    private fun notification(problem: String? = null): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, "Gate", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val tap = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )
        return b.setContentTitle(if (problem == null) "BreathGate" else "BreathGate isn't watching")
            .setContentText(problem ?: "One breath before the door opens.")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "gate"
        private const val NOTIF_ID = 1
        /**
         * ⭐ HIS REPORT: *"I open an app and it runs + has a glimpse of the app before the gate
         * loads."* Two causes compounding, and this is the larger one.
         *
         * At 350 ms, an app could be up and drawing for a third of a second before we even
         * noticed — and `UsageStatsManager` adds its own latency on top, so the visible gap was
         * worse than the number suggests. **The gate is a doorman; a doorman who arrives after you
         * are through the door is a receipt.**
         *
         * 150 ms while the screen is on, and **nothing at all while it is off** ([screenWatcher]),
         * which is where the budget comes from: the old loop polled all night for no possible
         * benefit. [POLL_RELAXED] then backs off again once we are inside an app already let
         * through, because re-intervention is measured in minutes and does not need frames.
         */
        private const val POLL_ACTIVE = 150L
        private const val POLL_RELAXED = 600L
        /**
         * While the screen is dark. The tick still runs — that is the whole point, it is what
         * lets the loop come back on its own — but it does no work beyond asking whether the
         * screen is on. Four wakeups a minute of one boolean read costs nothing next to the
         * `queryEvents` call it is skipping.
         */
        private const val POLL_DARK = 15_000L
        /** Permissions do not change at 150 ms. */
        private const val HEALTH_EVERY_MS = 30_000L
        /** Long enough to survive a starved tick, short enough to stay cheap to parse. */
        private const val LOOKBACK_MS = 5_000L

        fun start(ctx: Context) {
            val i = Intent(ctx, GateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }
}
