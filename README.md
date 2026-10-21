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

**Build locally** (needs the Android SDK with NDK 27.3 + CMake 3.31.6 for the AI engine):

```sh
git submodule update --init --recursive        # llama.cpp + whisper.cpp
cd android
./gradlew testDebugUnitTest assembleRelease   # APK → app/build/outputs/apk/release/
```

Release signing reads `BITCALL_KEYSTORE_PATH`, `BITCALL_KEYSTORE_PASSWORD`, `BITCALL_KEY_ALIAS`, `BITCALL_KEY_PASSWORD`
(CI gets them from repo secrets). Without them the build is debug-signed.

**On-device AI engine** lives in `android/app/src/main/cpp` (`bitcall_ai.cpp` core + `bitcall_jni.cpp` glue).
Desktop smoke test with real models:

```sh
cmake -S android/app/src/main/cpp -B /tmp/aibuild -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build /tmp/aibuild --target bitcall_ai_test
/tmp/aibuild/bitcall_ai_test Qwen3.5-0.8B-Q4_K_M.gguf ggml-base-q5_1.bin android/app/src/main/cpp/whisper.cpp/samples/jfk.wav
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
- [x] llama.cpp / whisper.cpp native engines (0.2)
- [ ] Release signing key added to repo secrets (key generated; see GITHUB_SECRETS.txt from the 0.2 hand-off)
