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

## ✅ In 0.2
- **Working on-device AI engine**: llama.cpp + whisper.cpp compiled into the APK (`android/app/src/main/cpp`),
  one shared ggml, 7 CPU variants picked at runtime for the phone's Arm generation
- Qwen runs through the model's own chat template with thinking turned off for quick answers
- Whisper transcribes voicemails without a carrier transcript, and call recordings (🧠 TRANSCRIBE in Recents)
- Settings → On-device AI → ▶ TEST AI checks the engine + model on the phone and shows the speed
- Release signing key; CI publishes signed pre-releases that install over each other

## 🔜 Next
1. Stream tokens to the screen as they're generated; GPU (Vulkan/OpenCL) backend for faster 4B answers.
2. **Home-screen favorites widget.**
3. **Pixel mascot** that reacts to calls (sleeping with 0 missed calls, panicking at 5+), level-up animation,
   more achievements (Call Streak, Inbox Zero Voicemail).
4. **Contact themes** — per-favorite colour + ringtone sprite.
5. **Voice commands** — pixel-mascot assistant to dial / text / look people up hands-free.
6. **Twilio route** (optional) — real AI Call Screen that talks to callers, studio-quality two-sided recording,
   reliable Hold for Me.
7. Community spam lists for the spam sniffer.

## Platform limits worth knowing
- Android doesn't let third-party apps read call audio. Recording, Hold for Me and live transcripts use the
  speaker + microphone, so quality depends on the phone and room. A Twilio route or a rooted phone gets the real audio.
- Google's own Call Screen / Hold for Me / Call Notes / Magic Cue run on Google's private Pixel services and can't be
  reused; Bitcall rebuilds them its own way.
- Canada needs at least one party's consent to record; Bitcall always announces recordings.
