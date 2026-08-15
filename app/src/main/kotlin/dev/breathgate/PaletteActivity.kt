package dev.breathgate

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Build a palette by hand.
 *
 * Seven colours, each with the role it plays written next to it — not "colour 3". Every change
 * repaints the live preview above, because a palette is a set of relationships and a hex code
 * on its own tells you nothing about how it will sit beside the other six.
 *
 * Start-from buttons copy a built-in set, so the usual path is "take the one that is nearly
 * right and move two colours" rather than picking seven from nothing.
 */
class PaletteActivity : AppCompatActivity() {

    private lateinit var p: Prefs
    private lateinit var colours: MutableList<Int>
    private lateinit var root: LinearLayout
    private lateinit var preview: PreviewStrip

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        colours = p.customPalette.toMutableList()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 64, 56, 120)
        }
        val scroll = ScrollView(this).apply { addView(root); clipToPadding = false }
        setContentView(scroll)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(v.paddingLeft, top + 48, v.paddingRight, v.paddingBottom)
            insets
        }
        render()
    }

    private fun render() {
        root.removeAllViews()
        root.setBackgroundColor(Color.parseColor("#101312"))

        head("Build a palette", 26f, "#E6EAE8")
        head("Seven colours, and what each one does.", 14f, "#9BA6A2")

        preview = PreviewStrip(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 340)
        }
        preview.colours = colours
        root.addView(space(28)); root.addView(preview)

        // start-from
        root.addView(space(22))
        head("Start from", 12f, "#9BA6A2")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Palette.BUILT_IN.forEach { b ->
            row.addView(Button(this).apply {
                text = b.name; isAllCaps = false; textSize = 13f
                setTextColor(b.ring); setBackgroundColor(Color.TRANSPARENT)
                setPadding(0, 0, 34, 0)
                setOnClickListener { colours = b.asList().toMutableList(); render() }
            })
        }
        root.addView(row)

        // the seven
        Palette.ROLES.forEachIndexed { i, role -> colourRow(i, role) }

        root.addView(space(36))
        root.addView(Button(this).apply {
            text = "Use this palette"
            isAllCaps = false
            setOnClickListener {
                p.customPalette = colours
                p.palette = Palette.CUSTOM
                finish()
            }
        })
        head("Saved as your custom set and selected. The built-in ones stay where they are.", 12f, "#6E807B")
    }

    /** One role: its name, its hex, and three sliders. Hex and sliders stay in step. */
    private fun colourRow(i: Int, role: String) {
        root.addView(space(26))
        val label = TextView(this).apply {
            text = role; textSize = 14f; setTextColor(Color.parseColor("#E6EAE8"))
        }
        val hex = EditText(this).apply {
            setText(hexOf(colours[i]))
            textSize = 15f
            setTextColor(colours[i])
            inputType = InputType.TYPE_CLASS_TEXT
        }
        root.addView(label); root.addView(hex)

        val bars = mutableListOf<SeekBar>()
        fun push() {
            val c = Color.rgb(bars[0].progress, bars[1].progress, bars[2].progress)
            colours[i] = c
            hex.setText(hexOf(c)); hex.setTextColor(c)
            preview.colours = colours; preview.invalidate()
        }
        listOf("R" to Color.red(colours[i]), "G" to Color.green(colours[i]), "B" to Color.blue(colours[i]))
            .forEach { (_, v) ->
                val sb = SeekBar(this).apply {
                    max = 255; progress = v
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, pr: Int, user: Boolean) { if (user) push() }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })
                }
                bars.add(sb); root.addView(sb)
            }

        hex.setOnFocusChangeListener { _, focused ->
            if (focused) return@setOnFocusChangeListener
            runCatching { Color.parseColor(hex.text.toString().trim()) }.onSuccess { c ->
                colours[i] = c
                bars[0].progress = Color.red(c); bars[1].progress = Color.green(c); bars[2].progress = Color.blue(c)
                hex.setTextColor(c)
                preview.colours = colours; preview.invalidate()
            }.onFailure { hex.setText(hexOf(colours[i])) }
        }
    }

    private fun hexOf(c: Int) = String.format("#%06X", 0xFFFFFF and c)

    private fun head(s: String, size: Float, colour: String) = root.addView(TextView(this).apply {
        text = s; textSize = size; setTextColor(Color.parseColor(colour)); setPadding(0, 6, 0, 0)
    })

    private fun space(h: Int) = View(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
    }

    /** A still frame of the gate — the form, an outline, and both hold colours, on the ground. */
    class PreviewStrip(ctx: android.content.Context) : View(ctx) {
        var colours: List<Int> = Palette.BUILT_IN[0].asList()
        private val pt = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: android.graphics.Canvas) {
            val (bg, fill, ring) = Triple(colours[0], colours[1], colours[2])
            pt.style = android.graphics.Paint.Style.FILL
            pt.color = bg
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pt)

            val cx = width / 2f; val cy = height / 2f - 14f
            val r = minOf(width, height) / 3.6f
            pt.color = fill; canvas.drawCircle(cx, cy, r, pt)
            pt.style = android.graphics.Paint.Style.STROKE; pt.strokeWidth = 5f
            pt.color = ring; canvas.drawCircle(cx, cy, r, pt)

            // the two holds, as the dot would wear them
            pt.style = android.graphics.Paint.Style.FILL
            pt.color = colours[3]; canvas.drawCircle(cx - r - 34f, cy, 15f, pt)
            pt.color = colours[4]; canvas.drawCircle(cx + r + 34f, cy, 15f, pt)

            pt.color = colours[5]; pt.textSize = 34f
            pt.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText("breathe in", cx, cy + r + 52f, pt)
            pt.color = colours[6]; pt.textSize = 26f
            canvas.drawText("then hold", cx, cy + r + 90f, pt)
        }
    }
}
