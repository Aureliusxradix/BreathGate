# Changelog

Versions before 0.12.0 were distributed privately while the app was shaped against daily use.
They are listed because a project that appears fully formed is hiding how it got there, and the
mistakes below are the most useful part of this file.

## 0.14.2 — 2026-09-08

**Store metadata rides in the tree.** F-Droid reads an app's title and description from the
tagged commit it builds, and the Fastlane files landed after 0.14.1 was tagged. This release
exists to carry them, so the listing on f-droid.org reads the same as the one here.

- `fastlane/metadata/android/en-US/`: title, short and full description, per-version changelogs,
  the icon at 512 px.
- README names the maker and the app's page; the BreathGate on Apple's App Store is another
  maker's work.
- A stray second Gradle wrapper at `gradle/gradle/wrapper/` is gone.
- The app itself is 0.14.1 under a new number.

## 0.14.1 — 2026-08-20

**The gate stopped watching, and said nothing.** Reported the same day: *"not doing the breath
check even though it is theoretically on, stopped all of a sudden."*

- **The watch loop can no longer stop.** 0.14.0 made the loop re-post itself only while a cached
  screen-state flag was true, and that flag was maintained by a broadcast receiver — which left the
  whole watcher with a single ignition path. One missed screen-on broadcast and it was dead until
  the service restarted, with every outward sign still normal. The loop now always re-posts and
  asks the system for the screen state as it runs. The receiver is still there, but only to react
  faster; if every one of its broadcasts were dropped, the gate would still work. The battery
  saving is unchanged — the cost was never the tick, it was the usage query that gets skipped.
- **Grace windows are measured on a clock that runs.** Re-intervention used a clock that stops
  counting during deep sleep, so a 30-minute window could stay open across a night of standby and
  the gate would not appear the next morning.
- **The notification says when something is wrong.** With no network and no telemetry it is the
  app's only voice, and it read "One breath before the door opens" while the watcher was doing
  nothing. It now reports missing Usage Access, missing overlay permission, being paused, or
  having no apps chosen — and tapping it opens the app.

## 0.14.0

- **Fixed: the app was briefly visible before the gate appeared.** Two causes compounding. The
  watcher polled every 350 ms, so an app could be up and drawing for a third of a second before it
  was even noticed — a doorman who arrives after you are through the door is a receipt. It now
  polls every 150 ms while the screen is on, and **not at all while the screen is off**, which is
  where the budget comes from: the old loop ran all night for no possible benefit. It also backs
  off again once you are inside an app it has already let through, since re-intervention is
  measured in minutes.
- The overlay's background is now set before the window is added rather than after, which removes
  a single transparent frame at exactly the moment you were looking at it.
- Unlocking the screen checks immediately instead of waiting for the next poll.

## 0.13.1

- Liberapay added beside Monero on the support screen. The two are different in kind rather than
  redundant: Monero has no intermediary and nobody who can decide to stop it; Liberapay is
  reachable by the far larger number of people who want to send a few dollars a month without
  installing anything.

## 0.13.0

- **The sound has a pitch control.** The struck notes were pinned two octaves above the drone,
  which is a defensible place to put a melody and a terrible place to have no choice about. Four
  positions — deep, low, mid, bright — and the default moved down one, because the only person who
  had actually listened to it said it was too bright.
- **And a separate brightness control**, because "too high" has two causes. Brightness is upper
  harmonics, which is why a low sound can still be piercing; turning it down darkens the bells and
  closes the pad's filter together.
- Struck notes can be switched off entirely, leaving only the drone.

## 0.12.1 — first public release

- Same as 0.12.0 below, with the source link inside the app pointing at where the source actually
  ended up. 0.12.0 was built against a repository that was never created, and a link compiled into
  an APK cannot be corrected afterwards — so it was replaced rather than reused.

## 0.12.0 — never released

- The source is public, and reachable from inside the app. Under the GPL that is not a courtesy
  link: a binary with no route back to its source satisfies the licence in letter and nobody in
  practice. It is also where the app's central claim stops being a claim — *no internet
  permission* is checkable from the manifest, and everything behind it is checkable here.
- Updates no longer require being on one particular wifi network.

## 0.11.1

- Fixed: **here** could flash back over the expanding circle when *go in* was tapped. The opening
  animation was setting the word's opacity absolutely, undoing the fade that had just removed it.

## 0.11.0

- Fixed: **here** and the closing question were drawn at the same place, on top of each other. The
  word now leaves as the question arrives.
- Fixed: the fifth visual (**coral**) was off the edge of a scrolling row and effectively
  invisible. The visuals are now a wrapping grid — nothing scrolls, everything is on screen.
- The way into the full settings moved to **Extras**, top right beside the name. It had been a
  button at the bottom of the screen, past everything it was offering to show you.
- Added a Monero address to the donation screen, with copy and open-in-wallet.

## 0.10.0

- **The sound was rebuilt.** The heartbeat envelope stepped instantly from silence, which is a
  click; layers summed past full scale into a hard clamp, which is distortion; and the melody
  voice was a single tone gliding continuously, which is a siren. Now: an attack on the beat, a
  headroom budget with the heart ducking everything else beneath it, a soft saturator in place of
  the clamp, and struck bells walking the tuning's degrees over a filtered pad.
- **The two gate buttons became real buttons** — full width, 64 dp tall, filled and outlined,
  stacked rather than side by side, with the declining choice at thumb reach. The filled one picks
  its own text colour by luminance so a custom palette cannot make it unreadable.
- **A simple screen, and it is now the default** — a pattern, how many rounds, how it looks, which
  apps. Everything else is one tap away and nothing is lost by going there.
- Added the **4-7-8** preset. A longer exhale than inhale is the part that actually calms.
- Added a support screen: the gift, and a line for feature requests. Both hand off to apps that
  already have network permission; this one still has none.

## 0.9.1

- Fixed: with the heartbeat on, the form locked at full brightness for the whole closing animation
  instead of settling.

## 0.9.0

- **Coral**, a new visual: a lung as a reef, its tubes filling with air from the centre outward.
- The still centre holding the count is now sized from the actual drawn width of the largest
  number, plus a real 2.5 mm of clearance, instead of a guessed fraction.
- Bloom's rings travel much further out.
- Guilloche: alternating rotation, per-curve petal counts and uneven breathing, so the figure
  morphs instead of spinning.
- Added an optional **heartbeat** pulse, and optional **sound** — a drone in each palette's own
  key, generated on the device.

## 0.8.0 and earlier

Private builds. The gate mechanism, the visuals, palettes and the custom palette builder, the
per-app re-intervention timer, editable prompts, the light gate, the test run, and the release
pipeline that verifies the no-network invariant before anything can be published.
