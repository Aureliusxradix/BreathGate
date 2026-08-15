package dev.breathgate

/**
 * THE THEME IS COLOUR *AND* TUNING.
 *
 * A palette was only ever half a theme. Every colour set here now has a voice: a root pitch and
 * a small set of degrees the breath climbs and falls through. Choosing "deep" chooses both — one
 * decision, two senses — which is the whole point of building the sound into the theme rather
 * than bolting a music player onto the side of it.
 *
 * WHY THIS SHAPE, AND WHY IT KEEPS THE INVARIANT.
 * There is no INTERNET permission and there never will be, so streamed music is not on the table
 * and never was. Two sources remain, and both are honest:
 *
 *   1 · SYNTHESISED ON DEVICE (this file + [Sound]) — no assets, no licences, no downloads, and
 *       it can follow the breath continuously in a way a fixed recording cannot.
 *   2 · THE PERSON'S OWN AUDIO (future) — chosen through the system picker, which grants this app
 *       a URI and nothing else. No storage permission, no library scan, no INTERNET.
 *
 * Path 1 is built. Path 2 is architecture: [Tuning] is what a composed piece would have to agree
 * with, so a recorded bed can be dropped in later and land in the same key as the drone rather
 * than fighting it. Sound engineering per palette — reverb size, the weight of the low end, how
 * far the voice moves — hangs off [warmth] and [rootHz], not off a second parallel settings tree.
 */
data class Tuning(
    val name: String,
    /** The drone. Low, because the body answers low sound with the diaphragm. */
    val rootHz: Float,
    /** Semitones above the root that the breath moves through, in order. */
    val degrees: List<Int>,
    /** 0 = hollow and glassy · 1 = reedy and warm. Sets the harmonic weight. */
    val warmth: Float
)

object Theme {

    /**
     * One per palette, in `Palette.BUILT_IN` order — index i is the voice of colour set i.
     * Roots are deliberately in the 87–147 Hz band: below speech, above the range where a phone
     * speaker gives up entirely.
     */
    val TUNINGS: List<Tuning> = listOf(
        Tuning("deep",   110.00f, listOf(0, 3, 7, 10), 0.30f),  // A2  · minor, wide open
        Tuning("ember",  130.81f, listOf(0, 4, 7, 11), 0.62f),  // C3  · lydian warmth
        Tuning("ink",     98.00f, listOf(0, 2, 7,  9), 0.22f),  // G2  · sparse, cool
        Tuning("moss",   146.83f, listOf(0, 2, 5,  9), 0.45f),  // D3  · dorian, green
        Tuning("bone",    87.31f, listOf(0, 5, 7, 12), 0.18f),  // F2  · bare fifths
        Tuning("signal", 123.47f, listOf(0, 2, 6,  8), 0.70f)   // B2  · whole tone, unresolved
    )

    /** The tuning in force. A hand-built palette picks its own voice; everything else inherits. */
    fun forPrefs(p: Prefs): Tuning {
        val i = if (p.palette == Palette.CUSTOM) p.customTuning else p.palette
        return TUNINGS[i.coerceIn(0, TUNINGS.lastIndex)]
    }

    /** Equal-temperament ratio for a (possibly fractional) number of semitones. */
    fun ratio(semitones: Float): Float = Math.pow(2.0, semitones / 12.0).toFloat()
}
