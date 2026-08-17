# Contributing

## The rule that overrides everything else

**No `INTERNET` permission.** Not behind a flag, not for a crash reporter, not for an update
check, not "temporarily". The release script refuses to publish a build that has one, and that
refusal is not an obstacle to route around — it is the product's only structural guarantee.

A change that needs the network is a change that belongs in a different program.

The same goes for `QUERY_ALL_PACKAGES` (use the `<queries>` declaration) and for
`AccessibilityService` (use Usage Access — see the README for why).

## What makes a good report

Name the **sensation**, not the diagnosis. "It sounds spiky." "The text overlaps." "I couldn't
find it." Every one of those has pointed straight at a real mechanism faster than a theory would
have. You do not need to know what caused it; you are the only person who can say what it was
like.

If you can, include the version (the support screen shows it) and what you were doing.

## Building

```
./gradlew assembleGiftDebug
```

JDK 17+, Android SDK with API 35 and build-tools 35.0.1. Two flavours: `gift` (open
distribution, includes the donation screen) and `play` (no donation screen — Google Play exempts
donations only for registered tax-exempt organisations). The difference lives in the flavour
source sets, so it is real in the binary and not just in the UI.

## Style

The comments in this codebase explain **why**, and often what went wrong before. That is
deliberate: a comment that restates the code is noise, and a comment recording the failure a line
prevents is the only documentation that survives being forgotten. Where you fix something subtle,
say what the wrong version did.

## On how this was written

Most of this code was written by an AI, directed by the author. It is stated in the README, and
it is worth knowing before you spend time on a patch: some people prefer to work on code written
by hand, and that is a reasonable preference.

If you do contribute, your work is yours and is credited as yours.

## Licence

By contributing you agree your work ships under **GPL-3.0-only**, like the rest of it.
