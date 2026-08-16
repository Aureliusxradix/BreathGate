# Changelog

Versions before 0.12.0 were distributed privately while the app was shaped against daily use.
They are listed because a project that appears fully formed is hiding how it got there, and the
mistakes below are the most useful part of this file.

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
