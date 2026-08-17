# BreathGate

**One breath before the door opens.**

When an app you have chosen comes to the foreground, BreathGate asks you for a breath, then asks
whether you still want to go in. Blockers get uninstalled at the exact moment they are needed.
This one asks for about twenty seconds and a decision, then steps aside whichever way you decide.

---

## Everything it can do

The app requests six permissions. This is all of them:

```
PACKAGE_USAGE_STATS        see which app came to the foreground
SYSTEM_ALERT_WINDOW        draw over that app
FOREGROUND_SERVICE         keep watching while the screen is on
POST_NOTIFICATIONS         show the notification Android requires of that service
RECEIVE_BOOT_COMPLETED     start again after a restart
dev.breathgate.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
                           added by the Android build tools; signature-level, and usable
                           only by this app itself
```

**Network access would appear on that list.** Read it for yourself before you install anything:

```
aapt2 dump permissions BreathGate.apk
```

What follows from that: the app runs entirely on your phone. Every setting stays in its own local
storage, and the whole thing works with the device in airplane mode. The release script reads this
same list on every build and stops if `INTERNET` has appeared.

That is the product. The rest is breathing exercises.

## What it does

- **Watches for the apps you choose** coming to the foreground.
- **Holds a breath** — box breathing (4·4·4·4) by default, or 4-7-8, or plain in-and-out, or any
  counts you like, for as many rounds as you set.
- **Draws it five ways**, all growing from the centre:
  - **orb** — one body filling and emptying
  - **bloom** — rings travelling outward, the cycle read as waves
  - **box** — a square whose dot walks one edge per phase
  - **guilloche** — a rosette that morphs as it breathes
  - **coral** — a lung whose tubes fill with air from the middle out
- **Asks in your words.** Every prompt is yours to edit, including the question at the end.
- **Comes back while you are still inside.** *Re-intervention*: it returns on its own timer during
  a long session, on a schedule you set per app.
- **Sounds, if you want it to.** A drone in the palette's own key, generated on the device sample
  by sample. A struck note on each phase change walks the tuning's degrees, so the melody follows
  the breath pattern.
- **Beats, if you want it to.** A heartbeat pulse at a resting rate, in colour and — with sound on
  — in the same envelope you can hear. One function drives both.

## Why Usage Access

Most apps in this category use an `AccessibilityService`, which hands them the contents of your
screen. This one uses **Usage Access**, which reports only which app came forward. Three reasons:

- Google Play has been tightening review of the accessibility API for apps outside accessibility.
- Android's **Advanced Protection Mode** revokes that API from such apps outright.
- Usage Access is enough for this job.

The trade is real: some finer-grained interventions stay out of reach this way. That is an
accepted cost, written down here so you can weigh it.

## Install

**Obtainium** is the recommended route. It tracks releases from this repository and updates in
place, and it side-steps Android's *Restricted Settings*, which otherwise blocks a sideloaded app
from being granted Usage Access.

**F-Droid** works too, through this project's own repository:

> <https://aureliusxradix.github.io/fdroid/repo?fingerprint=A859EAFDA0219053FFEEEA63155CDB4C62B12959F61DF53A3839B5554BB8E504>

Opening that link on a phone with F-Droid installed adds the repository in one tap. By hand
instead: **Settings → Repositories → +**, paste the URL up to the `?`, and check the fingerprint
matches:

```
A859EAFDA0219053FFEEEA63155CDB4C62B12959F61DF53A3839B5554BB8E504
```

That repository ships the same APKs published here, signed with the same key, so an app installed
one way updates the other way. Stores that rebuild and re-sign an app work differently: there,
switching means an uninstall first.

If you install the APK by hand and a permission switch appears greyed out, that is Android holding
it shut because the app was sideloaded:

> Settings → Apps → BreathGate → ⋮ (top right) → **Allow restricted settings**

Then grant **Usage access** and **Display over other apps**. The app walks you through both on
first open.

**Minimum Android 8.0 (API 26).**

## Build it yourself

```
BG_KEYSTORE_PROPS=/path/to/keystore.properties   # optional; omit this for an unsigned build
./gradlew assembleGiftRelease
```

Requires JDK 17 or later and an Android SDK with API 35 and build-tools 35.0.1.

The build is reproducible: independent builds of this source produce a byte-identical APK, so you
can rebuild a release and compare it against the published one.

Two flavours share one codebase:

- **gift** — Obtainium, F-Droid, direct APK. Free, and it includes the donation screen.
- **play** — the Store build, which omits the donation screen. Google Play exempts donations only
  for registered tax-exempt organisations. The split lives in the source sets, so the play build
  contains zero payment addresses, which `strings` will confirm.

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

**GPL-3.0-only**, and the reasoning matters more than the formality.

This app is given away. A permissive licence would let someone wrap it, add the tracking it
refuses, and sell it back to you. Copyleft turns *stays open* into a fact: use it, study it,
change it, share it, and anyone distributing a changed version passes the same freedom on.

In practice it protects one thing above all. A fork that adds an `INTERNET` permission is a
different program wearing this one's clothes.

## Supporting it

Free, and staying free. Every feature is in the app already.

- **Liberapay** — <https://liberapay.com/Aureliusxradix> (recurring; the platform takes no cut)
- **Monero** — `87TdnZcH3TSLtneBhq9qzx9hKMUWfBUeTfF6RERT9NiVb4VJ6q3EA4YMwooSaTyxq3XVLcUrzU4QA9WdvaGPBSurA4WYwqs`

Both are on the app's own support screen too, with a copy button for the second, since ninety-five
characters is a lot to retype.

## Asking for something

Almost everything here arrived because someone used the app and said what was wrong. Open an
issue, or write to `infobreathgate.gladiator565@passmail.net`.

The most useful report names the **sensation**: "it sounds spiky", "the text overlaps", "I looked
for that setting and found something else". Each of those led straight to a mechanism, faster than
a theory would have. Describing what it was like is the part only you can do.
