package dev.breathgate

import android.graphics.Color

/**
 * The gate's colours, chosen as sets rather than as knobs.
 *
 * Code rather than resources: a palette must be switchable at runtime, previewable in the
 * overview, buildable by hand, and invertible — none of which XML resources do well.
 */
data class Palette(
    val name: String,
    val bg: Int,
    val fill: Int,
    val ring: Int,
    val holdFull: Int,
    val holdEmpty: Int,
    val text: Int,
    val textSoft: Int
) {

    /**
     * The light gate. Grounds and inks trade places while the accents stay put, so a palette
     * keeps its character instead of becoming a different one — the point is a bright room,
     * not a second set of colours to maintain.
     */
    fun inverted(): Palette = Palette(
        name = "$name light",
        bg = text,
        fill = mix(text, bg, 0.10f),
        ring = ring,
        holdFull = holdFull,
        holdEmpty = holdEmpty,
        text = bg,
        textSoft = mix(bg, text, 0.42f)
    )

    fun asList() = listOf(bg, fill, ring, holdFull, holdEmpty, text, textSoft)

    companion object {
        private fun h(s: String) = Color.parseColor(s)

        fun mix(a: Int, b: Int, f: Float): Int {
            val t = f.coerceIn(0f, 1f)
            fun c(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
            return Color.rgb(
                c(Color.red(a), Color.red(b)),
                c(Color.green(a), Color.green(b)),
                c(Color.blue(a), Color.blue(b))
            )
        }

        private fun luma(c: Int) =
            (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f

        /**
         * Which of this palette's two inks to write ON a given colour.
         *
         * A filled button has to choose its own text colour or it will eventually be handed a
         * pale accent by the custom-palette builder and go invisible on itself. Relative
         * luminance with the usual perceptual weights — green carries most of what the eye
         * reads as brightness.
         */
        fun inkOn(c: Int, p: Palette): Int {
            val dark = if (luma(p.bg) < luma(p.text)) p.bg else p.text
            val light = if (luma(p.bg) < luma(p.text)) p.text else p.bg
            return if (luma(c) > 0.55f) dark else light
        }

        /** Labels for the seven roles, in the order `asList()` returns them. */
        val ROLES = listOf(
            "background", "form", "outline", "hold — full", "hold — empty", "text", "text, soft"
        )

        val BUILT_IN: List<Palette> = listOf(
            Palette("deep",   h("#0E1113"), h("#16211F"), h("#3E6B63"), h("#4E7F5E"), h("#2A3E52"), h("#C8D3CF"), h("#6E807B")),
            Palette("ember",  h("#120E0C"), h("#241713"), h("#8A4A2C"), h("#B46A34"), h("#4A2A34"), h("#E4D3C6"), h("#8A7466")),
            Palette("ink",    h("#0B0D14"), h("#141826"), h("#3F4E86"), h("#5A6FB0"), h("#2A2F4A"), h("#CDD3E6"), h("#6E7794")),
            Palette("moss",   h("#0A100C"), h("#131E17"), h("#3C6B45"), h("#5C8A4C"), h("#26402F"), h("#CBD8CC"), h("#6E8072")),
            Palette("bone",   h("#141312"), h("#211F1D"), h("#7A7266"), h("#9C9081"), h("#3A3630"), h("#E8E4DC"), h("#8B857B")),
            Palette("signal", h("#0D0F0E"), h("#151A19"), h("#2E8B7A"), h("#3FB39C"), h("#1F4A5C"), h("#D6E5E1"), h("#6F8783"))
        )

        /** The index reserved for the hand-built one. */
        const val CUSTOM = -1

        fun fromInts(v: List<Int>, name: String = "custom") =
            Palette(name, v[0], v[1], v[2], v[3], v[4], v[5], v[6])

        /** The palette actually in force, including the custom one and the light inversion. */
        fun current(p: Prefs): Palette {
            val base = if (p.palette == CUSTOM) fromInts(p.customPalette) else
                BUILT_IN[p.palette.coerceIn(0, BUILT_IN.lastIndex)]
            return if (p.lightGate) base.inverted() else base
        }

        fun at(i: Int) = BUILT_IN[i.coerceIn(0, BUILT_IN.lastIndex)]
    }
}
