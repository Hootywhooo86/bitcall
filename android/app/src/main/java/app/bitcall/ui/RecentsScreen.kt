package app.bitcall.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.CallLog
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bitcall.data.Blocklist
import app.bitcall.data.CallEntry
import app.bitcall.data.CallLogRepo
import app.bitcall.data.Contact
import app.bitcall.data.NotesStore
import app.bitcall.data.Phone
import app.bitcall.data.Settings
import app.bitcall.ai.BitAi
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

enum class RecentFilter(val label: String) { ALL("ALL"), MISSED("MISSED"), ENEMIES("👾 ENEMIES") }

@Composable
fun RecentsScreen(calls: List<CallEntry>, contacts: List<Contact>, onChanged: () -> Unit, onOpen: (Long) -> Unit) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    var filter by remember { mutableStateOf(RecentFilter.ALL) }
    var expanded by remember { mutableStateOf<Long?>(null) }
    var addNumber by remember { mutableStateOf<String?>(null) }
    val memos by NotesStore.memos.collectAsState()
    val stats = remember(calls) { CallLogRepo.stats(calls) }
    val photos = remember(contacts) { contacts.flatMap { c -> c.numbers.map { Phone.key(it) to c.photoUri } }.toMap() }
    val contactIds = remember(contacts) { contacts.flatMap { c -> c.numbers.map { Phone.key(it) to c.id } }.toMap() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var asked by remember { mutableStateOf<BitAi.CallQuery?>(null) }
    val known = remember(contacts) { contacts.flatMap { c -> c.numbers.map(Phone::key) }.toSet() }
    val scores = remember(calls, known, s.spamNumbers) {
        calls.map { it.number }.distinct().associateWith { BitAi.spamScore(it, Phone.key(it) in known, calls) }
    }
    val nudges = remember(contacts, calls) { BitAi.nudges(contacts, calls) }
    val shown = remember(calls, filter, s.spamNumbers, asked, scores) {
        val base = asked?.let { BitAi.apply(it, calls) } ?: calls
        when (filter) {
            RecentFilter.ALL -> base
            RecentFilter.MISSED -> base.filter { it.missed }
            RecentFilter.ENEMIES -> base.filter { Phone.isSpam(it.number) || (scores[it.number] ?: 0) >= 70 }
        }
    }
    fun ask() {
        if (question.isBlank()) { asked = null; return }
        scope.launch {
            val q = BitAi.parseQuestion(context, question, contacts)
            if (q.call && q.name != null) {
                val c = contacts.firstOrNull { it.name.contains(q.name, true) }
                if (c != null) { Phone.call(context, c.primary); question = ""; return@launch }
            }
            asked = q
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item { XpCard(stats) }
        nudges.forEach { n -> item { Text("💌 $n", style = px(8, p.accent), modifier = Modifier.padding(top = 10.dp)) } }
        item {
            OutlinedTextField(
                question, { question = it; if (it.isBlank()) asked = null },
                Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true, textStyle = px(9, p.text),
                placeholder = { Text("ASK: who called me last week?", style = px(8, p.dim)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { ask() }),
            )
            if (asked != null) Text("✕ CLEAR SEARCH (${shown.size} calls)", style = px(7, p.accent),
                modifier = Modifier.clickable { asked = null; question = "" }.padding(vertical = 8.dp))
        }
        item {
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecentFilter.entries.forEach { f -> PixelChip(f.label, f == filter) { filter = f } }
            }
        }
        if (shown.isEmpty()) item {
            Text(if (filter == RecentFilter.MISSED) "No missed calls. Nice." else "Nothing here yet.", style = px(9, p.dim), modifier = Modifier.padding(16.dp))
        }
        items(shown, key = { it.id }) { e ->
            val memo = remember(memos, e) { NotesStore.match(e.number, e.date) }
            RecentRow(e, photos[Phone.key(e.number)], memo != null, scores[e.number] ?: 0, expanded == e.id) { expanded = if (expanded == e.id) null else e.id }
            if (expanded == e.id) RecentDetails(e, memo, contactIds[Phone.key(e.number)], onOpen, onAdd = { addNumber = e.number }, onChanged = onChanged)
        }
    }
    addNumber?.let { n -> AddContactDialog(n, onDismiss = { addNumber = null }, onSaved = { addNumber = null; onChanged() }) }
}

@Composable
private fun RecentRow(e: CallEntry, photo: String?, hasMemo: Boolean, score: Int, open: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val enemy = Phone.isSpam(e.number) || score >= 70
    val (icon, color) = when (e.type) {
        CallLog.Calls.OUTGOING_TYPE -> "↗" to p.good
        CallLog.Calls.MISSED_TYPE -> "↙✕" to p.bad
        CallLog.Calls.REJECTED_TYPE, CallLog.Calls.BLOCKED_TYPE -> "⊘" to p.bad
        CallLog.Calls.VOICEMAIL_TYPE -> "◎" to p.accent
        else -> "↙" to p.accent
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (open) p.surface else Color.Transparent)
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PixelAvatar(e.name ?: e.number, photo, 40.dp, enemy)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.name ?: Phone.pretty(e.number), style = px(10, if (e.missed) p.bad else p.text),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                if (enemy) EnemyBadge(Modifier.padding(start = 6.dp))
            }
            if (score >= 70) Text("BOSS LEVEL · SPAM SCORE $score", style = px(6, p.bad), modifier = Modifier.padding(top = 4.dp))
            Text(
                "$icon  " + DateUtils.getRelativeTimeSpanString(e.date) + (if (e.durationSec > 0) " · ${DateUtils.formatElapsedTime(e.durationSec)}" else "") + if (hasMemo) "  🗒" else "",
                style = px(7, color), modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentDetails(e: CallEntry, memo: app.bitcall.data.CallMemo?, contactId: Long?, onOpen: (Long) -> Unit, onAdd: () -> Unit, onChanged: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val enemy = Phone.isSpam(e.number)
    Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PixelChip("📞 CALL", false) { Phone.call(context, e.number) }
            if (contactId != null) PixelChip("👤 DETAILS", false) { onOpen(contactId) }
            PixelChip("💬 TEXT", false) {
                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", e.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            if (contactId == null) PixelChip("+ CONTACT", false, onAdd)
            PixelChip(if (enemy) "✓ NOT SPAM" else "👾 SPAM", enemy) { toggleSpam(e.number) }
            PixelChip("⊘ BLOCK", false) { Blocklist.block(context, e.number); onChanged() }
            PixelChip("🗑", false) { CallLogRepo.delete(context, e.id); onChanged() }
        }
        if (memo != null) {
            memo.recordingUri?.let { uri ->
                RecordingPlayer(uri)
                if (memo.transcript.isBlank() && LocalBitSettings.current.aiMode != "OFF") TranscribeRecording(memo, uri)
            }
            if (memo.notes.isNotBlank()) {
                Text("🗒 " + memo.notes, style = px(8, p.text), modifier = Modifier.padding(top = 10.dp))
                NoteActions(memo, e.name ?: Phone.pretty(e.number))
            }
            if (memo.transcript.isNotBlank()) Text("TRANSCRIPT\n" + memo.transcript, style = px(7, p.dim), modifier = Modifier.padding(top = 10.dp))
        }
    }
}

fun toggleSpam(number: String) {
    val k = Phone.key(number)
    if (k.isEmpty()) return
    Settings.update { s -> s.copy(spamNumbers = if (k in s.spamNumbers) s.spamNumbers - k else s.spamNumbers + k) }
}

@Composable
fun RecordingPlayer(uri: String, label: String = "▶ PLAY RECORDING") {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(uri) { onDispose { player?.release(); player = null } }
    Row(Modifier.padding(top = 10.dp)) {
        PixelChip(if (player != null) "■ STOP" else label, player != null) {
            val cur = player
            if (cur != null) { cur.release(); player = null }
            else player = play(context, Uri.parse(uri)) { player = null }
        }
    }
}

private fun play(context: Context, uri: Uri, onDone: () -> Unit): MediaPlayer? = runCatching {
    MediaPlayer().apply {
        setDataSource(context, uri)
        setOnCompletionListener { it.release(); onDone() }
        prepare()
        start()
    }
}.getOrNull()

/** Fun stats: XP for calling, a level bar, streaks and unlockable badges. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XpCard(stats: app.bitcall.data.CallStats) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(top = 4.dp).clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LV ${stats.level}", style = px(14, p.accent, glow = p.accent))
            Text("  ${stats.xp} XP", style = px(9, p.text), modifier = Modifier.weight(1f))
            if (stats.streakDays > 0) Text("🔥${stats.streakDays}", style = px(10, p.text))
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(12.dp).clip(RoundedCornerShape(3.dp)).background(p.surface2)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(stats.levelProgress.coerceIn(0.02f, 1f)).background(p.good))
        }
        if (open) {
            Text(
                "CALLS MADE  ${stats.made}\nANSWERED    ${stats.answered}\nMISSED      ${stats.missed}\nTALK TIME   ${stats.talkMinutes} MIN",
                style = px(8, p.dim), modifier = Modifier.padding(top = 12.dp),
            )
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.badges.forEach { Text(it, style = px(7, p.text), modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(4.dp)).background(p.surface2).padding(6.dp)) }
            }
        }
    }
}

/** After-call notes: tidy with AI (or a simple bullet pass), then turn into a calendar event. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteActions(memo: app.bitcall.data.CallMemo, who: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PixelChip("✨ TIDY", false) {
            scope.launch { NotesStore.upsert(memo.copy(notes = BitAi.tidyNote(context, memo.notes))) }
        }
        PixelChip("📅 EVENT", false) {
            val title = memo.notes.lines().first { it.isNotBlank() }.removePrefix("• ").take(60)
            context.startActivity(
                Intent(Intent.ACTION_INSERT, android.provider.CalendarContract.Events.CONTENT_URI)
                    .putExtra(android.provider.CalendarContract.Events.TITLE, title)
                    .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "From call with $who:\n${memo.notes}")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/** Whisper the call recording into a transcript saved with the call (then ✨ TIDY can turn it into notes). */
@Composable
private fun TranscribeRecording(memo: app.bitcall.data.CallMemo, uri: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Row(Modifier.padding(top = 8.dp)) {
        PixelChip(if (busy) "🧠 LISTENING…" else "🧠 TRANSCRIBE", busy) {
            if (busy) return@PixelChip
            busy = true
            scope.launch {
                val text = BitAi.transcribe(context, Uri.parse(uri))
                if (text != null) NotesStore.upsert(memo.copy(transcript = text, notes = memo.notes.ifBlank { text }))
                busy = false
            }
        }
    }
}
