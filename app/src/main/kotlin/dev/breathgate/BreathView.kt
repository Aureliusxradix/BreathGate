package dev.breathgate

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * The gate. Five ways of drawing one breath — all grow from the centre, all show the cycle.
 *
 *   0 ORB       one body filling and emptying around a still centre
 *   1 BLOOM     rings travelling outward, far past the form; the cycle read as waves
 *   2 BOX       a square drawn edge by edge — box breathing, literally
 *   3 GUILLOCHE the rosette from the card mark, breathing
 *   4 CORAL     a lung as coral: tubes filling with air from the centre out
 *
 * ONE ANIMATOR DRIVES EVERYTHING. The earlier choppiness came from three clocks — the breath
 * animator, the settle, and the opening — each starting from wherever the last one happened to
 * stop. Now `openness` is a single animated value handed between stages, so every transition
 * begins exactly where the previous frame ended.
 *
 * THE STILL CENTRE IS MEASURED, NOT GUESSED. Orb, bloom, guilloche and coral all hold the count
 * in a fixed centre whose radius is derived from the actual drawn size of the widest number this
 * session will show, plus a real 2.5 mm of air. The gap is the specification; when a long count
 * would push the centre too wide, the TYPE shrinks and the air stays. See [layoutCore].
 */
class BreathView(
    context: Context,
    private val p: Prefs,
    private val onComplete: () -> Unit
) : View(context) {

    private val pal = Palette.current(p)
    private val argb = ArgbEvaluator()

    private val bg = Paint().apply { color = pal.bg }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.text; textAlign = Paint.Align.CENTER; textSize = 50f
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.textSoft; textAlign = Paint.Align.CENTER; textSize = 34f
    }
    /** The count, drawn inside the form. */
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = pal.text; textAlign = Paint.Align.CENTER
    }

    /** name, seconds, kind: 1 in · 0 hold-full · -1 out · 2 hold-empty */
    private val phases: List<Triple<String, Int, Int>> = buildList {
        add(Triple(p.wordIn, p.inhale, 1))
        if (p.holdIn > 0) add(Triple(p.wordHold, p.holdIn, 0))
        add(Triple(p.wordOut, p.exhale, -1))
        if (p.holdOut > 0) add(Triple(p.wordHoldEmpty, p.holdOut, 2))
    }

    private val total = p.totalSeconds.coerceAtLeast(1)
    private var elapsed = 0f
    private var finished = false

    /** 0.40 empty … 1.0 full … >1 while the door opens. The single source of size. */
    private var openness = MIN_OPEN
    private var wordAlpha = 1f
    private var breathing = true
    private var animator: ValueAnimator? = null
    /** Its own animator: the words can leave while the form is doing something else. */
    private var wordFade: ValueAnimator? = null

    /** The theme's voice. Null unless he asked for it, or unless the device refused us a track. */
    private var sound: BreathSound? = null

    // ── the measured centre ─────────────────────────────────────────────────
    /** The widest count that will ever be drawn this session — what the centre is sized around. */
    private val widestCount = (phases.maxOf { it.second } + 1).toString()
    private var coreR = 0f
    private var coreBaselineDy = 0f
    private var gapPx = 0f
    private val textBounds = Rect()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        layoutCore()
    }

    /**
     * THE GAP IS THE SPEC.
     *
     * A number sitting flush against the edge of its circle reads as cramped no matter how well
     * the rest is drawn. So: measure the widest count at a candidate type size, take the radius
     * that clears its corners by [GAP_MM] of real millimetres — `COMPLEX_UNIT_MM`, so it is the
     * same distance on a small dense screen as on a large loose one — and if that centre would
     * grow past [CORE_MAX] of the form, shrink the TYPE and measure again. The air never gives way.
     */
    private fun layoutCore() {
        val maxR = min(width, height) / RADIUS_DIVISOR
        if (maxR <= 0f) return
        gapPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_MM, GAP_MM, resources.displayMetrics
        )
        var size = maxR * COUNT_TEXT
        var r: Float
        while (true) {
            core.textSize = size
            core.getTextBounds(widestCount, 0, widestCount.length, textBounds)
            // half the diagonal: the corners of the number are its closest approach to the ring,
            // so clearing those clears everything else by more.
            r = hypot(textBounds.width().toFloat(), textBounds.height().toFloat()) / 2f + gapPx
            if (r <= maxR * CORE_MAX || size <= maxR * 0.09f) break
            size *= 0.94f
        }
        coreR = r.coerceIn(maxR * CORE_MIN, maxR * CORE_MAX)
        // digits have no descender, so the tight-bounds midpoint centres them honestly
        coreBaselineDy = textBounds.height() / 2f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (p.sound) {
            sound = runCatching {
                BreathSound(
                    context, Theme.forPrefs(p), p.restingBpm, p.heartbeat, p.soundVolume,
                    p.soundOctave, p.soundBrightness, p.soundBells
                ).also { it.start() }
            }.getOrNull()
        }
        animator = ValueAnimator.ofFloat(0f, total.toFloat()).apply {
            duration = total * 1000L
            interpolator = LinearInterpolator()
            addUpdateListener {
                elapsed = it.animatedValue as Float
                val n = now()
                openness = opennessFor(n)
                sound?.update(openness, n.kind, n.within, n.index)
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) = settle()
            })
            start()
        }
    }

    /**
     * Sound dies with the view. That covers the overlay being torn down by GateService AND the
     * in-app test run, without either of them having to remember — the one place that knows the
     * gate is gone is the thing that was drawing it.
     */
    override fun onDetachedFromWindow() {
        animator?.cancel(); animator = null
        wordFade?.cancel(); wordFade = null
        sound?.stop(); sound = null
        super.onDetachedFromWindow()
    }

    /** The breath has finished: ease from wherever the last frame left the form to rest. */
    private fun settle() {
        breathing = false
        animator?.cancel()
        animator = ValueAnimator.ofFloat(openness, SETTLED).apply {
            duration = 620L
            interpolator = android.view.animation.DecelerateInterpolator(1.4f)
            addUpdateListener { openness = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    finished = true; invalidate(); onComplete()
                }
            })
            start()
        }
    }

    /**
     * 🐛 THE WORD AND THE QUESTION WERE BOTH ON SCREEN AT ONCE.
     *
     * When the breath finishes, [words] draws **"here"** below the form. When the gate is set to
     * ask, `GateService` then lays its question and its two buttons over the bottom of the same
     * screen — and the two collided. Two pieces of copy, written independently, each correct on
     * its own, arriving at the same coordinates because neither knew about the other.
     *
     * The gate calls this the moment it decides to ask, so **"here" leaves as the question
     * arrives** rather than being painted over by it. On a test run nothing asks, so the word
     * stays — which is right, because there it *is* the ending.
     */
    fun releaseTheWord() {
        wordFade?.cancel()
        wordFade = ValueAnimator.ofFloat(wordAlpha, 0f).apply {
            duration = 260L
            addUpdateListener { wordAlpha = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /**
     * The way in. Continues from the current openness rather than restarting, and fades the
     * words on the same clock — the two used to be separate animations, which is what made
     * the hand-off to "go in" feel like a stutter.
     */
    fun openAndThen(after: () -> Unit) {
        animator?.cancel()
        wordFade?.cancel()
        val from = openness
        // ⚠ RELATIVE, NOT ABSOLUTE. This used to assign `wordAlpha = 1 - f*2.2`, which on the
        // first frame (f≈0) writes 1.0 — so after `releaseTheWord()` had already faded the word
        // out for the question, tapping *go in* SNAPPED IT BACK to full and drew "here" under the
        // expanding circle for ~170 ms. Exactly the collision he reported, returning in the very
        // flow that was supposed to have fixed it. Multiplying by where the word actually was
        // keeps both paths right: 0 stays 0, and 1 fades exactly as it always did.
        val w0 = wordAlpha
        val corner = hypot(width / 2f, height / 2f) * 1.16f
        val maxR = min(width, height) / RADIUS_DIVISOR
        val to = (corner / maxR).coerceAtLeast(from + 0.1f)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 620L
            interpolator = android.view.animation.AccelerateInterpolator(1.5f)
            addUpdateListener {
                val f = it.animatedValue as Float
                openness = from + (to - from) * f
                wordAlpha = (w0 * (1f - f * 2.2f)).coerceIn(0f, 1f)
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) = after()
            })
            start()
        }
    }

    // ── the pattern ─────────────────────────────────────────────────────────
    private data class Now(val name: String, val within: Float, val len: Int, val kind: Int, val index: Int)

    private fun now(): Now {
        val cycleLen = p.cycleSeconds.coerceAtLeast(1)
        // FLASH FIX: at the very end `elapsed % cycle` wraps to 0, so the FIRST phase rendered
        // for one frame before settle() ran — the breath appeared to start again for an instant.
        // Past the end we hold the final phase instead.
        if (elapsed >= total) {
            val (n, l, k) = phases.last()
            return Now(n, 1f, l, k, phases.lastIndex)
        }
        var t = elapsed % cycleLen
        phases.forEachIndexed { i, (name, len, kind) ->
            if (t < len) return Now(name, t / len.coerceAtLeast(1), len, kind, i)
            t -= len
        }
        val (n, l, k) = phases.last()
        return Now(n, 1f, l, k, phases.lastIndex)
    }

    private fun opennessFor(n: Now): Float {
        // Holds are not still. The form keeps a small living movement — a held breath is
        // something you are doing, and a frozen shape says the opposite.
        val drift = sin(n.within * Math.PI.toFloat() * 2f) * 0.012f
        return when (n.kind) {
            1 -> MIN_OPEN + (1f - MIN_OPEN) * ease(n.within, IN_BIAS)
            0 -> 1f + drift
            -1 -> 1f - (1f - MIN_OPEN) * ease(n.within, OUT_BIAS)
            else -> MIN_OPEN + drift
        }
    }

    /**
     * Symmetric easing is why it read as "a simple scale up and scale down". A breath is not
     * symmetric: the in-breath arrives with intent and the out-breath lets go — it starts
     * quickly and trails away. `bias` below 1 front-loads the motion, above 1 back-loads it.
     */
    private fun ease(x: Float, bias: Float = 1f): Float {
        val t = x.coerceIn(0f, 1f).let { if (bias == 1f) it else Math.pow(it.toDouble(), bias.toDouble()).toFloat() }
        return (1f - cos(t * Math.PI.toFloat())) / 2f
    }

    private fun blend(from: Int, to: Int, f: Float) = argb.evaluate(f.coerceIn(0f, 1f), from, to) as Int

    private fun smooth(x: Float): Float {
        val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t)
    }

    /** 0 at the emptiest point of the breath, 1 at the fullest. What every form is drawing. */
    private fun breathNorm(r: Float, maxR: Float) =
        (((r / maxR) - MIN_OPEN) / (1f - MIN_OPEN)).coerceIn(0f, 1.2f)

    // ── draw ────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

        val cx = width / 2f
        val cy = height / 2f - 60f
        val maxR = min(width, height) / RADIUS_DIVISOR
        if (coreR <= 0f) layoutCore()
        val n = now()

        // Holds always move in colour. Movement phases do too, if he asked for it.
        val pulse = (sin(n.within * Math.PI.toFloat() * 2f) + 1f) / 2f
        var ring = pal.ring
        var body = pal.fill
        when (n.kind) {
            0 -> { ring = blend(pal.ring, pal.holdFull, 0.35f + 0.65f * pulse)
                   body = blend(pal.fill, pal.holdFull, 0.20f + 0.22f * pulse) }
            2 -> { ring = blend(pal.ring, pal.holdEmpty, 0.35f + 0.65f * pulse)
                   body = blend(pal.fill, pal.holdEmpty, 0.20f + 0.22f * pulse) }
            else -> if (p.motionColour) {
                val f = if (n.kind == 1) ease(n.within) else 1f - ease(n.within)
                ring = blend(pal.holdEmpty, pal.holdFull, f)
                body = blend(pal.fill, blend(pal.fill, pal.holdFull, 0.35f), f)
            }
        }

        // ── THE HEARTBEAT ───────────────────────────────────────────────────
        // The second way to move without moving. It brightens the outline and warms the body on
        // [Heart]'s envelope — deliberately NOT a scale kick, because the one thing in this app
        // that must never resize is the number, and everything around it is tied to that centre.
        // It rides on top of the colour shift rather than replacing it: they are siblings.
        //
        // ⚠ GATED ON `breathing`, NOT ON `!finished` — and the difference is a visible bug.
        // The breath animator's last frame leaves `elapsed` at exactly `total`; `settle()` then
        // runs 620 ms of invalidations without advancing it. `!finished` is still true through all
        // of them, so the envelope was being recomputed at a CONSTANT t for the whole settle. At
        // the default 60 bpm the interval is exactly 1 s and `total` is a whole number of seconds,
        // so `total % 1 == 0` — the top of a beat. The form locked at MAXIMUM FLASH for 620 ms at
        // the precise moment the breath completes, then snapped off. `breathing` makes the pulse
        // live exactly as long as the breath does, and die with it.
        val beat = if (p.heartbeat && breathing) Heart.envelope(elapsed, p.restingBpm) else 0f
        if (beat > 0f) {
            ring = blend(ring, pal.text, 0.50f * beat)
            body = blend(body, pal.holdFull, 0.26f * beat)
        }

        val r = maxR * openness

        // Once the door is opening the form is simply a growing field of colour.
        if (!breathing && wordAlpha < 1f && openness > SETTLED + 0.05f) {
            fill.color = body
            canvas.drawCircle(cx, cy, r, fill)
            return
        }

        // the heart's halo, behind everything — a widening of the light, not of the form
        if (beat > 0.02f) {
            fill.color = pal.holdFull
            fill.alpha = (52 * beat).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, r * (1f + 0.10f * beat), fill)
            fill.alpha = 255
        }

        when (p.visual) {
            1 -> bloom(canvas, cx, cy, r, maxR, ring, body)
            2 -> box(canvas, cx, cy, ring, body, n)
            3 -> guilloche(canvas, cx, cy, r, maxR, ring, body)
            4 -> coral(canvas, cx, cy, r, maxR, ring, body)
            else -> orb(canvas, cx, cy, r, ring, body, n)
        }
        words(canvas, cx, cy, maxR, n)
    }

    /** The still centre, drawn over whatever was moving. Every radial form ends with this. */
    private fun centre(cv: Canvas, cx: Float, cy: Float, ring: Int, body: Int) {
        fill.color = body
        cv.drawCircle(cx, cy, coreR, fill)
        stroke.color = ring
        stroke.strokeWidth = 3f
        cv.drawCircle(cx, cy, coreR, stroke)
    }

    /**
     * ORB — one body, filling and emptying around a centre that does not move.
     *
     * The centre is new here. The orb used to be a single circle with the number floating in it,
     * which meant the number's clearance changed with every frame of the breath; now the count
     * has its own still room and the breath happens around the outside of it. At the emptiest
     * moment the body closes down onto that centre rather than through it.
     */
    private fun orb(cv: Canvas, cx: Float, cy: Float, r: Float, ring: Int, body: Int, n: Now) {
        val rr = r.coerceAtLeast(coreR * 1.06f)
        fill.color = body
        cv.drawCircle(cx, cy, rr, fill)
        stroke.color = ring
        stroke.strokeWidth = if (n.kind == 0 || n.kind == 2) 7f else 4f
        cv.drawCircle(cx, cy, rr, stroke)
        centre(cv, cx, cy, ring, Palette.mix(body, pal.bg, 0.38f))
    }

    /**
     * BLOOM — the rings TRAVEL, and now they travel much further out.
     *
     * They were reaching barely past the form itself, which made the bloom a slightly busier orb.
     * The point of a bloom is that the room is bigger than the thing in it: rings now run to
     * [BLOOM_REACH] times the breath's own radius, past the edge of the screen on a full breath,
     * fading on a cubic curve so the far ones are a suggestion rather than clutter. More of them,
     * launched in procession, so there is always one leaving and one arriving.
     */
    private fun bloom(cv: Canvas, cx: Float, cy: Float, r: Float, maxR: Float, ring: Int, body: Int) {
        val corner = hypot(width / 2f, height / 2f)
        val reach = ((r * BLOOM_REACH) - coreR).coerceIn(1f, corner * 1.12f)

        for (i in 0 until BLOOM_RINGS) {
            // each ring offset along a shared travel phase, so they leave in procession;
            // how far out they reach is what breathes, not the core they leave from
            val travel = ((elapsed / p.cycleSeconds.coerceAtLeast(1)) * 1.05f + i.toFloat() / BLOOM_RINGS) % 1f
            val rr = coreR + reach * travel
            val fade = (1f - travel)
            stroke.color = ring
            stroke.alpha = (245 * fade * fade * fade).toInt().coerceIn(0, 255)
            stroke.strokeWidth = 2f + 6f * fade
            cv.drawCircle(cx, cy, rr, stroke)
        }
        stroke.alpha = 255

        // a soft body just outside the centre, so the bloom has a source and not just a hole
        fill.color = body
        fill.alpha = 150
        cv.drawCircle(cx, cy, coreR + (r - coreR).coerceAtLeast(0f) * 0.34f, fill)
        fill.alpha = 255

        centre(cv, cx, cy, ring, body)
    }

    /**
     * BOX — rehauled 2026-08-14 on his correction: **the box does not move.**
     *
     * The square is fixed. The dot alone travels, and its speed is set by the counts, so a
     * long phase is a slow edge and a short one is a quick edge — you can read the pattern
     * off the motion without counting. **Corners are phase changes**, which is the only thing
     * that makes a box worth drawing rather than a circle.
     *
     * On a hold the dot changes colour. With the box still, the colour IS the hold signal —
     * and a hold is a held state, not a stalled one, so it stays lit and keeps moving.
     *
     * Phases share the perimeter equally: four active phases give one edge each (true box
     * breathing); a pattern with no holds gives two phases and half the perimeter each, so a
     * cycle is always exactly one lap and the dot never has to teleport across a zero-length
     * edge.
     */
    private fun box(cv: Canvas, cx: Float, cy: Float, ring: Int, body: Int, n: Now) {
        // Fixed size — deliberately ignores `openness`, which is what "no movement" means.
        val half = min(width, height) / RADIUS_DIVISOR * 0.95f
        val rect = RectF(cx - half, cy - half, cx + half, cy + half)

        fill.color = pal.fill
        cv.drawRect(rect, fill)
        stroke.color = pal.ring
        stroke.strokeWidth = 4f
        cv.drawRect(rect, stroke)

        // Where the dot is: each phase owns an equal share of the perimeter, and crosses it
        // in that phase's own seconds.
        val segments = phases.size.coerceAtLeast(1)
        val lap = ((n.index + n.within) / segments).coerceIn(0f, 1f)
        val d = lap * 4f
        val side = d.toInt().coerceIn(0, 3)
        val f = d - side
        val (mx, my) = when (side) {
            0 -> (rect.left + rect.width() * f) to rect.top
            1 -> rect.right to (rect.top + rect.height() * f)
            2 -> (rect.right - rect.width() * f) to rect.bottom
            else -> rect.left to (rect.bottom - rect.height() * f)
        }

        // The dot carries the phase. Holds get their own colour and a little breath of size.
        val holding = n.kind == 0 || n.kind == 2
        val dotColour = when (n.kind) {
            0 -> pal.holdFull
            2 -> pal.holdEmpty
            else -> ring
        }
        val pulse = (sin(n.within * Math.PI.toFloat() * 2f) + 1f) / 2f
        val r = if (holding) 17f + 5f * pulse else 15f

        if (holding) {                       // a soft halo, so a hold reads at a glance
            fill.color = dotColour
            fill.alpha = 70
            cv.drawCircle(mx, my, r * 2.1f, fill)
            fill.alpha = 255
        }
        fill.color = dotColour
        cv.drawCircle(mx, my, r, fill)
    }

    /**
     * GUILLOCHE — the card mark's rosette, alive.
     *
     * The centre stays fixed and carries the count; everything outside it breathes.
     *
     * REWORKED 2026-08-15, because the family was still moving as one block — the same figure
     * slowly turning. Four changes, each aimed at that:
     *
     *   · EVERY OTHER CURVE TURNS THE OTHER WAY. Counter-rotation inside one family is where
     *     guilloche shimmer actually comes from; a family all drifting one way is a spinning
     *     doily. This is the change you see first.
     *   · EACH CURVE HAS ITS OWN PETAL COUNT (9, 10, 11, repeating). Different closed figures
     *     interfering, rather than one figure drawn at seven sizes.
     *   · A THIRD HARMONIC, small and slow, so the outline is never quite the shape it was.
     *   · THE FAMILY BREATHES UNEVENLY — each curve's own radial wander on its own period, so
     *     the rosette opens like something living rather than like a zoom.
     *
     * Integer harmonics throughout, so every curve still closes on itself exactly.
     */
    private fun guilloche(cv: Canvas, cx: Float, cy: Float, r: Float, maxR: Float, ring: Int, body: Int) {
        val breath = breathNorm(r, maxR)
        val path = Path()

        for (k in 0 until GUIL_CURVES) {
            val dir = if (k % 2 == 0) 1f else -1f            // counter-rotation within the family
            val phase = elapsed * (0.058f + 0.021f * k) * dir
            val counter = -elapsed * (0.034f + 0.009f * (k % 3)) * dir
            val slowTurn = elapsed * 0.013f

            val fast = GUIL_FAST + (k % 3)                    // 9 · 10 · 11, repeating
            val slow = GUIL_SLOW + (k % 2)                    // 5 · 6
            val third = 3

            // the figure opens as the breath fills, and gathers as it empties
            val a1 = 0.12f + 0.19f * breath
            val a2 = 0.11f - 0.05f * breath
            val a3 = 0.035f + 0.02f * sin(elapsed * 0.19f + k)

            val scale = 0.42f + 0.58f * (k + 1f) / GUIL_CURVES
            // each curve wanders on its own period — the family no longer breathes as one body
            val wander = 1f + 0.045f * sin(elapsed * (0.21f + 0.06f * k) + k * 1.7f)
            val base = (coreR + (r * GUIL_REACH - coreR).coerceAtLeast(1f) * scale) * wander

            path.reset()
            var first = true
            var a = 0f
            while (a <= 360f) {
                val th = Math.toRadians(a.toDouble()).toFloat()
                val rr = base * (1f +
                    a1 * cos(fast * th + phase) +
                    a2 * cos(slow * th + counter) +
                    a3 * cos(third * th + slowTurn))
                val x = cx + rr * cos(th)
                val y = cy + rr * sin(th)
                if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
                a += GUIL_STEP
            }
            path.close()
            stroke.color = ring
            stroke.alpha = (54 + 156f * (k + 1f) / GUIL_CURVES).toInt().coerceIn(34, 255)
            stroke.strokeWidth = 1.6f + 1.4f * breath      // the line itself thickens as it fills
            cv.drawPath(path, stroke)
        }
        stroke.alpha = 255

        centre(cv, cx, cy, ring, body)
    }

    // ── CORAL ───────────────────────────────────────────────────────────────
    /**
     * One sub-segment of the tube network, in polar coordinates about the centre.
     *
     * `rad` is 0 at the edge of the still centre and 1 at full reach, so the geometry survives a
     * rotation, a resize and a breath without being rebuilt. `d` is how far along the TUBE the
     * point is — the distance air has to travel to get there — which is what the fill front races
     * along, and it is not the same as `rad`: an outer branch that took a long way round fills late.
     */
    private class Twig(
        val rad0: Float, val ang0: Float, val d0: Float,
        val rad1: Float, val ang1: Float, val d1: Float,
        val width: Float, val tip: Boolean
    )

    private val coral: List<Twig> by lazy { buildCoral() }

    /**
     * Grown once, deterministically. A fixed seed because this is a piece of the app's face —
     * it should be the same lung every time he opens the gate, not a new random one each breath.
     *
     * Polar growth rather than a cartesian tree: a branch pushes outward and drifts sideways.
     * That is what coral does, and it makes the whole structure rotatable about the centre for
     * free — which is how the sway below can be a travelling wave instead of a wobble.
     */
    private fun buildCoral(): List<Twig> {
        val out = ArrayList<Twig>(320)
        val rnd = java.util.Random(CORAL_SEED)

        fun grow(startRad: Float, startAng: Float, len: Float, depth: Int, dSoFar: Float) {
            val drift = (rnd.nextFloat() - 0.5f) * 0.34f          // a tube is not a ruler
            val w = CORAL_WIDTH * Math.pow(0.70, depth.toDouble()).toFloat()
            var rad = startRad
            var ang = startAng
            var d = dSoFar
            val step = len / CORAL_SUBDIV
            for (s in 0 until CORAL_SUBDIV) {
                val rad2 = rad + step
                val ang2 = ang + drift / CORAL_SUBDIV
                // TRUE path length, not radial distance — a tube that leans sideways is a longer
                // tube, and air arriving at its end arrives later. This is the whole reason `d`
                // exists as its own quantity rather than being read off the radius.
                val segLen = hypot(rad2 - rad, ((rad + rad2) / 2f) * (ang2 - ang))
                out.add(
                    Twig(
                        rad, ang, d, rad2, ang2, d + segLen, w,
                        tip = (s == CORAL_SUBDIV - 1 && depth == CORAL_DEPTH)
                    )
                )
                rad = rad2; ang = ang2; d += segLen
            }
            if (depth >= CORAL_DEPTH) return
            val spread = CORAL_SPREAD * (1f + 0.28f * depth) * (0.8f + 0.4f * rnd.nextFloat())
            grow(rad, ang + spread, len * CORAL_DECAY, depth + 1, d)
            grow(rad, ang - spread, len * CORAL_DECAY, depth + 1, d)
        }

        val trunk = 1f / (1f + CORAL_DECAY + CORAL_DECAY * CORAL_DECAY +
            CORAL_DECAY * CORAL_DECAY * CORAL_DECAY)
        for (i in 0 until CORAL_TRUNKS) {
            val a = (Math.PI.toFloat() * 2f * i / CORAL_TRUNKS) - Math.PI.toFloat() / 2f
            grow(0f, a, trunk, 0, 0f)
        }

        // normalise the tube distance so the fill front runs 0 → 1 no matter how it grew
        val maxD = out.maxOf { it.d1 }.coerceAtLeast(0.0001f)
        return out.map {
            Twig(it.rad0, it.ang0, it.d0 / maxD, it.rad1, it.ang1, it.d1 / maxD, it.width, it.tip)
        }
    }

    /**
     * A rigid rotation of the shell at tube-depth `d`. Every point at the same depth moves by the
     * same angle, so the network can never tear itself apart at a join — and because the offset
     * lags with depth, what you see is a wave travelling out along the tubes. Weighted by d² so
     * the trunks are steady and only the fine ends move, which is how anything in water behaves.
     */
    private fun sway(d: Float) =
        CORAL_SWAY * d * d * sin(elapsed * CORAL_SWAY_RATE - d * CORAL_SWAY_WAVE)

    /**
     * CORAL — the lung as a reef.
     *
     * A branching network of tubes running from the still centre outward, and the breath is
     * literally AIR ENTERING THEM: a front travels up the tubes on the in-breath, lighting each
     * one as it passes and swelling it, then recedes on the out-breath. The front is soft, so
     * what you see is filling rather than a switch being thrown. At full breath the tips —
     * alveoli, polyps, whichever word you want — are lit and open.
     *
     * The whole structure also drifts on a slow current, and reaches further out as it fills.
     */
    private fun coral(cv: Canvas, cx: Float, cy: Float, r: Float, maxR: Float, ring: Int, body: Int) {
        val breath = breathNorm(r, maxR)
        val front = breath * 1.06f                       // a little overshoot, so full is FULL
        val span = (maxR * CORAL_REACH - coreR).coerceAtLeast(1f) * (0.80f + 0.30f * breath)
        val dim = Palette.mix(pal.bg, ring, 0.30f)

        stroke.strokeCap = Paint.Cap.ROUND
        for (t in coral) {
            val lit = smooth((front - t.d0) / CORAL_SOFT)
            val a0 = t.ang0 + sway(t.d0)
            val a1 = t.ang1 + sway(t.d1)
            val r0 = coreR + t.rad0 * span
            val r1 = coreR + t.rad1 * span
            val x0 = cx + r0 * cos(a0); val y0 = cy + r0 * sin(a0)
            val x1 = cx + r1 * cos(a1); val y1 = cy + r1 * sin(a1)

            stroke.color = blend(blend(dim, ring, lit), pal.holdFull, lit * lit * 0.42f)
            stroke.alpha = (70 + 185 * lit).toInt().coerceIn(0, 255)
            stroke.strokeWidth = maxR * t.width * (0.70f + 0.65f * lit)
            cv.drawLine(x0, y0, x1, y1, stroke)

            if (t.tip) {                                  // the ends open as the air arrives
                val tipR = maxR * 0.017f * (0.55f + 1.5f * lit)
                fill.color = blend(dim, pal.holdFull, lit)
                fill.alpha = (46 + 150 * lit).toInt().coerceIn(0, 255)
                cv.drawCircle(x1, y1, tipR * 2.2f, fill)
                fill.alpha = 255
                cv.drawCircle(x1, y1, tipR, fill)
            }
        }
        stroke.alpha = 255

        centre(cv, cx, cy, ring, body)
    }

    /**
     * The words layer.
     *
     * The count sits INSIDE the form — it is the thing you follow, so it belongs where your eyes
     * already are rather than below the shape. Its size was settled once in [layoutCore] against
     * the widest number this session can produce, so it never reflows and the centre it sits in
     * never has to resize to accommodate it.
     *
     * The instruction can be turned off entirely; and while it is on, the NEXT phase fades up
     * before the current one ends, so the body is told what is coming instead of being surprised
     * by it every time.
     */
    private fun words(cv: Canvas, cx: Float, cy: Float, maxR: Float, n: Now) {
        val a = (255 * wordAlpha).toInt().coerceIn(0, 255)
        if (a == 0) return

        // ── the count, in the core ──────────────────────────────────────────
        if (!finished) {
            val left = (n.len - n.within * n.len).toInt() + 1
            core.color = pal.text
            core.alpha = a
            cv.drawText("$left", cx, cy + coreBaselineDy, core)
        }

        if (!p.showWords) return

        label.alpha = a; sub.alpha = a
        val base = cy + maxR + WORD_DROP
        if (finished) { cv.drawText("here", cx, base, label); return }

        cv.drawText(n.name, cx, base, label)

        // ── prime the next phase ────────────────────────────────────────────
        val secsLeft = n.len - n.within * n.len
        if (secsLeft <= PRIME_SECONDS && phases.size > 1) {
            val next = phases[(n.index + 1) % phases.size].first
            val f = ((PRIME_SECONDS - secsLeft) / PRIME_SECONDS).coerceIn(0f, 1f)
            sub.alpha = (a * f * 0.75f).toInt().coerceIn(0, 255)
            cv.drawText("then $next", cx, base + 62f, sub)
            sub.alpha = a
        } else if (p.cycles > 1) {
            val round = (elapsed / p.cycleSeconds.coerceAtLeast(1)).toInt() + 1
            cv.drawText("round $round of ${p.cycles}", cx, base + 62f, sub)
        }
    }

    companion object {
        /** In: gathers, then fills steadily. Out: releases at once, then trails away. */
        private const val IN_BIAS = 1.25f
        private const val OUT_BIAS = 0.72f
        private const val MIN_OPEN = 0.40f
        private const val SETTLED = 0.80f
        /** Smaller divisor = the breath reaches further out. */
        private const val RADIUS_DIVISOR = 2.7f

        /**
         * The still centre. It does NOT breathe: a number inside a shape that is changing size is
         * a number that keeps resizing itself — unreadable exactly when you are trying to follow
         * it. The breath happens *around* it.
         *
         * GAP_MM is real millimetres of air between the number and the ring around it, and it is
         * the fixed quantity — [layoutCore] shrinks the type rather than the gap.
         */
        private const val GAP_MM = 2.5f
        private const val COUNT_TEXT = 0.40f     // starting type size, as a fraction of the form
        private const val CORE_MIN = 0.24f
        private const val CORE_MAX = 0.52f

        /** BLOOM: how far past the breath's own radius the rings run. */
        private const val BLOOM_REACH = 2.6f
        private const val BLOOM_RINGS = 8

        /** GUILLOCHE: the rosette family. */
        private const val GUIL_CURVES = 9
        private const val GUIL_FAST = 9
        private const val GUIL_SLOW = 5
        private const val GUIL_REACH = 1.32f
        private const val GUIL_STEP = 1.5f       // degrees per point

        /** CORAL: the tube network. */
        private const val CORAL_SEED = 20260815L
        private const val CORAL_TRUNKS = 6
        private const val CORAL_DEPTH = 3
        private const val CORAL_SUBDIV = 3
        private const val CORAL_DECAY = 0.74f
        private const val CORAL_SPREAD = 0.17f   // radians a branch leans off its parent
        private const val CORAL_WIDTH = 0.030f   // trunk stroke, as a fraction of the form
        private const val CORAL_REACH = 1.55f
        private const val CORAL_SOFT = 0.24f     // how soft the air front is
        private const val CORAL_SWAY = 0.16f     // radians of drift at the tips
        private const val CORAL_SWAY_RATE = 0.9f
        private const val CORAL_SWAY_WAVE = 3.4f

        /** How far below the form the words sit. */
        private const val WORD_DROP = 210f
        /** How long before a phase ends the next one begins to fade up. */
        private const val PRIME_SECONDS = 1.6f
    }
}
