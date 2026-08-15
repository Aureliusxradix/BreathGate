package dev.breathgate

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * The overview. One column, sectioned, everything named in words.
 *
 * NoActionBar in the theme — the app was drawing its own title while the action bar drew
 * another, which is what produced the doubled heading and the overlap at the top.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var p: Prefs
    private lateinit var root: LinearLayout
    private lateinit var pal: Palette

    // The overview wears the SAME palette as the gate. It used to take fixed resource colours,
    // so choosing a palette changed the gate and left this screen behind — which read as the
    // setting not having worked.
    private fun col(id: Int): Int = when (id) {
        R.color.bg -> pal.bg
        R.color.surface -> Palette.mix(pal.bg, pal.text, 0.06f)
        R.color.ink -> pal.text
        R.color.ink_soft -> pal.textSoft
        R.color.accent -> pal.ring
        else -> ContextCompat.getColor(this, id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        pal = Palette.current(p)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 140)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(col(R.color.bg))
            addView(root)
            clipToPadding = false
        }
        setContentView(scroll)
        // The title was hugging the bezel because nothing accounted for the status bar.
        // Take the real inset rather than guessing at a padding number.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(v.paddingLeft, top + 56, v.paddingRight, v.paddingBottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        render()
        if (ready()) GateService.start(this)
    }

    private fun ready() = hasUsageAccess() && Settings.canDrawOverlays(this)

    // ═══ the screen ═════════════════════════════════════════════════════════
    private fun render() {
        pal = Palette.current(p)
        root.removeAllViews()
        (root.parent as? View)?.setBackgroundColor(pal.bg)

        header()
        lede("One breath before the door opens.")

        if (!ready()) { permissions(); return }

        if (p.simpleMode) simple() else full()
        sectionSupport()

        gap(48)
        fine("No internet permission. Nothing this app knows can leave the phone — check the manifest yourself.")
    }

    /**
     * SIMPLE — the default face, and the answer to his *"great, but overwhelming."*
     *
     * Both halves of that were true: nothing on the full panel is wrong, there is simply too much
     * of it to meet someone with on first open. This screen holds the four decisions that change
     * what actually happens — **a pattern · how many rounds · how it looks · which doors** — and
     * one clear way through to the rest.
     *
     * ⚠ Nothing is hidden destructively. Every setting the full panel edits keeps whatever value
     * it had, and going through finds it unchanged. **A view, not a mode.**
     */
    private fun simple() {
        gap(44); heading("The breath")
        val summary = TextView(this).apply {
            textSize = 21f; setTextColor(col(R.color.accent)); setPadding(0, 6, 0, 2)
        }
        val totalLine = TextView(this).apply { textSize = 13f; setTextColor(col(R.color.ink_soft)) }
        fun refresh() {
            summary.text = "${p.inhale} in · ${p.holdIn} hold · ${p.exhale} out · ${p.holdOut} hold"
            totalLine.text = "${patternName()} · ${p.cycles} round${if (p.cycles == 1) "" else "s"} · ${p.totalSeconds}s in total"
        }
        refresh()
        root.addView(summary); root.addView(totalLine)

        gap(18)
        presetRow()
        count("Rounds", p.cycles, 1, 12) { p.cycles = it; refresh() }

        gap(44); heading("How it looks")
        visualRow()
        gap(24); paletteRow()

        gap(30)
        action("▶  Test run") { startActivity(Intent(this, TestRunActivity::class.java)) }
        fine("Runs the gate right now with these settings — no waiting to catch yourself opening something.")

        gap(44); heading("The doors")
        appList(withTiming = false)

    }

    /** EXTENSIVE — every knob, unchanged. Reached from the header, left the same way. */
    private fun full() {
        sectionBreath()
        sectionWords()
        sectionDoors()
    }

    /**
     * THE HEADER — the name, and the way through, on one line.
     *
     * The switch used to be a full-width pill at the *bottom* of the simple screen, which put the
     * door out of the room: you had to scroll past everything to find the thing that shows you
     * more. ⭐ **Top right, beside the name, is where a person looks for "and the rest"** — his
     * placement, and it is the right one. One word — **Extras** — because a label that has to
     * explain itself is a label doing the screen's job.
     */
    private fun header() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = "BreathGate"; textSize = 32f; setTextColor(col(R.color.ink))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        if (ready()) {
            row.addView(Button(this).apply {
                text = if (p.simpleMode) "Extras" else "Done"
                isAllCaps = false; textSize = 16f
                setTextColor(col(R.color.accent))
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // a real target beside a 32sp title, not a word floating next to it
                minWidth = Pill.dp(this@MainActivity, 76f).toInt()
                minHeight = Pill.dp(this@MainActivity, 48f).toInt()
                setPadding(Pill.dp(this@MainActivity, 12f).toInt(), 0, 0, 0)
                setOnClickListener { p.simpleMode = !p.simpleMode; render() }
            })
        }
        root.addView(row)
    }

    private fun patternName() = when {
        p.isBox() -> "box breathing"
        p.isCalm() -> "4-7-8 — the calm one"
        p.isSimple() -> "simple"
        else -> "your own"
    }

    // ── the gift, and the line back ────────────────────────────────────────
    private fun sectionSupport() {
        gap(52); heading("Support")
        body(
            if (Flavour.DONATIONS)
                "This app is free and stays free. If it has been worth something, there is a way to send something back — and a way to ask for what it is still missing."
            else
                "Almost everything in this app came from someone using it and saying what was wrong. There is a line for that."
        )
        gap(16)
        root.addView(
            Pill.make(this, pal, if (Flavour.DONATIONS) "The gift · ask for something" else "Ask for something", primary = false) {
                startActivity(Intent(this, SupportActivity::class.java))
            },
            Pill.row(this, 0f)
        )
    }

    private fun permissions() {
        gap(28)
        card(
            "If a switch looks greyed out, Android is holding it shut because this app was sideloaded:\n\n" +
                "Settings → Apps → BreathGate → ⋮ (top right) → Allow restricted settings\n\n" +
                "Installing through Obtainium or F-Droid skips this step."
        )
        if (!hasUsageAccess()) {
            gap(24)
            body("Usage access — how the gate notices which app just came forward.")
            action("Grant usage access") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        if (!Settings.canDrawOverlays(this)) {
            gap(24)
            body("Display over other apps — how the breath appears in front.")
            action("Allow drawing over apps") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }
    }

    // ── the breath ─────────────────────────────────────────────────────────
    /**
     * The ways of drawing a breath — A GRID, not a scrolling row.
     *
     * 🐛 **Coral was invisible.** Five names did not fit a phone's width, so the row scrolled —
     * and a horizontal scroller with no visible edge is a UI that hides its last item from
     * everyone who does not already know it is there. He built the feature and then could not
     * find it. **The failure was not "hard to reach", it was "no reason to believe there is more".**
     *
     * Four to a line, wrapping. Nothing scrolls, everything is on screen at once, and the grid
     * has room for the next several visuals without this decision being revisited. Selection is a
     * filled chip rather than a leading dot — a dot costs the width of a character on a cell that
     * has none to spare, and a filled shape reads as *chosen* from further away than a marker does.
     */
    private fun visualRow() {
        val modes = listOf("orb" to 0, "bloom" to 1, "box" to 2, "guilloche" to 3, "coral" to 4)
        val grid = GridLayout(this).apply { columnCount = GRID_COLS }
        modes.forEach { (name, id) ->
            val on = p.visual == id
            grid.addView(TextView(this).apply {
                text = name
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(if (on) Palette.inkOn(pal.ring, pal) else col(R.color.ink_soft))
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = Pill.dp(this@MainActivity, 10f)
                    setColor(if (on) pal.ring else android.graphics.Color.TRANSPARENT)
                    if (!on) setStroke(2, Palette.mix(pal.bg, pal.text, 0.16f))
                }
                setPadding(0, Pill.dp(this@MainActivity, 12f).toInt(), 0, Pill.dp(this@MainActivity, 12f).toInt())
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4, 4, 4, 4)
                }
                setOnClickListener { p.visual = id; render() }
            })
        }
        root.addView(grid)
        fine("All five originate at the centre. Orb fills and empties · bloom travels outward in rings · box draws one edge per phase · guilloche is the rosette from the card mark · coral is a lung, its tubes filling with air from the middle out.")
    }

    /**
     * The three named patterns. **4-7-8 is here because the exhale-weighted one is the one that
     * actually calms** — a breath out longer than the breath in is the part that reaches the
     * parasympathetic side, and it was missing from a screen whose whole job is to offer the
     * useful choice without explaining itself.
     */
    private fun presetRow() {
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun preset(label: String, on: Boolean, apply: () -> Unit) = Button(this).apply {
            text = if (on) "● $label" else label
            isAllCaps = false; textSize = 14f
            setTextColor(col(if (on) R.color.accent else R.color.ink_soft))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(0, 0, 40, 0)
            setOnClickListener { apply(); render() }
        }
        presets.addView(preset("box 4·4·4·4", p.isBox()) { p.setBox() })
        presets.addView(preset("calm 4·7·8", p.isCalm()) { p.setCalm() })
        presets.addView(preset("simple 4·6", p.isSimple() && !p.isCalm()) { p.setSimple() })
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(presets)
        })
    }

    /** The colour sets, and the hand-built one on the end. */
    private fun paletteRow() {
        val palRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Palette.BUILT_IN.forEachIndexed { i, b ->
            palRow.addView(TextView(this).apply {
                text = if (p.palette == i) "●" else "○"
                textSize = 30f
                setTextColor(b.ring)
                setPadding(0, 0, 34, 0)
                setOnClickListener { p.palette = i; render() }
            })
        }
        palRow.addView(TextView(this).apply {
            text = if (p.palette == Palette.CUSTOM) "●" else "○"
            textSize = 30f
            setTextColor(Palette.fromInts(p.customPalette).ring)
            setPadding(0, 0, 34, 0)
            setOnClickListener { p.palette = Palette.CUSTOM; render() }
        })
        root.addView(palRow)
        fine("Palette: ${Palette.current(p).name}. The last dot is your own.")
        action("Build a palette…") { startActivity(Intent(this, PaletteActivity::class.java)) }
    }

    // ── the extensive panel ────────────────────────────────────────────────
    private fun sectionBreath() {
        gap(48); heading("How it looks")
        visualRow()
        gap(26)
        paletteRow()

        gap(14)
        root.addView(CheckBox(this).apply {
            text = "Light gate"
            setTextColor(col(R.color.ink))
            isChecked = p.lightGate
            setOnCheckedChangeListener { _, v -> p.lightGate = v; render() }
        })
        fine("Grounds and inks trade places; the accents stay. For when a dark room at midday is the wrong room.")

        gap(20)
        root.addView(CheckBox(this).apply {
            text = "Colour moves on the breath too"
            setTextColor(col(R.color.ink))
            isChecked = p.motionColour
            setOnCheckedChangeListener { _, v -> p.motionColour = v }
        })
        fine("Holds always shift colour. With this on, the in-breath warms as it fills and the out-breath cools as it empties.")

        // The heartbeat stands BESIDE the colour shift, not instead of it — two ways for the form
        // to move without changing size. Both can be on; they do different work.
        gap(20)
        root.addView(CheckBox(this).apply {
            text = "Heartbeat"
            setTextColor(col(R.color.ink))
            isChecked = p.heartbeat
            setOnCheckedChangeListener { _, v -> p.heartbeat = v; render() }
        })
        fine("The form pulses on a steady beat — lub, then dub. A body watching a slow pulse tends to walk toward it.")
        if (p.heartbeat) {
            count(
                "    ↳ beats per minute", p.restingBpm, Heart.MIN_BPM, Heart.MAX_BPM,
                unit = { "$it bpm" }
            ) { p.restingBpm = it }
            fine("60 is the default: the low edge of an ordinary resting rate (60–100) and the top of a trained one (40–60) — the rate a settled body arrives at. It is also exactly one beat a second.")
        }

        gap(28); heading("The theme's voice")
        root.addView(CheckBox(this).apply {
            text = "Sound"
            setTextColor(col(R.color.ink))
            isChecked = p.sound
            setOnCheckedChangeListener { _, v -> p.sound = v; render() }
        })
        fine("A drone in the palette's own key, generated on this phone — no file, no download, and still no internet permission. It climbs as you fill and falls as you empty. With the heartbeat on, you hear the same pulse you see.")
        if (p.sound) {
            count("    ↳ volume", p.soundVolume, 0, 100, unit = { "$it%" }) { p.soundVolume = it }
            val t = Theme.forPrefs(p)
            fine("Now playing in ${t.name} — ${"%.0f".format(t.rootHz)} Hz. Each palette has its own.")
            if (p.palette == Palette.CUSTOM) {
                gap(10)
                body("Your own palette has no key of its own. Whose does it borrow?")
                val voices = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                Theme.TUNINGS.forEachIndexed { i, tune ->
                    voices.addView(Button(this).apply {
                        text = if (p.customTuning == i) "● ${tune.name}" else tune.name
                        isAllCaps = false; textSize = 14f
                        setTextColor(col(if (p.customTuning == i) R.color.accent else R.color.ink_soft))
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setPadding(0, 0, 34, 0)
                        setOnClickListener { p.customTuning = i; render() }
                    })
                }
                root.addView(HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(voices)
                })
            }
        }

        gap(26)
        action("▶  Test run") { startActivity(Intent(this, TestRunActivity::class.java)) }
        fine("Runs the gate right now with these settings — no waiting to catch yourself opening something.")

        gap(44); heading("The breath")

        val summary = TextView(this).apply {
            textSize = 20f; setTextColor(col(R.color.accent)); setPadding(0, 6, 0, 2)
        }
        val totalLine = TextView(this).apply { textSize = 13f; setTextColor(col(R.color.ink_soft)) }
        fun refresh() {
            summary.text = "${p.inhale} in · ${p.holdIn} hold · ${p.exhale} out · ${p.holdOut} hold"
            totalLine.text = "${patternName()} · ${p.cycles} round${if (p.cycles == 1) "" else "s"} · ${p.totalSeconds}s in total"
        }
        refresh()
        root.addView(summary); root.addView(totalLine)

        gap(18)
        presetRow()

        root.addView(CheckBox(this).apply {
            text = "Extreme mode"
            setTextColor(col(R.color.ink))
            isChecked = p.extreme
            setOnCheckedChangeListener { _, v -> p.extreme = v; render() }
        })
        fine(if (p.extreme) "Counts are plain number fields and the caps are off."
             else "Counts stop at ${p.cap}s. Turn this on to type any number instead.")

        count("Breathe in", p.inhale, 1, p.cap) { p.inhale = it; refresh() }
        count("Hold full", p.holdIn, 0, p.cap) { p.holdIn = it; refresh() }
        count("Breathe out", p.exhale, 1, p.cap) { p.exhale = it; refresh() }
        count("Hold empty", p.holdOut, 0, p.cap) { p.holdOut = it; refresh() }
        count("Rounds", p.cycles, 1, if (p.extreme) 99 else 12) { p.cycles = it; refresh() }
    }

    // ── words ──────────────────────────────────────────────────────────────
    private fun sectionWords() {
        gap(48); heading("The words")
        root.addView(CheckBox(this).apply {
            text = "Show the words"
            setTextColor(col(R.color.ink))
            isChecked = p.showWords
            setOnCheckedChangeListener { _, v -> p.showWords = v; render() }
        })
        fine("Off, only the form and the count remain — for when reading is the thing you are trying to stop doing. The count sits inside the form either way.")
        if (!p.showWords) return
        gap(8)
        body("What the gate says while you breathe, and what it asks at the end.")
        field("On the in-breath", p.wordIn) { p.wordIn = it }
        field("On the hold", p.wordHold) { p.wordHold = it }
        field("On the out-breath", p.wordOut) { p.wordOut = it }
        field("On the empty hold", p.wordHoldEmpty) { p.wordHoldEmpty = it }
        gap(16)
        field("The question at the end", p.askStatement) { p.askStatement = it }
        gap(12)
        root.addView(CheckBox(this).apply {
            text = "Ask before letting me in"
            setTextColor(col(R.color.ink))
            isChecked = p.confirmEntry
            setOnCheckedChangeListener { _, v -> p.confirmEntry = v }
        })
        fine("With this on, the breath ends with a choice. Off, and the door simply opens when the breath is done.")
    }

    // ── doors + per-app re-intervention ────────────────────────────────────
    private fun sectionDoors() {
        gap(48); heading("The doors")

        count(
            "Re-intervention — ask again after", p.defaultReinterventionMinutes, 0, 120,
            unit = { if (it == 0) "every time" else "$it min" }
        ) { p.defaultReinterventionMinutes = it }
        fine("The default for every door. While you are inside an app, the gate returns after this long — not only when you come back to it.")

        gap(24)
        appList(withTiming = true)
    }

    /**
     * The doors. `withTiming` is the whole difference between the two faces: simple mode asks
     * only *which apps*, the extensive panel also offers each one its own re-intervention clock.
     */
    private fun appList(withTiming: Boolean) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { it.packageName != packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        body(when {
            apps.size <= 3 -> "Only ${apps.size} apps visible — reinstall this version if that looks wrong."
            withTiming -> "${apps.size} apps. Tick the ones worth a breath; a ticked one gets its own timing."
            else -> "${apps.size} apps. Tick the ones worth a breath."
        })

        apps.forEach { app ->
            val pkg = app.packageName
            val watched = pkg in p.watched
            root.addView(CheckBox(this).apply {
                text = pm.getApplicationLabel(app).toString()
                setTextColor(col(R.color.ink))
                isChecked = watched
                setOnCheckedChangeListener { _, _ -> p.toggle(pkg); render() }
            })
            if (watched && withTiming) {
                count(
                    "    ↳ ask again after", p.reinterventionFor(pkg), 0, 120,
                    unit = { if (it == 0) "every time" else "$it min" }
                ) { p.setReinterventionFor(pkg, it) }
            }
        }
    }

    // ═══ building blocks ════════════════════════════════════════════════════
    private fun title(s: String) = root.addView(TextView(this).apply {
        text = s; textSize = 32f; setTextColor(col(R.color.ink)); setPadding(0, 0, 0, 4)
    })

    private fun heading(s: String) = root.addView(TextView(this).apply {
        text = s; textSize = 13f; setTextColor(col(R.color.ink_soft))
        letterSpacing = 0.14f; setPadding(0, 0, 0, 10)
        text = s.uppercase()
    })

    private fun lede(s: String) = root.addView(TextView(this).apply {
        text = s; textSize = 15f; setTextColor(col(R.color.ink_soft))
    })

    private fun body(s: String) = root.addView(TextView(this).apply {
        text = s; textSize = 15f; setTextColor(col(R.color.ink)); setPadding(0, 4, 0, 8)
    })

    private fun fine(s: String) = root.addView(TextView(this).apply {
        text = s; textSize = 13f; setTextColor(col(R.color.ink_soft)); setPadding(0, 6, 0, 0)
    })

    private fun card(s: String) = root.addView(TextView(this).apply {
        text = s; textSize = 14f; setTextColor(col(R.color.ink))
        setBackgroundColor(col(R.color.surface)); setPadding(40, 36, 40, 36)
    })

    private fun action(s: String, onClick: () -> Unit) = root.addView(Button(this).apply {
        text = s; isAllCaps = false
        setOnClickListener { onClick() }
    })

    private fun gap(h: Int) = root.addView(View(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
    })

    /** A named count with its value in words — never a bare position on a bar. */
    private fun count(
        label: String,
        current: Int,
        min: Int,
        max: Int,
        unit: (Int) -> String = { "$it" },
        onChange: (Int) -> Unit
    ) {
        if (p.extreme) { numberRow(label, current, min, onChange); return }
        gap(16)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val name = TextView(this).apply {
            text = label; textSize = 15f; setTextColor(col(R.color.ink))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val value = TextView(this).apply {
            text = unit(current); textSize = 17f; setTextColor(col(R.color.accent))
        }
        row.addView(name); row.addView(value)
        root.addView(row)
        root.addView(SeekBar(this).apply {
            this.max = max - min
            progress = (current - min).coerceIn(0, max - min)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val v = prog + min
                    value.text = unit(v)
                    onChange(v)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })
    }

    /** Extreme mode: type the number. No bar, no ceiling worth arguing with. */
    private fun numberRow(label: String, current: Int, min: Int, onChange: (Int) -> Unit) {
        gap(14)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            text = label; textSize = 15f; setTextColor(col(R.color.ink))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(EditText(this).apply {
            setText(current.toString())
            textSize = 17f
            setTextColor(col(R.color.accent))
            inputType = InputType.TYPE_CLASS_NUMBER
            width = 260
            setOnFocusChangeListener { _, focused ->
                if (!focused) onChange(text.toString().toIntOrNull()?.coerceAtLeast(min) ?: current)
            }
        })
        root.addView(row)
    }

    private fun field(label: String, current: String, onChange: (String) -> Unit) {
        gap(14)
        root.addView(TextView(this).apply {
            text = label; textSize = 13f; setTextColor(col(R.color.ink_soft))
        })
        root.addView(EditText(this).apply {
            setText(current)
            textSize = 16f
            setTextColor(col(R.color.ink))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setOnFocusChangeListener { _, focused -> if (!focused) onChange(text.toString().trim()) }
        })
    }

    private companion object {
        /** Four to a line. Room for the visuals that are not built yet, without a redesign. */
        const val GRID_COLS = 4
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        ) == AppOpsManager.MODE_ALLOWED
    }
}
