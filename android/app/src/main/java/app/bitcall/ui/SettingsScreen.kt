package app.bitcall.ui

import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import app.bitcall.data.BitSettings
import app.bitcall.data.Blocklist
import app.bitcall.data.Phone
import app.bitcall.data.Settings
import app.bitcall.telecom.RecorderService
import kotlinx.coroutines.launch

enum class Section(val title: String, val icon: String, val group: String) {
    AI("On-device AI (beta)", "🧠", "Bitcall AI"),
    SPAM("Caller ID & spam", "⚠", "Call Assist"),
    SCREEN("Call Screen", "🛡", "Call Assist"),
    HOLD("Hold for Me", "⏸", "Call Assist"),
    NOTES("Call Notes", "🗒", "Call Assist"),
    CUE("Magic Cue", "✨", "Call Assist"),
    RECORD("Call recording", "●", "Call Assist"),
    THEME("Theme & key colors", "🎨", "General"),
    SOUNDS("Sounds & ringtone", "♪", "General"),
    DND("Do Not Disturb schedule", "☾", "General"),
    BLOCKED("Blocked numbers", "⊘", "General"),
    REPLIES("Quick responses", "💬", "General"),
    SPEED("Speed dial", "⚡", "General"),
    ACCOUNTS("Calling accounts", "☏", "General"),
    VOICEMAIL("Voicemail", "◎", "General"),
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onChanged: () -> Unit, initial: Section? = null) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(initial) }
    BackHandler(open != null) { open = null }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = { if (open != null) open = null else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.text)
            }
            Text(open?.title?.uppercase() ?: "SETTINGS", style = px(12, p.text))
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            val sec = open
            if (sec != null) {
                item { Card(Modifier.fillMaxWidth()) { SectionBody(sec, onChanged) } }
            } else {
                Section.entries.groupBy { it.group }.forEach { (group, sections) ->
                    item { SectionTitle(group) }
                    item {
                        Column(Modifier.clip(RoundedCornerShape(14.dp)).background(p.surface)) {
                            sections.forEachIndexed { i, s ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { open = s }.padding(horizontal = 16.dp, vertical = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(s.icon, style = px(12, p.accent), modifier = Modifier.size(32.dp))
                                    Text(s.title, style = px(10, p.text))
                                }
                                if (i < sections.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).size(height = 2.dp, width = 0.dp).background(p.bg))
                            }
                        }
                    }
                }
                item {
                    val ctx = LocalContext.current
                    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
                    Text("bitcall $version · 128bit family", style = px(7, p.dim), modifier = Modifier.padding(vertical = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, sub: String?, on: Boolean, set: (BitSettings, Boolean) -> BitSettings) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = px(9, p.text))
            sub?.let { Text(it, style = px(7, p.dim), modifier = Modifier.padding(top = 6.dp)) }
        }
        Switch(on, { v -> Settings.update { set(it, v) } }, colors = SwitchDefaults.colors(checkedTrackColor = p.accent))
    }
}

/** Do Not Disturb access lets bitcall pause phone DND while a ★ favorite rings, so the call screen shows too. */
@Composable
private fun DndAccessRow() {
    val p = LocalPalette.current
    val context = LocalContext.current
    val nm = context.getSystemService(NotificationManager::class.java)
    var granted by remember { mutableStateOf(nm.isNotificationPolicyAccessGranted) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) granted = nm.isNotificationPolicyAccessGranted }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    if (granted) {
        Info("✓ Do Not Disturb access is on. During phone DND, favorites ring (on the alarm sound) and pop up the call screen. Your DND stays on.")
    } else {
        Info("Favorites already ring during phone DND, on the alarm sound. Give bitcall Do Not Disturb access so the call screen pops up too. Your DND stays on.")
        Row(Modifier.padding(bottom = 8.dp)) {
            PixelChip("☾ ALLOW DND ACCESS", false) {
                context.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        }
    }
}

