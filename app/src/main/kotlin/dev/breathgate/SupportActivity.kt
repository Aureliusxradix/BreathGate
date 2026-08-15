package dev.breathgate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * SUPPORT — the gift, and the line back.
 *
 * Two things his roadmap asked for, on one screen because they are the same gesture in opposite
 * directions: **a way to give something back, and a way to ask for something.**
 *
 * ⚠ NOTHING HERE TOUCHES THE NETWORK. There is no INTERNET permission; every action on this
 * screen is an **intent handed to an app that already has one** — a mail client, a browser. The
 * app does not send, fetch, submit or check anything. See [Contact] for why that is the design
 * and not a limitation: **the permission count is the product.**
 *
 * The donation half is `gift`-flavour only ([Flavour.DONATIONS]) — Play exempts donations only
 * for tax-exempt organisations, and a solo developer is not one.
 */
class SupportActivity : AppCompatActivity() {

    private lateinit var p: Prefs
    private lateinit var pal: Palette
    private lateinit var root: LinearLayout

    private val version: String get() = BuildConfig.VERSION_NAME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = Prefs(this)
        pal = Palette.current(p)

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 160)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            addView(root)
            clipToPadding = false
        }
        setContentView(scroll)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(v.paddingLeft, top + 56, v.paddingRight, v.paddingBottom)
            insets
        }
        render()
    }

    private fun render() {
        root.removeAllViews()

        if (Flavour.DONATIONS) sectionGift()
        sectionAsk()

        sectionSource()

        gap(56)
        fine(
            "BreathGate $version · GPL-3.0. No internet permission, no account, no analytics, " +
                "no crash reporting — nothing this app knows can leave the phone. " +
                "Everything on this screen hands the job to an app that you already trust with it."
        )
    }

    /**
     * The source, reachable from inside the app.
     *
     * Under the GPL this is not decoration. The licence gives everyone holding the program the
     * right to read it, change it and pass it on — and a binary with no route back to its source
     * satisfies that in letter and nobody in practice. It is also where the app's central claim
     * stops being a claim: *no internet permission* is checkable from the manifest, and
     * everything behind it is checkable here.
     */
    private fun sectionSource() {
        gap(64); title("What it is made of")
        gap(10)
        body(
            "All of it is open, under the GPL — which means nobody can take this, close it, add " +
                "the tracking it refuses, and sell it back to you. Read it, change it, pass it on."
        )
        gap(16)
        pill("Read the source", primary = false) { openUrl(Contact.SOURCE) }
        gap(14)
        root.addView(TextView(this).apply {
            text = Contact.SOURCE
            textSize = 13f
            setTextColor(pal.textSoft)
            setTextIsSelectable(true)
        })
    }

    // ── the gift ────────────────────────────────────────────────────────────
    private fun sectionGift() {
        title("The gift")
        gap(10)
        body(
            "This app is free and always will be. It has no ads, no subscription, no account and " +
                "nothing to unlock — and the source is open, so it can never be closed again by " +
                "anyone, including me."
        )
        gap(14)
        body(
            "What flows through can only be transmitted, never owned. If it has been worth " +
                "something to you and you are moved to send something back, there is a way. If " +
                "you are not, use it anyway — that was always the arrangement."
        )

        val ways = listOfNotNull(
            Flavour.MONERO.ifBlank { null }?.let { Triple("Monero", it, false) },
            Flavour.LIBERAPAY.ifBlank { null }?.let { Triple("Liberapay", it, true) },
            Flavour.KOFI.ifBlank { null }?.let { Triple("Ko-fi", it, true) }
        )

        gap(28)
        if (ways.isEmpty()) {
            // Rendered honestly rather than shipping a placeholder address. A wrong wallet
            // address is money posted into a void with no way to recall it.
            card(
                "No donation channel is open yet.\n\n" +
                    "If you want to send something, write to the address below and one will be " +
                    "opened for you. Saying so plainly beats printing an address nobody checked."
            )
        } else {
            ways.forEach { (name, value, isUrl) ->
                gap(16)
                heading(name)
                if (isUrl) {
                    pill("Open $name", primary = true) { openUrl(value) }
                } else {
                    // 95 characters. Monospace so the glyphs line up and a person can actually
                    // check what they pasted against what is on screen — which is the only
                    // verification available to them at the far end of this.
                    root.addView(TextView(this).apply {
                        text = value
                        textSize = 12f
                        typeface = android.graphics.Typeface.MONOSPACE
                        setTextColor(pal.textSoft)
                        setTextIsSelectable(true)
                        setLineSpacing(4f, 1f)
                        setBackgroundColor(Palette.mix(pal.bg, pal.text, 0.06f))
                        setPadding(24, 24, 24, 24)
                    })
                    gap(14)
                    pill("Copy the $name address", primary = true) { copy(name, value) }
                    gap(12)
                    // A wallet app registers `monero:` — if one is installed this fills in the
                    // address for them. If none is, nothing is lost: the copy button above is
                    // the path, and this one simply says so instead of failing silently.
                    pill("Open in a wallet app", primary = false) {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("monero:$value")))
                        }.onFailure { toast("No wallet app installed — copy the address instead.") }
                    }
                }
            }
        }
    }

    // ── the line back ───────────────────────────────────────────────────────
    private fun sectionAsk() {
        gap(if (Flavour.DONATIONS) 64 else 0)
        title("Ask for something")
        gap(10)
        body(
            "Almost everything in this app came from someone using it and saying what was wrong. " +
                "The gate's whole shape, the coral, the counts, the buttons you just pressed — " +
                "all of it arrived that way."
        )
        gap(14)
        fine(
            "There is no form here, and there will not be one: a form needs an internet " +
                "permission, and that permission is the one thing this app does not have. " +
                "Your mail app has it. This hands the message to your mail app and steps back — " +
                "which also means you can see exactly what you are sending, and to whom."
        )

        gap(26)
        pill("Write a feature request", primary = true) { mail() }
        gap(12)
        pill("Copy the address instead", primary = false) { copy("address", Contact.EMAIL) }
        gap(14)
        root.addView(TextView(this).apply {
            text = Contact.EMAIL
            textSize = 13f
            setTextColor(pal.textSoft)
            setTextIsSelectable(true)
        })
    }

    // ── handing off ─────────────────────────────────────────────────────────
    /**
     * `ACTION_SENDTO` with a `mailto:` URI resolves ONLY to mail apps — unlike `ACTION_SEND`,
     * which offers every share target on the phone and turns a bug report into a social post by
     * accident. If nothing handles it, say so and leave the address on screen to be copied.
     */
    private fun mail() {
        val uri = Uri.parse(
            "mailto:${Uri.encode(Contact.EMAIL)}" +
                "?subject=${Uri.encode(Contact.featureSubject(version))}" +
                "&body=${Uri.encode(Contact.featureBody(version, Build.VERSION.SDK_INT))}"
        )
        val i = Intent(Intent.ACTION_SENDTO, uri)
        runCatching { startActivity(i) }.onFailure {
            toast("No mail app found — the address is on screen; copy it.")
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast("No browser found.") }
    }

    private fun copy(what: String, value: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("BreathGate $what", value))
        toast("Copied.")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // ── blocks ──────────────────────────────────────────────────────────────
    private fun title(s: String) = root.addView(TextView(this).apply {
        text = s; textSize = 30f; setTextColor(pal.text)
    })

    private fun heading(s: String) = root.addView(TextView(this).apply {
        text = s.uppercase(); textSize = 13f; setTextColor(pal.textSoft)
        letterSpacing = 0.14f; setPadding(0, 0, 0, 10)
    })

    private fun body(s: String) = root.addView(TextView(this).apply {
        text = s; textSize = 15f; setTextColor(pal.text); setLineSpacing(8f, 1f)
    })

    private fun fine(s: String) = root.addView(TextView(this).apply {
        text = s; textSize = 13f; setTextColor(pal.textSoft); setLineSpacing(6f, 1f)
    })

    private fun card(s: String) = root.addView(TextView(this).apply {
        text = s; textSize = 14f; setTextColor(pal.text); setLineSpacing(6f, 1f)
        setBackgroundColor(Palette.mix(pal.bg, pal.text, 0.06f))
        setPadding(40, 36, 40, 36)
    })

    private fun pill(s: String, primary: Boolean, onTap: () -> Unit) =
        root.addView(Pill.make(this, pal, s, primary, onTap), Pill.row(this, 0f))

    private fun gap(h: Int) = root.addView(View(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
    })
}
