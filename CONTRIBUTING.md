# Contributing

## The rule that overrides everything else

**The app requests six permissions, and `INTERNET` stays off that list.** Behind a flag, for a
crash reporter, for an update check, temporarily — the answer is the same each time. The release
script reads the permission list on every build and stops if that one has appeared, and that stop
is the product's only structural guarantee.

A change that needs the network belongs in a different program.

Two other choices are settled the same way: use the `<queries>` declaration where you might reach
for `QUERY_ALL_PACKAGES`, and use Usage Access where you might reach for an
`AccessibilityService`. The README says why.

## What makes a good report

Name the **sensation** and leave the diagnosis to the code. "It sounds spiky." "The text
overlaps." "I looked for that setting and found something else." Each of those has led straight to
a real mechanism, faster than a theory would have. Describing what it was like is the part only
you can do.

If you can, include the version — the support screen shows it — and what you were doing.

## Building

```
./gradlew assembleGiftDebug
```

JDK 17 or later, and an Android SDK with API 35 and build-tools 35.0.1.

Two flavours: `gift` (open distribution, includes the donation screen) and `play` (the Store
build, which omits it, because Google Play exempts donations only for registered tax-exempt
organisations). The difference lives in the flavour source sets, so it holds in the binary as well
as in the interface.

The build is reproducible. Independent builds of this source produce a byte-identical APK.

## Style

The comments here explain **why**, and often what went wrong before. That is deliberate: a comment
restating the code is noise, while a comment recording the failure a line prevents is the
documentation that survives being forgotten. Where you fix something subtle, say what the wrong
version did.

## On how this was written

Most of this code was written by an AI, directed by the author. It is stated in the README, and it
is worth knowing before you spend time on a patch: some people prefer to work on code written by
hand, and that is a reasonable preference.

If you do contribute, your work is yours and is credited as yours.

## Licence

By contributing you agree your work ships under **GPL-3.0-only**, like the rest of it.
