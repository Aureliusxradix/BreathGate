package dev.breathgate

/**
 * GIFT — Obtainium, F-Droid, a direct APK. Free, GPL-3.0, no billing, donate freely.
 *
 * Ruled 2026-08-13. The flavour split exists for exactly one reason: Google Play exempts
 * *tax-exempt* donations, which a solo developer is not — so the donate button is a policy risk
 * on Play and a plain fact everywhere else. One codebase, one constant, no branching in the UI
 * beyond `if (Flavour.DONATIONS)`.
 */
object Flavour {
    const val NAME = "gift"
    const val DONATIONS = true

    /**
     * The channels. The support screen renders only what is filled — a blank one does not appear.
     *
     * ⚠ A wallet address nobody verified is worse than no wallet address at all: it is money
     * posted into a void with no way to recall it. Nothing here is ever transcribed on trust.
     *
     * ✅ **The address below was CHECKED before it shipped** (2026-08-15), not eyeballed: decoded
     * from Monero base58 (8-byte blocks — not Bitcoin's), then its four-byte checksum recomputed
     * with **Keccak-256**. ⭐ Not SHA3-256: those two differ only in a padding byte, so reaching
     * for the one in every standard library would have "validated" the address while checking
     * nothing at all. 69 bytes decoded · network byte **42 = mainnet subaddress** · checksum
     * `4eefaf72` computed and matched.
     *
     * ⭐ And a subaddress is the right kind of address to publish: it receives exactly like a
     * primary one while revealing neither the primary address nor any link to his other
     * subaddresses.
     */
    const val MONERO = "87TdnZcH3TSLtneBhq9qzx9hKMUWfBUeTfF6RERT9NiVb4VJ6q3EA4YMwooSaTyxq3XVLcUrzU4QA9WdvaGPBSurA4WYwqs"
    const val LIBERAPAY = ""     // https://liberapay.com/…
    const val KOFI = ""          // https://ko-fi.com/…
}
