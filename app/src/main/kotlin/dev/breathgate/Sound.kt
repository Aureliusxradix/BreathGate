package dev.breathgate

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * THE VOICE OF THE GATE — generated, not played.
 *
 * Every sample here is computed on this device from [Tuning]. There is no audio file, no asset,
 * no licence, no download and no INTERNET permission — the count of places anything can leave
 * the phone stays at zero, and playback needs no permission of any kind, so `release.sh`'s two
 * invariants are untouched by all of this.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * REBUILT 2026-08-15 on his verdict: **"kinda shit — spiky, a kinda static, not really relaxing,
 * the heartbeat clips the audio."** Every word of that was a real defect with a findable cause.
 *
 *   "STATIC"          the heartbeat envelope stepped 0 → 1 in one sample. A step discontinuity in
 *                     amplitude is broadband noise — a click, once per beat. **Fixed in [Heart]
 *                     itself** with an 18 ms attack, so the flash and the thump stay one function.
 *   "CLIPS"           the layers summed past 1.0 and met a hard `coerceIn`. Hard clipping is the
 *                     harshest thing a synth can do. Now every layer has a **budget** so the sum
 *                     lands near 0.85, the heart **ducks the pad** under it (which is what makes a
 *                     beat sound clean rather than crushed), and the master ends in a **soft
 *                     saturator** instead of a clamp — nothing can hard-clip any more.
 *   "SPIKY"           a single sine gliding continuously up and down an octave above the drone is
 *                     a siren, not music. **Gone.** In its place: bells.
 *   "NOT RELAXING"    → **more melodic**, which is what he asked for. A soft pad of detuned
 *                     partials, a low-pass that opens as you fill and closes as you empty, and a
 *                     **struck bell on each phase change** walking the tuning's degrees, with a
 *                     long tail into a small reverb. The melody is the breath pattern itself.
 *
 * ⚠ STILL UNTESTED BY EAR at the time of writing. Everything above is a fix for a named defect
 * with a mechanism, not a claim that it sounds good. `Unfalsified`, not `Proven`.
 * ─────────────────────────────────────────────────────────────────────────────
 */
