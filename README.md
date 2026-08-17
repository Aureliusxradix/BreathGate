# BreathGate

**One breath before the door opens.**

BreathGate puts a conscious gate in front of an unconscious intake. When an app you have chosen
comes to the foreground, it asks you for a breath — and then asks whether you still want to go in.

It is not a blocker. Blockers get uninstalled at the exact moment they are needed. This asks for
about twenty seconds and a decision, and then gets out of the way whichever way you decide.

---

## The one thing that matters most

**There is no `INTERNET` permission, and there never will be.**

Not "we don't collect data" — a promise you would have to take on trust. The app *cannot* reach
the network, and you can check that yourself from the manifest before you install it:

```
aapt2 dump permissions BreathGate.apk | grep INTERNET     # returns nothing
```

The full list, in the order `aapt2` prints it:

```
PACKAGE_USAGE_STATS                    which app just came to the foreground
SYSTEM_ALERT_WINDOW                    draw the breath in front of it
FOREGROUND_SERVICE (+ SPECIAL_USE)     keep watching while the screen is on
POST_NOTIFICATIONS                     the notification Android requires of that service
RECEIVE_BOOT_COMPLETED                 start again after a restart
dev.breathgate.DYNAMIC_RECEIVER_...    added by the Android build tools, not by this app;
                                       signature-level and only usable by this app itself
```

Nothing else. There is no account, no sync, no analytics, no crash reporting and no server —
because there is nothing that could talk to one.

The release script refuses to publish a build that has acquired that permission, so it cannot
arrive by accident later. **The missing permission is the product.**

## What it does

- **Watches for apps you choose** coming to the foreground.
- **Holds a breath** — box breathing (4·4·4·4) by default, or 4-7-8, or simply in-and-out, or any
  counts you like. One to many rounds.
- **Draws it five ways**, all growing from the centre: an **orb** filling and emptying · a
  **bloom** of rings travelling outward · a **box** whose dot walks one edge per phase · a
  **guilloche** rosette · **coral**, a lung whose tubes fill with air from the middle out.
- **Asks in your words.** Every prompt is editable — the instructions, and the question at the end.
- **Comes back while you are still inside.** *Re-intervention*: the gate returns on its own timer
  during a long session, not only when you re-open the app. Set per app.
- **Optionally sounds.** A drone in the palette's own key, generated on the device sample by
  sample — no audio files, no downloads. A bell on each phase change walks the tuning's degrees,
  so the melody is the breath pattern itself.
- **Optionally beats.** A heartbeat pulse at a resting rate, in colour and — with sound on — in
  the same envelope you can hear. One function drives both.

## Why Usage Access and not an AccessibilityService

Most apps in this category use an `AccessibilityService`, because it gives far more control. This
one deliberately does not:

- Google Play has been tightening review of the accessibility API for non-accessibility apps.
- Android's **Advanced Protection Mode** revokes that API from such apps outright.
- Usage Access does this job without asking for the keys to your screen contents.

The trade is real — some finer-grained interventions are simply not possible this way. That is an
accepted cost, not an oversight.

## Install

**Obtainium** is the recommended route: it tracks releases from this repository and updates in
place. It also side-steps Android's *Restricted Settings*, which otherwise blocks a sideloaded app
from being granted Usage Access.

**F-Droid** works too, via this project's own repository:

> <https://aureliusxradix.github.io/fdroid/repo?fingerprint=A859EAFDA0219053FFEEEA63155CDB4C62B12959F61DF53A3839B5554BB8E504>

Opening that link on a phone with F-Droid installed adds the repository in one tap. To add it by
hand instead: **Settings → Repositories → +**, paste the URL without the fingerprint, and check the
fingerprint matches:

```
A859EAFDA0219053FFEEEA63155CDB4C62B12959F61DF53A3839B5554BB8E504
```

⭐ **This repository ships the same APKs published here, signed with the same key** — so switching
between the two never means an uninstall, and an app installed one way updates the other way.
That is not true of apps rebuilt and re-signed by a store.

If you install the APK by hand and a permission switch appears greyed out, that is Android holding
it shut, not a bug:

> Settings → Apps → BreathGate → ⋮ (top right) → **Allow restricted settings**

Then grant **Usage access** and **Display over other apps**. The app explains both on first open.

**Minimum Android 8.0 (API 26).**

## Build it yourself

```
BG_KEYSTORE_PROPS=/path/to/keystore.properties   # optional; omit for an unsigned build
./gradlew assembleGiftRelease
```

Requires JDK 17+ and an Android SDK with API 35 and build-tools 35.0.1. There are two flavours,
one codebase:

- **gift** — Obtainium, F-Droid, direct APK. Free, no billing, includes the donation screen.
- **play** — the Store build, with no donation screen. Google Play exempts donations only for
  registered tax-exempt organisations, so the button is simply absent there. That split is
  enforced in the source set, not in a runtime check: the play build contains no payment
  address at all, which you can confirm with `strings`.

## How this was made

Most of the code in this repository was written by an AI (Claude), directed by me.

I chose what the app does, what it declines to do, and what stays out of it. Each feature came
from using the app and finding a problem: the animation was flat, the sound was too bright, two
pieces of text overlapped at the end of a breath. The commit messages and the changelog record
what was wrong before each fix.

I am responsible for the result. If the app misbehaves, that is mine to answer for.

Two questions usually follow.

**Does it carry copyright?** The law here is unsettled, and I will say so. The licence is GPL-3.0
and the intent is fixed: this is given away, and anyone distributing a modified version passes on
the same freedom. Where copyright applies, the licence binds it. Where the law is silent, the code
stays free to use.

**Has anyone checked it?** Every claim below is one you can verify yourself:

- There is no `INTERNET` permission. It is visible in the manifest before you install.
- The release script exits with an error if a build acquires one.
- The build is reproducible. Independent builds of this source produce a byte-identical APK, so
  you can rebuild it and compare.
- The signing certificate SHA-256 is published with each release, and every release is downloaded
  back and compared against the signed build before it is announced.

Some people will decline to install software written this way. That is a reasonable position to
hold, and it is why this section is here.

## Licence

**GPL-3.0-only.** Deliberately, and the reasoning is the point rather than a formality.

This app is a gift. A permissive licence would let anyone wrap it, add the telemetry it refuses,
and sell it back. **Copyleft is what makes "the gift cannot be owned" legally operative instead of
merely stated.** Use it, study it, change it, share it — and anyone who distributes a changed
version passes the same freedom on.

The invariant that licence protects in practice: **a fork that adds an `INTERNET` permission is a
different program wearing this one's clothes.**

## Supporting it

Free, and staying free — nothing is behind a donation and nothing will be.

- **Liberapay** — <https://liberapay.com/Aureliusxradix> (recurring; the platform takes no cut)
- **Monero** — `87TdnZcH3TSLtneBhq9qzx9hKMUWfBUeTfF6RERT9NiVb4VJ6q3EA4YMwooSaTyxq3XVLcUrzU4QA9WdvaGPBSurA4WYwqs`

Both are in the app's own support screen too, with a copy button for the second one, since
ninety-five characters is not something anyone should be retyping.

## Asking for something

Almost everything in this app arrived because someone used it and said what was wrong. The gate's
shape, the visuals, the counts, the buttons — all of it came that way.

There is no feedback form in the app and there will not be one: a form needs an internet
permission, and that permission is the one thing this app does not have. Open an issue here, or
write to **infobreathgate.gladiator565@passmail.net**.

The most useful bug report names the *sensation* — "spiky", "it clips", "the text overlaps". Every
one of those has pointed straight at a mechanism.
