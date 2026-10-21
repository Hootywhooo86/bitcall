# Bitcall

A pixel-style phone app in the **128bit family** — the phone app, but chunkier.

Landing page is live via GitHub Pages: https://hootywhooo86.github.io/bitcall/

## What this is

Bitcall is Daniel's 128bit-family take on the phone app: calls, contacts, and dialing with a chunky pixel aesthetic. Same family DNA as the other 128bit apps — one 128bit account, shared event feed, 128bit Pro subscription.

## Product brief (starter — Daniel's call on the concept)

**Core idea:** a pixel-art phone/dialer experience. What exactly it does beyond looking great is Daniel's product call. Directions on the table:

- **Pixel dialer + contacts** — a full retro phone UI: dial pad, recents, favorites, contacts with pixel avatars.
- **Smart call features** — spam/call-screening with pixel flair, call notes, pixel voicemail visualizer.
- **Social calling** — pixel rooms / voice lobbies, call streaks with friends.

**Open product questions for Daniel:**
1. What does Bitcall do besides dial — is it a full dialer replacement, a companion app, or something weirder?
2. iOS / Android / both? (Native dialer replacement is Android-only; iOS would be a companion.)
3. Does it need real telephony (Twilio/similar) or is it a UI + contacts + notes layer over the system dialer?

## The Android app (`android/`)

Bitcall is a full **default phone app for Android 11+** (Kotlin + Jetpack Compose). See [ROADMAP.md](ROADMAP.md) for
the feature list and what's next.

**Install a pre-release:** grab the latest APK from the repo's GitHub Releases (marked *Pre-release*), install it,
open Bitcall and accept **Set as default phone app**.

**Build locally:**

```sh
cd android
./gradlew testDebugUnitTest assembleRelease   # APK → app/build/outputs/apk/release/
```

Code map: `data/` (contacts, call log, settings, notes, voicemail) · `telecom/` (InCallService, screening, ringer,
recorder, notifications) · `assist/` (Hold for Me, Magic Cue, transcriber, 8-bit synth) · `ai/` (opt-in on-device AI)
· `ui/` (Compose screens).

## Architecture (128bit family standard)

- **One 128bit account** across all apps from day one.
- **Shared event schema** — Bitcall emits events like `call.made`, `call.missed`, `contact.added` into the family feed.
- **Family event feed** — `supabase/family-feed.sql` in this repo (same block as 128bitPlay's setup SQL — keep them in step). Run once in the Supabase SQL Editor.
- **Monetization:** 128bit Pro bundle subscription across all apps (default assumption).

## For Carlos

- Landing page: `index.html` (pixel dialer mock — the keys actually type; tap the number to clear). Needs a Formspree ID in the waitlist form before launch.
- This README is the starter brief — build the real thing per Daniel's product call above.
- Rocky codes scaffolding and straightforward builds; Carlos takes the harder features and double-checks Rocky's work.

## Status

- [x] Repo created, landing page live
- [ ] Formspree ID in `index.html`
- [x] Product direction locked: Android default dialer, Pixel-style, Call Assist, opt-in local AI
- [x] App scaffold + 0.1 pre-release (Android 11+)
- [ ] llama.cpp / whisper.cpp native engines
- [ ] Release signing key in repo secrets