@Composable
private fun Info(text: String) {
    val p = LocalPalette.current
    Text(text, style = px(7, p.dim), modifier = Modifier.padding(vertical = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionBody(section: Section, onChanged: () -> Unit) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val context = LocalContext.current
    when (section) {
        Section.SPAM -> {
            Info("Unknown callers that you flag as spam, or whose number fails carrier verification (spoofed), get the 👾 ENEMY badge.")
            Toggle("Block flagged spam", "Reject calls from numbers you marked 👾 before they ring", s.rejectSpam) { b, v -> b.copy(rejectSpam = v) }
            Text("FLAGGED NUMBERS (${s.spamNumbers.size})", style = px(8, p.accent), modifier = Modifier.padding(top = 12.dp))
            s.spamNumbers.forEach { k ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("👾 " + Phone.pretty(k), style = px(8, p.text), modifier = Modifier.weight(1f))
                    Text("✕", style = px(10, p.bad), modifier = Modifier.clickable { toggleSpam(k) }.padding(8.dp))
                }
            }
        }
        Section.SCREEN -> {
            Info("Uses Android's official call-screening service. It runs before your phone rings.")
            Toggle("Silence unknown callers", "Calls from numbers not in your contacts go straight to your call log & voicemail", s.silenceUnknown) { b, v -> b.copy(silenceUnknown = v) }
            Toggle("Block flagged spam", null, s.rejectSpam) { b, v -> b.copy(rejectSpam = v) }
            Info("A full AI answering assistant (Google-style Call Screen that talks to the caller) needs calls routed through a calling service such as Twilio. That's planned for a later version.")
        }
        Section.HOLD -> {
            Toggle("Hold for Me", "Shows a HOLD FOR ME button during calls", s.holdForMe) { b, v -> b.copy(holdForMe = v) }
            Info("EXPERIMENTAL. Puts the call on speaker, mutes you, and listens through the mic for a person (\"hello\", \"thanks for holding\"…). When someone picks up you get a 1-UP jingle + vibration. Android doesn't let apps hear call audio directly, so this is hit or miss: keep the phone near you and the volume up.")
        }
        Section.NOTES -> {
            Toggle("Live transcript", "Transcribe calls automatically (puts calls on speaker)", s.liveTranscript) { b, v -> b.copy(liveTranscript = v) }
            Info("Type notes during any call from the NOTES button. Notes and transcripts are saved with the call in Recents (🗒). Transcripts use the speaker + mic workaround, so they're best effort.")
        }
        Section.CUE -> {
            Toggle("Magic Cue", "Show helpful info during calls", s.magicCue) { b, v -> b.copy(magicCue = v) }
            Info("During a call, Magic Cue shows what your phone knows about the caller: email, company, address, birthday, your last calls, your notes from previous calls, and upcoming calendar events with them. Everything stays on the phone.")
        }
        Section.RECORD -> {
            Toggle("Record every call", "Starts recording as soon as a call connects", s.autoRecord) { b, v -> b.copy(autoRecord = v) }
            Toggle("Use speaker while recording", "Needed to capture the other person's voice", s.recordOnSpeaker) { b, v -> b.copy(recordOnSpeaker = v) }
            Info("Recordings save to the Recordings/Bitcall folder on your phone and play back from Recents.\n\nEvery recording starts by announcing: \"${RecorderService.NOTICE}\" Canada requires the consent of at least one person on the call, and the announcement lets everyone know. This can't be turned off.\n\nAndroid blocks apps from the call audio itself, so Bitcall records the microphone with the call on speaker. Studio-quality recording of both sides needs the Twilio route (planned) or a rooted phone.")
        }
        Section.THEME -> ThemeEditor()
        Section.AI -> AiSettings()
        Section.SOUNDS -> {
            Toggle("8-bit keypad sounds", "Every key plays its own chiptune note", s.keySounds) { b, v -> b.copy(keySounds = v) }
            Toggle("Chiptune ringtone", "Off = your normal system ringtone", s.chipRingtone) { b, v -> b.copy(chipRingtone = v) }
            Row(Modifier.padding(top = 8.dp)) { PixelChip("▶ PREVIEW", false) { app.bitcall.assist.ChipSound.powerUp() } }
        }
        Section.DND -> {
            Toggle("Quiet hours", "Silence calls during the schedule below", s.dndEnabled) { b, v -> b.copy(dndEnabled = v) }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelChip("FROM ${hhmm(s.dndStart)}", false) {
                    TimePickerDialog(context, { _, h, m -> Settings.update { it.copy(dndStart = h * 60 + m) } }, s.dndStart / 60, s.dndStart % 60, false).show()
                }
                PixelChip("TO ${hhmm(s.dndEnd)}", false) {
                    TimePickerDialog(context, { _, h, m -> Settings.update { it.copy(dndEnd = h * 60 + m) } }, s.dndEnd / 60, s.dndEnd % 60, false).show()
                }
            }
            Toggle("Let ★ favorites through", "Rings through your phone's own Do Not Disturb, on the alarm sound, even if the ringer is on vibrate", s.dndAllowFavorites) { b, v -> b.copy(dndAllowFavorites = v) }
            if (s.dndAllowFavorites) DndAccessRow()
            Info("Silenced calls still show in Recents. Everyone except ★ favorites follows your phone's own Do Not Disturb.")
            RingThroughCheck()
        }
        Section.BLOCKED -> {
            var refresh by remember { mutableIntStateOf(0) }
            val blocked = remember(refresh) { Blocklist.all(context) }
            var add by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(add, { add = it }, Modifier.weight(1f), singleLine = true, textStyle = px(10, p.text), placeholder = { Text("NUMBER", style = px(8, p.dim)) })
                PixelChip("⊘ BLOCK", false) { if (add.isNotBlank() && Blocklist.block(context, add.trim())) { add = ""; refresh++; onChanged() } }
            }
            if (blocked.isEmpty()) Info("No blocked numbers. Blocked callers never ring and go straight to the log.")
            blocked.forEach { (_, n) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⊘ " + Phone.pretty(n), style = px(8, p.text), modifier = Modifier.weight(1f))
                    Text("UNBLOCK", style = px(7, p.accent), modifier = Modifier.clickable { Blocklist.unblock(context, n); refresh++ }.padding(8.dp))
                }
            }
        }
        Section.REPLIES -> {
            Info("Shown when you decline a call with REPLY. The text goes out by SMS from your number.")
            s.quickReplies.forEachIndexed { i, r ->
                OutlinedTextField(
                    r, { v -> Settings.update { it.copy(quickReplies = it.quickReplies.toMutableList().also { l -> l[i] = v }) } },
                    Modifier.fillMaxWidth().padding(vertical = 4.dp), textStyle = px(9, p.text),
                )
            }
        }
        Section.SPEED -> {
            Info("Long-press 2–9 on the keypad to call. Long-press an empty key to set it. Long-press 1 for voicemail.")
            (2..9).forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("$d  ", style = px(12, p.key(d - 1, s)))
                    Text(s.speedDial[d]?.let(Phone::pretty) ?: "—", style = px(9, p.text), modifier = Modifier.weight(1f))
                    if (s.speedDial[d] != null) Text("✕", style = px(10, p.bad), modifier = Modifier.clickable { Settings.update { it.copy(speedDial = it.speedDial - d) } }.padding(8.dp))
                }
            }
        }
        Section.ACCOUNTS -> {
            Info("SIMs and calling accounts are managed by Android.")
            Row { PixelChip("OPEN CALLING ACCOUNTS", false) { runCatching { context.startActivity(Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS)) } } }
        }
        Section.VOICEMAIL -> {
            Info("Carrier voicemail settings (number, greeting, visual voicemail).")
            Row { PixelChip("OPEN VOICEMAIL SETTINGS", false) { runCatching { context.startActivity(Intent(TelephonyManager.ACTION_CONFIGURE_VOICEMAIL)) } } }
        }
    }
}

