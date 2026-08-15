package dev.breathgate

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout

/**
 * THE BUTTONS THE WHOLE APP EXISTS FOR.
 *
 * *go in* and *not now* were bare text on a transparent background — the two words that carry the
 * entire decision this app was built to put in front of someone, styled as if they were a menu.
 * His note: **"could do with more accessible / user-design implementation."** He is right, and it
 * is the most consequential surface in the project.
 *
 * WHAT CHANGED AND WHY EACH ONE:
 *
 *   · **Real touch targets.** 64 dp tall, full width. Android's own accessibility floor is 48 dp;
 *     these are the last thing you touch with soft eyes at the end of a breath, so they get more.
 *   · **A visible shape.** A filled pill and an outlined pill — you can see where the button *is*
 *     without reading it. Text alone gives no target boundary at all.
 *   · **Contrast that survives a hand-built palette.** The filled pill picks its own ink by
 *     luminance ([Palette.inkOn]), so a pale custom accent cannot render the label invisible.
 *   · **A pressed state**, because a tap with no acknowledgement gets tapped twice.
 *   · **Stacked, not side by side.** Two adjacent horizontal buttons at thumb width are a
 *     mis-tap waiting to happen, and this is the wrong moment to mis-tap.
 *   · ⭐ **`not now` sits at the BOTTOM — the easiest place on the screen for a thumb to reach.**
 *     The visual hierarchy stays conventional (the affirmative is the filled one), but the
 *     abstaining choice is the one your hand can take without moving. A gentle thumb on the
 *     scale, not a dark pattern in either direction.
 *
 * ⚙ If he wants the emphasis flipped so *not now* is the filled one, that is [ENTER_IS_PRIMARY]
 * and nothing else. It is a stance about what the app is for, so it is left as a constant with
 * his name on it rather than quietly chosen.
 */
object Pill {

    /** true: *go in* is the filled button. false: *not now* is. His call, one line. */
    const val ENTER_IS_PRIMARY = true

    const val HEIGHT_DP = 64f
    private const val RADIUS_DP = 32f
    private const val STROKE_DP = 2f

    fun dp(ctx: Context, v: Float) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)

    private fun shape(colour: Int, radius: Float, strokeWidth: Int = 0, strokeColour: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(colour)
            if (strokeWidth > 0) setStroke(strokeWidth, strokeColour)
        }

    /**
     * A pill. `primary` fills it in the palette's accent; otherwise it is an outline in the soft
     * ink. Both are the same size — a secondary choice is not a smaller choice.
     */
    fun make(ctx: Context, pal: Palette, text: String, primary: Boolean, onTap: () -> Unit): Button {
        val r = dp(ctx, RADIUS_DP)
        val stroke = dp(ctx, STROKE_DP).toInt().coerceAtLeast(1)

        val face: android.graphics.drawable.Drawable
        val pressed: android.graphics.drawable.Drawable
        val ink: Int
        if (primary) {
            face = shape(pal.ring, r)
            pressed = shape(Palette.mix(pal.ring, pal.bg, 0.30f), r)
            ink = Palette.inkOn(pal.ring, pal)
        } else {
            face = shape(Color.TRANSPARENT, r, stroke, pal.textSoft)
            pressed = shape(Palette.mix(pal.bg, pal.text, 0.14f), r, stroke, pal.text)
            ink = pal.text
        }

        return Button(ctx).apply {
            this.text = text
            isAllCaps = false
            textSize = 19f
            setTextColor(ink)
            gravity = Gravity.CENTER
            stateListAnimator = null          // or the platform draws its own elevation shadow
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressed)
                addState(intArrayOf(), face)
            }
            minHeight = dp(ctx, HEIGHT_DP).toInt()
            setPadding(dp(ctx, 24f).toInt(), 0, dp(ctx, 24f).toInt(), 0)
            setOnClickListener { onTap() }
        }
    }

    /** Full-width layout params with a gap under the button. */
    fun row(ctx: Context, marginBottom: Float = 14f) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, HEIGHT_DP).toInt()
        ).apply { bottomMargin = dp(ctx, marginBottom).toInt() }
}
