package dev.breathgate

/**
 * THE LINE OUT — and there is exactly one of them, written once.
 *
 * ⚠ THE CONSTRAINT THAT SHAPES ALL OF THIS: **there is no INTERNET permission and there never
 * will be.** So this app cannot send a message, cannot submit a form, cannot check for a reply
 * and cannot reach a donation page. What it can do is **hand the request to something that can** —
 * an `ACTION_SENDTO` intent to whatever mail app is installed, or `ACTION_VIEW` to a browser.
 * The app never touches the network; another app the person already trusts does.
 *
 * ⭐ That is not a workaround. **The permission count is the product**, and a feature-request form
 * inside the app would cost the one claim the whole thing rests on. A link out costs nothing.
 *
 * The address is a **single constant referenced everywhere**. An address retyped in three places
 * is an address with a typo in one of them, and a mail address with a typo is a silent dead-drop:
 * everything looks like it worked and nothing ever arrives.
 */
object Contact {

    /** His alias for the app, given 2026-08-15. Not tied to his name — a dedicated channel. */
    const val EMAIL = "infobreathgate.gladiator565@passmail.net"

    /**
     * WHERE THE SOURCE LIVES — and under the GPL this is not a courtesy link.
     *
     * The licence gives everyone who has the program the right to *have* the program: to read it,
     * change it and pass it on. A binary handed over with no route back to its source technically
     * satisfies GPL-3 only through the written-offer clause, and practically satisfies nobody.
     * **A person holding this app should be able to find what it is made of from inside it**, not
     * by knowing to search for it.
     *
     * It is also the honest end of the app's central claim. "No INTERNET permission" is checkable
     * from the manifest, and everything *behind* that claim is checkable here.
     *
     * ⚠ One line to change if the account name differs — and it is referenced in exactly one
     * place, for the same reason the address is.
     */
    const val SOURCE = "https://github.com/aureliusxradix/breathgate"

    /**
     * ⚠ THE DONATION CHANNELS LIVE IN `Flavour`, NOT HERE — one per source set.
     *
     * They started in this file, which is shared, and the result was that the **Play build's dex
     * still contained the wallet address** even though no screen in it could ever show one. The
     * behaviour was right and the binary told a different story. Moving them into the flavour
     * makes the split true where it can be checked: `strings` on the play APK finds no address at
     * all, which is a claim anyone can verify without trusting the UI logic.
     *
     * ⭐ Same principle as the missing INTERNET permission: **make the guarantee structural, so
     * it can be inspected rather than believed.**
     */

    fun featureSubject(version: String) = "BreathGate $version — feature request"

    /**
     * A scaffold, because a blank mail body is the reason most feature requests never get sent.
     * It asks for the three things that make a request actionable and nothing else.
     */
    fun featureBody(version: String, android: Int) = """
        |
        |
        |— what I was doing:
        |
        |— what I wanted instead:
        |
        |— why it matters to me:
        |
        |
        |(BreathGate $version · Android SDK $android)
    """.trimMargin()
}
