package app.bitcall.ui

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bitcall.data.Blocklist
import app.bitcall.data.CallEntry
import app.bitcall.data.ContactInfo
import app.bitcall.data.ContactsRepo
import app.bitcall.data.NotesStore
import app.bitcall.data.Phone
import app.bitcall.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything about one contact: numbers, details, history with them, and quick actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactDetailsScreen(contactId: Long, calls: List<CallEntry>, onBack: () -> Unit, onChanged: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf<ContactInfo?>(null) }
    var speedDialFor by remember { mutableStateOf<String?>(null) }
    val memos by NotesStore.memos.collectAsState()
    LaunchedEffect(contactId, refresh) { info = withContext(Dispatchers.IO) { ContactsRepo.info(context, contactId) } }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = p.text) }
            Spacer(Modifier.weight(1f))
            info?.let { c ->
                Text(
                    if (c.starred) "★" else "☆", style = px(22, if (c.starred) p.accent else p.dim),
                    modifier = Modifier.clickable {
                        scope.launch {
                            withContext(Dispatchers.IO) { runCatching { ContactsRepo.setStarred(context, c.id, !c.starred) } }
                            refresh++; onChanged()
                        }
                    }.padding(12.dp),
                )
            }
        }
        val c = info ?: return@Column
        val keys = c.phones.map { Phone.key(it.second) }.toSet()
        val history = remember(calls, keys) { calls.filter { Phone.key(it.number) in keys }.take(10) }
        val myMemos = remember(memos, keys) { memos.filter { Phone.key(it.number) in keys }.sortedByDescending { it.start } }
        val enemy = c.phones.any { Phone.isSpam(it.second) }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                PixelAvatar(c.name, c.photoUri, avatarSize(140.dp), enemy)
                Text(c.name, style = px(18, p.text, glow = p.accent), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                c.company?.let { Text(it, style = px(10, p.dim), modifier = Modifier.padding(top = 8.dp)) }
                if (enemy) EnemyBadge(Modifier.padding(top = 8.dp))
                Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    c.phones.firstOrNull()?.let { (_, n) ->
                        PixelButton(p.call, Modifier.size(width = 110.dp, height = 64.dp), onClick = { Phone.call(context, n) }) {
                            Text("📞 CALL", style = px(10, onColor(p.call)))
                        }
                        PixelButton(p.accent, Modifier.size(width = 110.dp, height = 64.dp), onClick = { sms(context, n) }) {
                            Text("💬 TEXT", style = px(10, onColor(p.accent)))
                        }
                    }
                }
            }
            item {
                SectionTitle("Phone", Modifier.fillMaxWidth())
                Card(Modifier.fillMaxWidth()) {
                    c.phones.forEachIndexed { i, (label, n) ->
                        Row(Modifier.fillMaxWidth().clickable { Phone.call(context, n) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Phone.pretty(n), style = px(13, p.text))
                                Text(label.uppercase(), style = px(8, p.dim), modifier = Modifier.padding(top = 6.dp))
                            }
                            Text("💬", style = px(18), modifier = Modifier.clickable { sms(context, n) }.padding(10.dp))
                            Text("⚡", style = px(18), modifier = Modifier.clickable { speedDialFor = n }.padding(10.dp))
                        }
                        if (i < c.phones.lastIndex) Spacer(Modifier.height(2.dp))
                    }
                }
            }
            if (c.emails.isNotEmpty() || c.addresses.isNotEmpty() || c.birthday != null || c.notes != null) item {
                SectionTitle("Details", Modifier.fillMaxWidth())
                Card(Modifier.fillMaxWidth()) {
                    c.emails.forEach { e -> DetailLine("✉", e) { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$e"))) } }
                    c.addresses.forEach { a -> DetailLine("📍", a) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a)))) } }
                    c.birthday?.let { DetailLine("🎂", it, null) }
                    c.notes?.let { DetailLine("📝", it, null) }
                }
            }
            if (history.isNotEmpty()) item {
                SectionTitle("Recent calls", Modifier.fillMaxWidth())
                Card(Modifier.fillMaxWidth()) {
                    history.forEach { e ->
                        val (icon, color) = when (e.type) {
                            CallLog.Calls.OUTGOING_TYPE -> "↗ OUTGOING" to p.good
                            CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> "↙ MISSED" to p.bad
                            else -> "↙ INCOMING" to p.accent
                        }
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(icon, style = px(9, color), modifier = Modifier.weight(1f))
                            Text(
                                DateUtils.getRelativeTimeSpanString(e.date).toString() +
                                    if (e.durationSec > 0) " · " + DateUtils.formatElapsedTime(e.durationSec) else "",
                                style = px(9, p.dim),
                            )
                        }
                    }
                }
            }
            if (myMemos.isNotEmpty()) item {
                SectionTitle("Call notes & recordings", Modifier.fillMaxWidth())
                myMemos.forEach { m ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Text(DateUtils.getRelativeTimeSpanString(m.start).toString(), style = px(8, p.dim))
                        if (m.notes.isNotBlank()) Text(m.notes, style = px(10, p.text), modifier = Modifier.padding(top = 8.dp))
                        m.recordingUri?.let { RecordingPlayer(it) }
                    }
                }
            }
            item {
                SectionTitle("Manage", Modifier.fillMaxWidth())
                FlowRow(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PixelChip("✎ EDIT", false) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_EDIT, c.lookupUri).putExtra("finishActivityOnSaveCompleted", true)) }
                    }
                    c.phones.firstOrNull()?.let { (_, n) ->
                        PixelChip(if (enemy) "✓ NOT SPAM" else "👾 SPAM", enemy) { toggleSpam(n) }
                    }
                    PixelChip("⊘ BLOCK", false) { c.phones.forEach { Blocklist.block(context, it.second) }; onChanged() }
                }
            }
        }
    }

    speedDialFor?.let { number ->
        AlertDialog(
            onDismissRequest = { speedDialFor = null },
            containerColor = p.surface,
            title = { Text("SPEED DIAL KEY", style = px(14, p.accent)) },
            text = {
                val s = LocalBitSettings.current
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    (2..9).forEach { d ->
                        PixelChip("$d" + if (s.speedDial[d] != null) "•" else "", s.speedDial[d]?.let(Phone::key) == Phone.key(number)) {
                            Settings.update { it.copy(speedDial = it.speedDial + (d to number)) }
                            speedDialFor = null
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { speedDialFor = null }) { Text("CANCEL", style = px(10, p.dim)) } },
        )
    }
}

@Composable
private fun DetailLine(icon: String, text: String, onClick: (() -> Unit)?) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, style = px(14), modifier = Modifier.padding(end = 12.dp))
        Text(text, style = px(10, p.text))
    }
}

private fun sms(context: android.content.Context, number: String) = Phone.text(context, number)
