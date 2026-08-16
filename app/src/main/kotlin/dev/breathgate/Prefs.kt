package dev.breathgate

import android.content.Context

/**
 * All settings, on the device. No account, no sync, no server — and no INTERNET permission,
 * so none of this can leave the phone even if something wanted it to.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("breathgate", Context.MODE_PRIVATE)

    // ── which doors ────────────────────────────────────────────────────────
    var watched: Set<String>
        get() = sp.getStringSet(KEY_WATCHED, emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet(KEY_WATCHED, v).apply()

    fun toggle(pkg: String) {
        val next = watched.toMutableSet()
        if (!next.remove(pkg)) next.add(pkg)
        watched = next
    }

    // ── the breath, as a PATTERN not a duration ────────────────────────────
    // "20 seconds" said nothing about what the 20 seconds were. A breath is four counts and
    // a number of rounds; the total is a consequence of the pattern, not the setting itself.
    var inhale: Int
        get() = sp.getInt(K_IN, 4); set(v) = sp.edit().putInt(K_IN, v.coerceIn(1, cap)).apply()
    var holdIn: Int
        get() = sp.getInt(K_H1, 4); set(v) = sp.edit().putInt(K_H1, v.coerceIn(0, cap)).apply()
    var exhale: Int
        get() = sp.getInt(K_OUT, 4); set(v) = sp.edit().putInt(K_OUT, v.coerceIn(1, cap)).apply()
    var holdOut: Int
        get() = sp.getInt(K_H2, 4); set(v) = sp.edit().putInt(K_H2, v.coerceIn(0, cap)).apply()
    var cycles: Int
        get() = sp.getInt(K_CYCLES, 2); set(v) = sp.edit().putInt(K_CYCLES, v.coerceIn(1, if (extreme) 99 else 12)).apply()

    val cycleSeconds: Int get() = inhale + holdIn + exhale + holdOut
    val totalSeconds: Int get() = cycleSeconds * cycles

    /** The named default: box breathing, 4-4-4-4. */
    fun setBox() { inhale = 4; holdIn = 4; exhale = 4; holdOut = 4 }
    fun isBox() = inhale == 4 && holdIn == 4 && exhale == 4 && holdOut == 4

    /** In and out, no holds — for when the box feels like arithmetic. */
    fun setSimple() { inhale = 4; holdIn = 0; exhale = 6; holdOut = 0 }
    fun isSimple() = holdIn == 0 && holdOut == 0

    /**
     * 4-7-8. The long exhale is the point: a breath out longer than the breath in is the part
     * that reaches the parasympathetic side, which is why every calming pattern in every
     * tradition is exhale-weighted. The one to reach for when the gate is meeting something hot.
     */
    fun setCalm() { inhale = 4; holdIn = 7; exhale = 8; holdOut = 0 }
    fun isCalm() = inhale == 4 && holdIn == 7 && exhale == 8 && holdOut == 0

    // ── re-intervention (his word) — PER APP ───────────────────────────────
    // Not "how long the gate stays shut behind you" but "how long before it asks again", and
    // it asks WHILE you are still inside. That is the fix: the old grace window only ever
    // fired when you left and came back, so a long session was never met at all.
    var defaultReinterventionMinutes: Int
        get() = sp.getInt(K_REINT, 15)
        set(v) = sp.edit().putInt(K_REINT, v.coerceIn(0, 240)).apply()

    fun reinterventionFor(pkg: String): Int =
        sp.getInt(K_REINT_PREFIX + pkg, defaultReinterventionMinutes)

    fun setReinterventionFor(pkg: String, minutes: Int) =
        sp.edit().putInt(K_REINT_PREFIX + pkg, minutes.coerceIn(0, 240)).apply()

    fun hasOwnReintervention(pkg: String) = sp.contains(K_REINT_PREFIX + pkg)

    fun clearReinterventionFor(pkg: String) = sp.edit().remove(K_REINT_PREFIX + pkg).apply()

    // ── the words ──────────────────────────────────────────────────────────
    var wordIn: String
        get() = sp.getString(K_W_IN, "breathe in") ?: "breathe in"
        set(v) = sp.edit().putString(K_W_IN, v).apply()
    var wordHold: String
        get() = sp.getString(K_W_HOLD, "hold") ?: "hold"
        set(v) = sp.edit().putString(K_W_HOLD, v).apply()
    var wordOut: String
        get() = sp.getString(K_W_OUT, "let it go") ?: "let it go"
        set(v) = sp.edit().putString(K_W_OUT, v).apply()
    /** The empty hold. It is a HOLD, not a rest — his correction; the word default follows. */
    var wordHoldEmpty: String
        get() = sp.getString(K_W_REST, "hold") ?: "hold"
        set(v) = sp.edit().putString(K_W_REST, v).apply()

    /** The question at the end. His words, not the app's. */
    var askStatement: String
        get() = sp.getString(K_ASK, "Still want to go in?") ?: "Still want to go in?"
        set(v) = sp.edit().putString(K_ASK, v).apply()

    /**
     * How the breath is drawn. 0 orb · 1 bloom · 2 box · 3 guilloche · 4 coral.
     * All of them originate at the centre — that is the one requirement a visual has to meet.
     */
    var visual: Int
        get() = sp.getInt(K_VIS, 0); set(v) = sp.edit().putInt(K_VIS, v.coerceIn(0, 4)).apply()

    /** Which colour set the gate wears. See Palette.ALL. */
    var palette: Int
        get() = sp.getInt(K_PAL, 0)
        set(v) = sp.edit().putInt(K_PAL, v).apply()

    /** A hand-built palette has no voice of its own — this says whose it borrows. See Theme. */
    var customTuning: Int
        get() = sp.getInt(K_TUNING, 0)
        set(v) = sp.edit().putInt(K_TUNING, v.coerceIn(0, Theme.TUNINGS.lastIndex)).apply()

    /** The hand-built palette: seven colours in Palette.ROLES order. */
    var customPalette: List<Int>
        get() {
            val d = Palette.BUILT_IN[0].asList()
            return (0..6).map { sp.getInt(K_CUSTOM + it, d[it]) }
        }
        set(v) = sp.edit().apply { v.forEachIndexed { i, c -> putInt(K_CUSTOM + i, c) } }.apply()

    /**
     * The light gate. Grounds and inks trade places, accents stay — for people who do not want
     * a dark room at midday.
     */
    var lightGate: Boolean
        get() = sp.getBoolean(K_LIGHT, false)
        set(v) = sp.edit().putBoolean(K_LIGHT, v).apply()

    /**
     * The words are optional. Some people want the count and the instruction; some want only
     * the form, and find the words are one more thing to read at the moment they are trying
     * to stop reading things.
     */
    var showWords: Boolean
        get() = sp.getBoolean(K_WORDS, true)
        set(v) = sp.edit().putBoolean(K_WORDS, v).apply()

    /**
     * Colour movement on the moving phases too, not only the holds — the in-breath warms as
     * it fills and the out-breath cools as it empties. Off by default: for some people the
     * colour is the signal, for others it is noise.
     */
    var motionColour: Boolean
        get() = sp.getBoolean(K_MOTIONCOL, false)
        set(v) = sp.edit().putBoolean(K_MOTIONCOL, v).apply()

    /**
     * The heartbeat — the SECOND way the gate can move without changing size, standing beside
     * the colour shift rather than replacing it. The form pulses on a resting pulse, and a body
     * watching a steady beat tends to walk toward it. Combinable with motionColour on purpose.
     */
    var heartbeat: Boolean
        get() = sp.getBoolean(K_HEART, false)
        set(v) = sp.edit().putBoolean(K_HEART, v).apply()

    /** Beats per minute for that pulse. Why 60 is the default: see [Heart]. */
    var restingBpm: Int
        get() = sp.getInt(K_BPM, Heart.DEFAULT_BPM)
        set(v) = sp.edit().putInt(K_BPM, v.coerceIn(Heart.MIN_BPM, Heart.MAX_BPM)).apply()

    /**
     * The theme's voice — a drone in the palette's key, generated on the device.
     * Off by default: sound in a gate is a strong choice and it should be chosen, not arrived at.
     */
    var sound: Boolean
        get() = sp.getBoolean(K_SOUND, false)
        set(v) = sp.edit().putBoolean(K_SOUND, v).apply()

    var soundVolume: Int
        get() = sp.getInt(K_SOUNDVOL, 40)
        set(v) = sp.edit().putInt(K_SOUNDVOL, v.coerceIn(0, 100)).apply()

    /**
     * WHERE THE MELODY SITS — his note, 2026-08-15: *"music is too high pitch for my taste."*
     *
     * The struck bell was pinned two octaves above the drone. That is a defensible place to put a
     * melody and a terrible place to have no choice about: **pitch preference is not a bug to fix
     * once, it is a knob**, and one person's clear is another's shrill.
     *
     * Octaves relative to that original: -2 · -1 · 0 · +1. **Default moved down one**, because the
     * person who has actually listened to it said it was too bright.
     */
    var soundOctave: Int
        get() = sp.getInt(K_SOUNDOCT, -1)
        set(v) = sp.edit().putInt(K_SOUNDOCT, v.coerceIn(-2, 1)).apply()

    /**
     * HOW MUCH LIGHT IS IN IT — a separate axis from pitch, and the distinction matters.
     *
     * A sound can sit low and still be piercing, because "bright" is upper harmonics, not
     * fundamental frequency. This scales the bell's upper partials **and** closes the pad's
     * filter, so turning it down darkens the whole voice rather than just moving it.
     *
     * Two controls instead of one because *"too high"* has two possible causes, and a single
     * knob would fix it for whichever of them the author guessed.
     */
    var soundBrightness: Int
        get() = sp.getInt(K_SOUNDBRIGHT, 45)
        set(v) = sp.edit().putInt(K_SOUNDBRIGHT, v.coerceIn(0, 100)).apply()

    /** Drone only, no struck notes — for when the melody is the thing that is too much. */
    var soundBells: Boolean
        get() = sp.getBoolean(K_SOUNDBELLS, true)
        set(v) = sp.edit().putBoolean(K_SOUNDBELLS, v).apply()

    /**
     * Off, the counts stop at 10 — nobody's ordinary breath is a twenty-second inhale, and a
     * slider that can reach there mostly makes the useful range hard to hit. On, the sliders
     * become plain number fields and the caps come off.
     */
    var extreme: Boolean
        get() = sp.getBoolean(K_EXTREME, false)
        set(v) = sp.edit().putBoolean(K_EXTREME, v).apply()

    val cap: Int get() = if (extreme) 300 else 10

    /**
     * THE SHORT SCREEN, and it is the default.
     *
     * His verdict on the full panel: *"great, but overwhelming."* Both are true — nothing on it
     * is wrong, there is simply too much of it to meet someone with on first open. Simple mode
     * shows the four decisions that matter (a pattern, how many rounds, how it looks, which
     * doors) and one clear way through to everything else.
     *
     * ⚠ Nothing is hidden destructively: every setting the full panel edits keeps whatever value
     * it had, and switching back finds it unchanged. This is a view, not a mode.
     */
    var simpleMode: Boolean
        get() = sp.getBoolean(K_SIMPLE, true)
        set(v) = sp.edit().putBoolean(K_SIMPLE, v).apply()

    var confirmEntry: Boolean
        get() = sp.getBoolean(K_CONFIRM, true)
        set(v) = sp.edit().putBoolean(K_CONFIRM, v).apply()

    var enabled: Boolean
        get() = sp.getBoolean(K_ENABLED, true)
        set(v) = sp.edit().putBoolean(K_ENABLED, v).apply()

    companion object {
        private const val KEY_WATCHED = "watched"
        private const val K_IN = "inhale"
        private const val K_H1 = "hold_in"
        private const val K_OUT = "exhale"
        private const val K_H2 = "hold_out"
        private const val K_CYCLES = "cycles"
        private const val K_REINT = "reintervention"
        private const val K_REINT_PREFIX = "reint_"
        private const val K_W_IN = "w_in"
        private const val K_W_HOLD = "w_hold"
        private const val K_W_OUT = "w_out"
        private const val K_W_REST = "w_rest"
        private const val K_ASK = "ask"
        private const val K_CONFIRM = "confirm"
        private const val K_ENABLED = "enabled"
        private const val K_VIS = "visual"
        private const val K_EXTREME = "extreme"
        private const val K_PAL = "palette"
        private const val K_MOTIONCOL = "motion_colour"
        private const val K_CUSTOM = "custom_col_"
        private const val K_LIGHT = "light_gate"
        private const val K_WORDS = "show_words"
        private const val K_HEART = "heartbeat"
        private const val K_BPM = "resting_bpm"
        private const val K_SOUND = "sound"
        private const val K_SOUNDVOL = "sound_volume"
        private const val K_TUNING = "custom_tuning"
        private const val K_SIMPLE = "simple_mode"
        private const val K_SOUNDOCT = "sound_octave"
        private const val K_SOUNDBRIGHT = "sound_brightness"
        private const val K_SOUNDBELLS = "sound_bells"
    }
}
