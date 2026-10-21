# Bitcall roadmap

## ✅ In the 0.1 pre-release
- Pixel-style dialer (Press Start 2P, chunky keys), every key its own colour, 6 palettes, per-key colour editor, CRT glow
- Default phone app: places, answers and manages calls (InCallService), full-screen incoming call, lock-screen support
- ★ Favorites drop-down (starred contacts = Google Contacts favorites); new contacts save to your Google account
- Recents with ALL / MISSED / 👾 ENEMIES filters, call-back, text, block, mark spam, delete
- T9 smart search, speed dial on long-press 2–9, long-press 1 = voicemail
- 8-bit keypad sounds + chiptune ringtone (or system ringtone)
- Pixel-sprite contact avatars (photos crushed to 16×16; invaders for everyone else)
- XP / levels / streaks / badges from your call log
- Call Assist: spam screening (Android CallScreeningService), silence unknown callers, Do Not Disturb schedule,
  blocked numbers, quick-reply decline texts, Hold for Me (experimental), call notes + live transcript (experimental),
  Magic Cue (contact details, past calls, past notes, upcoming calendar events)
- Call recording to Recordings/Bitcall, always announced ("This call is being recorded")
- Visual voicemail list (carrier VVM) with transcripts + one-line summaries
- Local AI opt-in (off by default): model catalog + downloads (Qwen3.5 / Qwen3 GGUF, Whisper), mode picker,
  "ask your call log" search, voicemail summaries, smart decline, tidy notes → calendar event, spam score, relationship nudges.
  Every feature has a rule-based fallback.

## 🔜 Next
1. **llama.cpp engine** — add `android/app/src/main/cpp` with llama.cpp as a submodule + a JNI shim implementing
   `LlamaBridge.nativeLoad / nativeGenerate / nativeFree` (see `ai/Engines.kt`). Needs the NDK + CMake in CI.
2. **whisper.cpp engine** — same pattern for `WhisperBridge.nativeTranscribe`; decode voicemail audio to 16 kHz PCM
   with MediaExtractor/MediaCodec, then summarise with the main model ("voicemail brain").
3. **Release signing key** in repo secrets so pre-releases upgrade cleanly.
4. **Home-screen favorites widget.**
5. **Pixel mascot** that reacts to calls (sleeping with 0 missed calls, panicking at 5+), level-up animation,
   more achievements (Call Streak, Inbox Zero Voicemail).
6. **Contact themes** — per-favorite colour + ringtone sprite.
7. **Voice commands** — pixel-mascot assistant to dial / text / look people up hands-free.
8. **Twilio route** (optional) — real AI Call Screen that talks to callers, studio-quality two-sided recording,
   reliable Hold for Me.
9. Community spam lists for the spam sniffer.

## Platform limits worth knowing
- Android doesn't let third-party apps read call audio. Recording, Hold for Me and live transcripts use the
  speaker + microphone, so quality depends on the phone and room. A Twilio route or a rooted phone gets the real audio.
- Google's own Call Screen / Hold for Me / Call Notes / Magic Cue run on Google's private Pixel services and can't be
  reused; Bitcall rebuilds them its own way.
- Canada needs at least one party's consent to record; Bitcall always announces recordings.
