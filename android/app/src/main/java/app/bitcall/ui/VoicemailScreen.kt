package app.bitcall.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.bitcall.data.Phone
import app.bitcall.data.VoicemailEntry
import app.bitcall.data.VoicemailRepo
import app.bitcall.ai.BitAi

@Composable
fun VoicemailScreen() {
    val p = LocalPalette.current
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<VoicemailEntry>?>(null) }
    LaunchedEffect(Unit) { items = VoicemailRepo.all(context) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        item {
            Row2 {
                PixelChip("📞 CALL VOICEMAIL", false) { Phone.call(context, "voicemail") }
            }
        }
        val list = items
        if (list != null && list.isEmpty()) item {
            Text(
                "No visual voicemail yet.\n\nIf your carrier supports visual voicemail, messages and their transcripts show up here. Otherwise long-press 1 on the keypad to dial in.",
                style = px(8, p.dim), modifier = Modifier.padding(16.dp),
            )
        }
        items(list.orEmpty(), key = { it.id }) { v ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(Phone.pretty(v.number) + if (!v.read) "  ●" else "", style = px(10, if (v.read) p.text else p.accent))
                Text(
                    DateUtils.getRelativeTimeSpanString(v.date).toString() + " · " + DateUtils.formatElapsedTime(v.durationSec),
                    style = px(7, p.dim), modifier = Modifier.padding(top = 6.dp),
                )
                // Voicemail brain: carrier transcript, else Whisper on the phone; then a one-line summary.
                var transcript by remember(v.id) { mutableStateOf(v.transcript?.takeIf { it.isNotBlank() } ?: VoicemailTranscripts.get(context, v.id)) }
                var summary by remember(v.id) { mutableStateOf<String?>(null) }
                LaunchedEffect(v.id) {
                    if (transcript == null) BitAi.transcribe(context, v.uri)?.let { transcript = it; VoicemailTranscripts.put(context, v.id, it) }
                    summary = BitAi.summarizeVoicemail(context, transcript)
                }
                summary?.let { Text("🧠 $it", style = px(9, p.accent), modifier = Modifier.padding(top = 10.dp)) }
                Text(
                    transcript ?: "(no transcript yet)",
                    style = px(8, p.text), modifier = Modifier.padding(top = 10.dp),
                )
                Row2 {
                    RecordingPlayer(v.uri.toString(), "▶ PLAY")
                    PixelChip("📞 CALL BACK", false) { Phone.call(context, v.number) }
                }
            }
        }
    }
}

@Composable
private fun Row2(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.padding(vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Bottom,
    ) { content() }
}

/** Whisper transcripts for voicemails the carrier didn't transcribe, cached so each runs once. */
private object VoicemailTranscripts {
    private fun prefs(c: android.content.Context) = c.getSharedPreferences("vm_transcripts", android.content.Context.MODE_PRIVATE)
    fun get(c: android.content.Context, id: Long): String? = prefs(c).getString(id.toString(), null)
    fun put(c: android.content.Context, id: Long, text: String) = prefs(c).edit().putString(id.toString(), text).apply()
}
