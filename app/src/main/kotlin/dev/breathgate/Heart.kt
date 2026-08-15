package dev.breathgate

import kotlin.math.exp

/**
 * The heartbeat — ONE LAW, USED TWICE.
 *
 * The flash you see and the thump you hear are the same function evaluated at the same time.
 * They are not two implementations kept in step by discipline; there is only one, so they
 * cannot drift apart. This is also the first piece of this app with no Android in it at all —
 * pure, referentially transparent, and therefore checkable without a device. (The idea is
 * straight out of *Functional Programming in Scala*: keep the laws where they can be stated.)
 *
 * WHERE 60 COMES FROM. A healthy adult's resting heart rate sits in the 60–100 bpm band, and
 * the trained/athletic end runs 40–60. 60 is the low edge of ordinary and the top of trained —
 * the rate a settled body arrives at rather than one it has to reach for. It is also exactly
 * 1 Hz, which puts the beat on the same clock as the seconds being counted, and it sits at the
 * same end of things as coherent breathing (~6 breaths/min, 0.1 Hz), which is the rate that
 * maximises heart-rate variability. The gate is not a medical instrument; it is a pacer, and
 * this is the number worth pacing toward.
 */
object Heart {

    const val DEFAULT_BPM = 60
    const val MIN_BPM = 40
    const val MAX_BPM = 100

    /** A heartbeat is TWO sounds — lub, then dub. One thump reads as a machine. */
    private const val DUB_AT = 0.30f      // where the second sound falls in the interval
    private const val LUB_DECAY = 13f     // how fast each collapses; higher = sharper
    private const val DUB_DECAY = 17f
    private const val DUB_LEVEL = 0.62f   // the second is softer than the first

    /**
     * ⚠ THE ATTACK IS NOT COSMETIC — it is what stops the beat being audible as a CLICK.
     *
     * The first version went 0 → 1 in a single sample at the top of each beat. On screen that is
     * nothing. Through a speaker a step discontinuity in amplitude is broadband noise: it is
     * exactly the static he heard, once per beat, and no amount of filtering downstream removes
     * it because the transient is in the signal itself.
     *
     * ⭐ AND IT IS FIXED HERE, IN THE SHARED LAW — not with a smoother on the audio side. Adding a
     * second envelope for sound would quietly end the thing this file exists to claim: that the
     * flash and the thump are the same function. 18 ms is one frame at 60 fps; the eye cannot
     * see it and the ear cannot do without it.
     */
    private const val ATTACK = 0.018f

    fun intervalSeconds(bpm: Int): Float = 60f / bpm.coerceIn(MIN_BPM, MAX_BPM)

    /** One sound: ramp up over [ATTACK], then fall away. Continuous at both ends. */
    private fun thump(x: Float, decay: Float): Float = when {
        x < 0f -> 0f
        x < ATTACK -> x / ATTACK
        else -> exp(-decay * (x - ATTACK))
    }

    /**
     * The envelope at time `t` seconds — 0 between beats, 1 at the top of the lub.
     * Feeds the visual flash and the synth's thump identically.
     */
    fun envelope(t: Float, bpm: Int): Float {
        val interval = intervalSeconds(bpm)
        val x = ((t % interval) + interval) % interval     // safe for negative t
        val lub = thump(x, LUB_DECAY)
        val dub = DUB_LEVEL * thump(x - DUB_AT * interval, DUB_DECAY)
        return (lub + dub).coerceIn(0f, 1f)
    }
}
