package dev.breathgate

/**
 * PLAY — the Store build. No donate button.
 *
 * ⚠ Not a stylistic difference: Play's payments policy exempts donations only for registered
 * tax-exempt organisations. A solo developer taking donations through an app distributed on Play
 * is the one live policy risk in this project (see the ROADMAP's Distribution section), and the
 * cheapest resolution is simply not to ship the button there. The feature-request line stays —
 * that is a mail intent, not a payment.
 */
object Flavour {
    const val NAME = "play"
    const val DONATIONS = false

    /**
     * ⭐ EMPTY, AND THAT IS THE POINT. These constants live per-flavour rather than in the shared
     * `Contact` precisely so the Play build carries **no wallet address in its binary at all** —
     * not merely no screen that shows one. `strings` on this APK finds nothing, which is a claim
     * a reviewer can check without trusting any of our UI logic.
     */
    const val MONERO = ""
    const val LIBERAPAY = ""
    const val KOFI = ""
}