private fun hhmm(min: Int) = "%d:%02d %s".format(((min / 60) + 11) % 12 + 1, min % 60, if (min < 720) "AM" else "PM")

/** Palette picker + per-key colour editor: tap a key to cycle it through the swatches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeEditor() {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    var selectedKey by remember { mutableStateOf<Int?>(null) }

    Text("PALETTE", style = px(8, p.accent))
    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PALETTES.forEach { pal -> PixelChip(pal.label, pal.id == s.palette) { Settings.update { it.copy(palette = pal.id) } } }
    }
    Toggle("CRT glow", "Scanlines and phosphor glow (dark palettes)", s.crt) { b, v -> b.copy(crt = v) }

    Text("KEY COLORS — tap a key, then a color", style = px(8, p.accent), modifier = Modifier.padding(top = 12.dp))
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KEYS.chunked(3).forEachIndexed { row, keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEachIndexed { col, (d, _) ->
                    val i = row * 3 + col
                    val c = p.key(i, s)
                    Box(
                        Modifier.weight(1f).aspectRatio(2f).clip(RoundedCornerShape(6.dp)).background(c)
                            .border(3.dp, if (selectedKey == i) p.text else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { selectedKey = i },
                        contentAlignment = Alignment.Center,
                    ) { Text(d, style = px(12, onColor(c))) }
                }
            }
        }
    }
    selectedKey?.let { i ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SWATCHES.forEach { sw ->
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)).background(Color(sw)).border(2.dp, p.surface2, RoundedCornerShape(4.dp))
                        .clickable { Settings.update { it.copy(keyColors = it.keyColors + (i to sw)) } },
                )
            }
        }
    }
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelChip("🎲 SHUFFLE", false) {
            val shuffled = (p.keys + SWATCHES.take(8).map { Color(it) }).shuffled().take(12)
            Settings.update { it.copy(keyColors = shuffled.mapIndexed { idx, col -> idx to colorLong(col) }.toMap()) }
        }
        PixelChip("↺ RESET", false) { Settings.update { it.copy(keyColors = emptyMap()) } }
    }
}

private fun colorLong(c: Color): Long =
    (((c.alpha * 255).toLong() shl 24) or ((c.red * 255).toLong() shl 16) or ((c.green * 255).toLong() shl 8) or (c.blue * 255).toLong())

/** Opt-in on-device AI: pick a mode, download models, everything stays on the phone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiSettings() {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); tick++ } }

    Info("Runs Qwen (via llama.cpp) and Whisper right on your phone. Nothing about your calls leaves the device. Off by default. Turn it on whenever you like.")
    Text("MODE", style = px(8, p.accent), modifier = Modifier.padding(top = 4.dp))
    FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("OFF" to "OFF", "FAST" to "FAST ONLY", "FULL" to "FULL").forEach { (id, label) ->
            PixelChip(label, s.aiMode == id) { Settings.update { it.copy(aiMode = id, aiAsked = true) } }
        }
    }
    Info(when (s.aiMode) {
        "OFF" -> "AI is off. Smart features use simple built-in rules instead."
        "FAST" -> "Only the small model: smart decline texts and spam sorting. Light on battery."
        else -> "Small model during calls; the main model after calls for voicemail summaries, tidy notes and call-log questions."
    })
    if (!app.bitcall.ai.BitAi.nativeReady) {
        Text("⚠ AI engine missing from this build.", style = px(7, p.bad), modifier = Modifier.padding(vertical = 8.dp))
    }
    if (s.aiMode == "OFF") return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var test by remember { mutableStateOf<String?>(null) }
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        PixelChip("▶ TEST AI", false) {
            test = "Thinking… (first run loads the model)"
            scope.launch { test = app.bitcall.ai.BitAi.selfTest(context) }
        }
    }
    test?.let { Text(it, style = px(7, p.good), modifier = Modifier.padding(bottom = 6.dp)) }
    app.bitcall.ai.ModelRole.entries.forEach { role ->
        if (role == app.bitcall.ai.ModelRole.MAIN && s.aiMode != "FULL") return@forEach
        val chosen = when (role) {
            app.bitcall.ai.ModelRole.MAIN -> s.aiMainModel
            app.bitcall.ai.ModelRole.FAST -> s.aiFastModel
            app.bitcall.ai.ModelRole.SPEECH -> s.aiSpeechModel
        }
        Text(when (role) {
            app.bitcall.ai.ModelRole.MAIN -> "MAIN MODEL (after calls)"
            app.bitcall.ai.ModelRole.FAST -> "FAST HELPER (during calls)"
            app.bitcall.ai.ModelRole.SPEECH -> "SPEECH (voicemail → text)"
        }, style = px(8, p.accent), modifier = Modifier.padding(top = 14.dp))
        app.bitcall.ai.ModelCatalog.all.filter { it.role == role }.forEach { m ->
            val status = remember(tick, m.id) { app.bitcall.ai.ModelManager.status(context, m) }
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                Settings.update {
                    when (role) {
                        app.bitcall.ai.ModelRole.MAIN -> it.copy(aiMainModel = m.id)
                        app.bitcall.ai.ModelRole.FAST -> it.copy(aiFastModel = m.id)
                        app.bitcall.ai.ModelRole.SPEECH -> it.copy(aiSpeechModel = m.id)
                    }
                }
            }) {
                Text((if (m.id == chosen) "● " else "○ ") + m.label, style = px(8, if (m.id == chosen) p.accent else p.text))
                Text("%.1f GB · %s".format(m.bytes / 1e9, m.note), style = px(6, p.dim), modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (status) {
                        app.bitcall.ai.ModelStatus.Missing -> PixelChip("⬇ DOWNLOAD (WI-FI)", false) { app.bitcall.ai.ModelManager.download(context, m); tick++ }
                        is app.bitcall.ai.ModelStatus.Downloading -> Text("⬇ %d%%".format((status.fraction * 100).toInt()), style = px(8, p.good))
                        app.bitcall.ai.ModelStatus.Ready -> {
                            Text("✓ READY", style = px(8, p.good))
                            Text("DELETE", style = px(7, p.bad), modifier = Modifier.clickable { app.bitcall.ai.ModelManager.delete(context, m); tick++ })
                        }
                    }
                }
            }
        }
    }
}