class BreathSound(
    context: Context,
    private val tuning: Tuning,
    private val bpm: Int,
    private val heartOn: Boolean,
    volumePercent: Int,
    /** Octaves to shift the struck notes, relative to two above the drone. See Prefs.soundOctave. */
    octave: Int = 0,
    /** 0 dark … 100 bright. Upper partials AND the pad's filter. See Prefs.soundBrightness. */
    brightness: Int = 45,
    private val bellsOn: Boolean = true
) {

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val gain = (volumePercent.coerceIn(0, 100) / 100f) * 0.9f

    /** ×4 was the original fixed position — two octaves above the drone. Now the middle of a range. */
    private val bellMul = 4f * Math.pow(2.0, octave.coerceIn(-2, 1).toDouble()).toFloat()

    /**
     * 0 → 2. At 50 everything sits where it was originally voiced, so the control reads as
     * "darker than / brighter than what you already know" rather than as an absolute scale.
     */
    private val bright = (brightness.coerceIn(0, 100) / 50.0)

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private var focus: AudioFocusRequest? = null

    @Volatile private var running = false
    @Volatile private var releasing = false

    /** Written by the view every frame; read by the audio thread. Deliberately lock-free. */
    @Volatile private var targetOpen = 0.4f
    /** A note waiting to be struck, in Hz. 0 = nothing pending. Cleared by the audio thread. */
    @Volatile private var pendingNote = 0f
    private var lastIndex = -1

    // ── the controls the view turns ─────────────────────────────────────────
    /**
     * Called on each drawn frame. The phase INDEX is what triggers a note — not the kind, which
     * is ambiguous when a pattern has two holds. A change of index is unambiguously a new phase,
     * so exactly one bell is struck per phase, and the four phases of a cycle play four degrees
     * of the tuning in order. **The melody is the breath pattern.**
     */
    fun update(open: Float, kind: Int, within: Float, index: Int) {
        targetOpen = open
        if (index != lastIndex) {
            lastIndex = index
            if (!bellsOn) return
            val d = tuning.degrees
            // `bellMul` sits the note above the pad without muddying it. Originally pinned at ×4 —
            // two octaves up, a 440 Hz bell from a 110 Hz root — which was too bright for the only
            // person who had heard it. Now the middle of a four-position range.
            pendingNote = tuning.rootHz * bellMul * Theme.ratio(d[index % d.size].toFloat())
        }
    }

    // ── lifecycle ───────────────────────────────────────────────────────────
    fun start() {
        if (running) return
        runCatching {
            requestFocus()
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val minBuf = AudioTrack.getMinBufferSize(
                RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(BLOCK * 4)
            val t = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            t.play()
            track = t
            running = true
            thread = Thread { runCatching { render(t) } }.apply { priority = Thread.MAX_PRIORITY; start() }
        }.onFailure { stop() }
    }

    /**
     * Stops on a fade, not a cut. `releasing` asks the render loop to walk the master gain to
     * zero and leave; we give it a moment, then tear down whatever is left regardless — a stuck
     * audio thread must never hold the gate open.
     */
    fun stop() {
        if (!running && track == null) { abandonFocus(); return }
        releasing = true
        runCatching { thread?.join(300) }
        running = false
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.release() }
        track = null
        thread = null
        abandonFocus()
    }

    // ── focus ───────────────────────────────────────────────────────────────
    /**
     * The gate appears OVER whatever was playing. Transient-may-duck is the honest request: turn
     * the other thing down for one breath, hand it straight back. Taking full focus would stop
     * someone's music every time they opened a watched app, which is a different app's behaviour.
     */
    private fun requestFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setWillPauseWhenDucked(false)
            .build()
        focus = req
        runCatching { am.requestAudioFocus(req) }
    }

    private fun abandonFocus() {
        focus?.let { f -> runCatching { am.abandonAudioFocusRequest(f) } }
        focus = null
    }

    // ── the bells ───────────────────────────────────────────────────────────
    /**
     * One struck note. Three partials on SEPARATE phase accumulators — the third is deliberately
     * inharmonic (×3.01), which is most of what makes a bell sound like a bell rather than an
     * organ, and an inharmonic partial cannot share a wrapped accumulator with the fundamental
     * without stepping every time that accumulator wraps. That step would be another click.
     */
    private class Bell {
        var t = -1.0            // seconds since strike; < 0 means idle
        var f = 0.0
        var p1 = 0.0; var p2 = 0.0; var p3 = 0.0
    }

    private val bells = Array(BELL_VOICES) { Bell() }
    private var nextBell = 0

    private fun strike(freq: Float) {
        val b = bells[nextBell]
        nextBell = (nextBell + 1) % BELL_VOICES
        b.t = 0.0; b.f = freq.toDouble(); b.p1 = 0.0; b.p2 = 0.0; b.p3 = 0.0
    }

    /** Slow in, long out, and faded to nothing before the voice is recycled. */
    private fun bellEnv(t: Double): Double {
        if (t < 0 || t > BELL_LIFE) return 0.0
        val rise = if (t < BELL_ATTACK) t / BELL_ATTACK else 1.0
        val fall = exp(-(t - BELL_ATTACK).coerceAtLeast(0.0) / BELL_DECAY)
        val tail = ((BELL_LIFE - t) / 0.35).coerceIn(0.0, 1.0)   // no cut at the end of its life
        return rise * fall * tail
    }

    // ── a small room ────────────────────────────────────────────────────────
    /**
     * Three comb filters and one allpass — the smallest arrangement that reads as a room rather
     * than an echo. A single delay line would slap a distinct repeat behind every bell; the point
     * of a reverb is that you cannot count the repeats. Damped inside the loop so the tail loses
     * its highs as it decays, which is what a soft room does and what stops a long tail turning
     * into a ringing tone. **The heartbeat does NOT go through it** — a thump wants to be dry and
     * close, and a wet one is mud.
     */
    private val comb = arrayOf(FloatArray(1116), FloatArray(1188), FloatArray(1356))
    private val combIdx = IntArray(3)
    private val combLp = FloatArray(3)
    private val allpass = FloatArray(556)
    private var apIdx = 0

    private fun room(x: Float): Float {
        var acc = 0f
        for (i in 0 until 3) {
            val buf = comb[i]
            val out = buf[combIdx[i]]
            combLp[i] = out * (1f - COMB_DAMP) + combLp[i] * COMB_DAMP
            buf[combIdx[i]] = x + combLp[i] * COMB_FEEDBACK
            combIdx[i] = (combIdx[i] + 1) % buf.size
            acc += out
        }
        acc /= 3f
        val d = allpass[apIdx]
        val out = -acc + d
        allpass[apIdx] = acc + d * AP_FEEDBACK
        apIdx = (apIdx + 1) % allpass.size
        return out
    }

    // ── the render loop ─────────────────────────────────────────────────────
    private fun render(t: AudioTrack) {
        val buf = ShortArray(BLOCK)
        val root = tuning.rootHz.toDouble()

        // pad: three partials, each a pair detuned a hair apart so they beat slowly against one
        // another. That slow beating is the whole difference between "a sine" and "warm".
        val padF = doubleArrayOf(root, root * 1.5, root * 2.0)
        val padW = doubleArrayOf(1.0, 0.55, 0.34)
        val padP = DoubleArray(6)

        var pHeart = 0.0
        var open = targetOpen.toDouble()
        var lp = 0.0
        var master = 0f
        var seconds = 0.0

        while (running) {
            for (i in buf.indices) {
                master += ((if (releasing) 0f else 1f) - master) * FADE
                open += (targetOpen - open) * OPEN_GLIDE      // per-sample, or it zippers

                pendingNote.let { if (it > 0f) { strike(it); pendingNote = 0f } }

                // ── the pad ─────────────────────────────────────────────────
                var pad = 0.0
                for (k in 0 until 3) {
                    val f = padF[k]
                    padP[k * 2] += TAU * f * (1.0 - DETUNE) / RATE
                    padP[k * 2 + 1] += TAU * f * (1.0 + DETUNE) / RATE
                    if (padP[k * 2] > TAU) padP[k * 2] -= TAU
                    if (padP[k * 2 + 1] > TAU) padP[k * 2 + 1] -= TAU
                    pad += (sin(padP[k * 2]) + sin(padP[k * 2 + 1])) * 0.5 * padW[k]
                }
                pad *= PAD_AMP_MIN + (PAD_AMP_MAX - PAD_AMP_MIN) * open

                // one-pole, opening as the breath fills. A filter sweep on a pad is the oldest
                // trick there is for making sound feel like breathing, and it is the right one.
                // The filter ceiling rides `bright` too, so the darkness control darkens the whole
                // voice rather than only the struck notes. Floor stays put — closing the bottom end
                // as well would make a dark setting sound muffled instead of warm.
                val cut = LP_MIN + (LP_MAX * bright - LP_MIN).coerceAtLeast(120.0) * open
                val a = (TAU * cut / RATE).coerceIn(0.002, 0.85)
                lp += a * (pad - lp)

                // ── the bells ───────────────────────────────────────────────
                var bell = 0.0
                for (b in bells) {
                    if (b.t < 0) continue
                    val env = bellEnv(b.t)
                    b.p1 += TAU * b.f / RATE
                    b.p2 += TAU * b.f * 2.0 / RATE
                    b.p3 += TAU * b.f * 3.01 / RATE
                    if (b.p1 > TAU) b.p1 -= TAU
                    if (b.p2 > TAU) b.p2 -= TAU
                    if (b.p3 > TAU) b.p3 -= TAU
                    // Upper partials ride `bright`. Turning it down does not merely make the bell
                    // quieter — it removes the harmonics that make a low note still sound piercing,
                    // which is the actual complaint behind "too high".
                    bell += (sin(b.p1) + 0.34 * bright * sin(b.p2) + 0.12 * bright * bright * sin(b.p3)) * env
                    b.t += 1.0 / RATE
                    if (b.t > BELL_LIFE) b.t = -1.0
                }
                bell *= BELL_AMP

                // ── the heart, and the room everything else makes for it ────
                var heart = 0.0
                var padOut = lp
                if (heartOn) {
                    val env = Heart.envelope(seconds.toFloat(), bpm).toDouble()
                    pHeart += TAU * (46.0 + 18.0 * env) / RATE
                    if (pHeart > TAU) pHeart -= TAU
                    heart = sin(pHeart) * env * HEART_AMP
                    // Sidechain: everything else steps back under the thump. This, not headroom
                    // alone, is what makes a beat land clean instead of sounding crushed.
                    // ⚠ Ducking `padOut` and NOT `lp` — `lp` is the filter's STATE, and scaling a
                    // filter's state feeds the duck back through the filter and bends its response.
                    padOut = lp * (1.0 - 0.35 * env)
                    bell *= (1.0 - 0.25 * env)
                }

                val dry = (padOut + bell).toFloat()
                val wet = room(dry * ROOM_SEND)
                // soft saturation, never a clamp — below about half scale it is transparent, and
                // above it the curve simply bends. Nothing here can hard-clip any more.
                val s = tanh((dry + wet).toDouble() * master * gain + heart * master * gain)

                buf[i] = (s * 30000.0).toInt().toShort()
                seconds += 1.0 / RATE
            }
            if (t.write(buf, 0, buf.size) < 0) return
            if (releasing && master < 0.001f) return
        }
    }

    private companion object {
        const val RATE = 44100
        const val BLOCK = 1024
        const val TAU = 2.0 * Math.PI

        /** Per-sample master fade — full in or out in well under 100 ms. */
        const val FADE = 0.002f
        /** Per-sample glide on the breath's own value; the view only updates it 60×/s. */
        const val OPEN_GLIDE = 0.0008

        /** How far apart the detuned pair sits. Small: this is warmth, not chorus. */
        const val DETUNE = 0.0016
        const val PAD_AMP_MIN = 0.055
        const val PAD_AMP_MAX = 0.135
        const val LP_MIN = 260.0     // Hz, at the emptiest point of the breath
        const val LP_MAX = 1750.0    // Hz, at the fullest

        const val BELL_VOICES = 4
        const val BELL_AMP = 0.13
        const val BELL_ATTACK = 0.11 // seconds — struck softly, never plucked
        const val BELL_DECAY = 1.15  // time constant of the tail
        const val BELL_LIFE = 3.4    // when the voice is free again

        const val HEART_AMP = 0.30

        const val COMB_FEEDBACK = 0.74f
        const val COMB_DAMP = 0.28f
        const val AP_FEEDBACK = 0.5f
        /** How much goes INTO the room. A comb bank at this feedback has real gain of its own,
         *  so the send is small and the wet is added at unity rather than mixed at a fraction. */
        const val ROOM_SEND = 0.22f
    }
}
