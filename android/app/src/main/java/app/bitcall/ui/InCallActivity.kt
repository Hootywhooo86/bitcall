package app.bitcall.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.Call
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bitcall.assist.MagicCue
import app.bitcall.data.Phone
import app.bitcall.telecom.CallManager
import app.bitcall.telecom.CallUi
import app.bitcall.telecom.HoldState
import app.bitcall.telecom.RecorderService
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {
    companion object { const val EXTRA_ANSWER = "answer" }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_ANSWER, false)) CallManager.answer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_ANSWER, false)) CallManager.answer()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        setContent {
            BitTheme {
                val calls by CallManager.calls.collectAsState()
                val primary = calls.firstOrNull { it.state != Call.STATE_DISCONNECTED }
                // Keep the last call on screen for a moment so "CALL ENDED · 4:12" is visible.
                var last by remember { mutableStateOf<CallUi?>(null) }
                if (primary != null) last = primary
                LaunchedEffect(primary == null) { if (primary == null) { delay(1500); finish() } }
                val shown = primary ?: last?.copy(state = Call.STATE_DISCONNECTED)
                if (shown != null) InCallScreen(shown)
            }
        }
    }
}

private enum class Panel { NONE, KEYPAD, NOTES, CUE, REPLY }

@Composable
private fun InCallScreen(ui: CallUi) {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    val context = LocalContext.current
    val audio by CallManager.audio.collectAsState()
    val recording by CallManager.recording.collectAsState()
    val hold by CallManager.hold.collectAsState()
    var panel by remember { mutableStateOf(Panel.NONE) }
    val ringing = ui.state == Call.STATE_RINGING
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) RecorderService.start(context)
    }

    // Live transcript fills the middle automatically whenever it has text.
    val session by CallManager.session.collectAsState()
    val transcript by (session?.transcript ?: CallManager.partial).collectAsState()
    val partial by CallManager.partial.collectAsState()
    val showTranscript = panel == Panel.NONE && (transcript.isNotBlank() || partial.isNotBlank())
    // With nothing to show in the middle, the caller block gets the space instead of leaving a gap.
    val hasContent = panel != Panel.NONE || showTranscript

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val dialing = ui.state in setOf(Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW, Call.STATE_SELECT_PHONE_ACCOUNT)
        // Header changes per screen: incoming blinks, outgoing animates dots.
        Text(
            when {
                ringing -> "◆ INCOMING CALL ◆"
                dialing -> "▶ OUTGOING CALL"
                else -> ""
            },
            style = px(10, if (ui.enemy && ringing) p.bad else p.dim),
            modifier = Modifier.padding(top = 16.dp).alpha(if (ringing) blink() else 1f),
        )
        if (!hasContent) Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))
        RingingAvatar(ui, active = ringing || dialing, size = if (hasContent) 96.dp else 176.dp)
        Spacer(Modifier.height(12.dp))
        Text(ui.name ?: Phone.pretty(ui.number), style = px(if (hasContent) 16 else 20, p.text, glow = p.accent), textAlign = TextAlign.Center)
        if (ui.name != null) Text(Phone.pretty(ui.number), style = px(11, p.dim), modifier = Modifier.padding(top = 8.dp))
        if (ui.enemy) Row(Modifier.padding(top = 10.dp)) { EnemyBadge(); Text("  BOSS LEVEL: POSSIBLE SPAM", style = px(8, p.bad)) }
        ui.policyNote?.let { Text(it, style = px(8, p.dim), modifier = Modifier.padding(top = 6.dp)) }
        Text(statusText(ui) + if (dialing) dots() else "", style = px(12, p.accent), modifier = Modifier.padding(top = 10.dp))
        if (recording) Text("● REC", style = px(10, p.bad), modifier = Modifier.padding(top = 8.dp))

        when (hold) {
            HoldState.LISTENING -> Banner("⏸ HOLD FOR ME: listening for a person… (you're muted)", p.surface2) { CallManager.stopHoldForMe() }
            HoldState.HUMAN -> Banner("🎉 SOMEONE'S ON THE LINE! TAP TO TALK", p.good) { CallManager.stopHoldForMe() }
            HoldState.OFF -> {}
        }

        if (!hasContent) Spacer(Modifier.weight(1f))
        else Column(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp).verticalScroll(rememberScrollState())) {
            when (panel) {
                Panel.KEYPAD -> InCallKeypad()
                Panel.NOTES -> NotesPanel()
                Panel.CUE -> CuePanel(ui.number)
                Panel.REPLY -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var smart by remember(ui.number) { mutableStateOf<String?>(null) }
                    LaunchedEffect(ui.number) { smart = app.bitcall.ai.BitAi.smartDecline(context, ui.name, null) }
                    smart?.let { r ->
                        PixelButton(p.accent, Modifier.fillMaxWidth(), onClick = { CallManager.declineWithText(r) }) {
                            Text("✨ $r", style = px(11, onColor(p.accent)), modifier = Modifier.padding(16.dp))
                        }
                    }
                    s.quickReplies.forEach { r ->
                        PixelButton(p.surface2, Modifier.fillMaxWidth(), onClick = { CallManager.declineWithText(r) }) {
                            Text(r, style = px(11, p.text), modifier = Modifier.padding(16.dp))
                        }
                    }
                }
                Panel.NONE -> if (showTranscript) Card(Modifier.fillMaxWidth()) {
                    Text("📝 LIVE TRANSCRIPT", style = px(9, p.accent))
                    Text(transcript + if (partial.isNotBlank()) "\n$partial…" else "", style = px(10, p.text), modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (ringing) {
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                RoundAction(p.bad, Icons.Filled.CallEnd, "DECLINE") { CallManager.hangup() }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixelChip("💬 REPLY", panel == Panel.REPLY) { panel = if (panel == Panel.REPLY) Panel.NONE else Panel.REPLY }
                    Spacer(Modifier.height(8.dp))
                    PixelChip("🔕 SILENCE", false) { CallManager.silence() }
                }
                RoundAction(p.good, Icons.Filled.Call, "ANSWER") { CallManager.answer() }
            }
        } else if (ui.state != Call.STATE_DISCONNECTED) {
            val grid = buildList<Triple<String, Boolean, () -> Unit>> {
                add(Triple(if (audio.muted) "🔇 MUTED" else "🎙 MUTE", audio.muted) { CallManager.setMuted(!audio.muted) })
                add(Triple("⌨ KEYPAD", panel == Panel.KEYPAD) { panel = if (panel == Panel.KEYPAD) Panel.NONE else Panel.KEYPAD })
                add(Triple("🔊 SPEAKER", audio.speaker) { CallManager.setSpeaker(!audio.speaker) })
                if (dialing) return@buildList
                add(Triple(if (ui.state == Call.STATE_HOLDING) "▶ RESUME" else "⏸ HOLD", ui.state == Call.STATE_HOLDING) { CallManager.toggleHold() })
                add(Triple(if (recording) "■ STOP REC" else "● RECORD", recording) {
                    when {
                        recording -> RecorderService.stop(context)
                        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> RecorderService.start(context)
                        else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                })
                add(Triple("🗒 NOTES", panel == Panel.NOTES) { panel = if (panel == Panel.NOTES) Panel.NONE else Panel.NOTES })
                if (s.holdForMe) add(Triple("⏳ HOLD FOR ME", hold != HoldState.OFF) {
                    if (hold == HoldState.OFF) CallManager.startHoldForMe() else CallManager.stopHoldForMe()
                })
                if (s.magicCue) add(Triple("✨ MAGIC CUE", panel == Panel.CUE) { panel = if (panel == Panel.CUE) Panel.NONE else Panel.CUE })
            }
            // 3 big buttons per row: icon on top, label below.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                grid.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (label, on, action) ->
                            val fg = if (on) onColor(p.accent) else p.text
                            PixelButton(if (on) p.accent else p.surface2, Modifier.weight(1f).height(76.dp), onClick = action) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label.substringBefore(' '), fontSize = 22.sp, color = fg)
                                    Text(label.substringAfter(' '), style = px(9, fg), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            RoundAction(p.bad, Icons.Filled.CallEnd, "END") { CallManager.hangup() }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun statusText(ui: CallUi): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(ui.state) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    return when (ui.state) {
        Call.STATE_RINGING -> "INCOMING CALL"
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW -> "CALLING"
        Call.STATE_HOLDING -> "ON HOLD"
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED ->
            "CALL ENDED" + if (ui.connectTime > 0) " · " + DateUtils.formatElapsedTime((now - ui.connectTime) / 1000) else ""
        Call.STATE_ACTIVE -> if (ui.connectTime > 0) DateUtils.formatElapsedTime((now - ui.connectTime).coerceAtLeast(0) / 1000) else "CONNECTED"
        else -> ""
    }
}

@Composable
private fun Banner(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, style = px(8, onColor(color)), textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(color).clickable(onClick = onClick).padding(12.dp),
    )
}

@Composable
private fun RoundAction(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PixelButton(color, Modifier.size(width = 140.dp, height = 76.dp), onClick = onClick) {
            Icon(icon, label, tint = onColor(color), modifier = Modifier.size(36.dp))
        }
        Text(label, style = px(9, LocalPalette.current.dim), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun InCallKeypad() {
    val p = LocalPalette.current
    val s = LocalBitSettings.current
    var typed by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(typed, style = px(14, p.accent), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = TextAlign.Center)
        KEYS.chunked(3).forEachIndexed { row, keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                keys.forEachIndexed { col, (d, _) ->
                    val color = p.key(row * 3 + col, s)
                    PixelButton(color, Modifier.weight(1f).aspectRatio(2.2f), onClick = { typed += d; CallManager.dtmf(d.single()) }) {
                        Text(d, style = px(14, onColor(color)))
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesPanel() {
    val p = LocalPalette.current
    val session by CallManager.session.collectAsState()
    val sess = session ?: return
    val notes by sess.notes.collectAsState()
    val transcript by sess.transcript.collectAsState()
    val partial by CallManager.partial.collectAsState()
    OutlinedTextField(
        notes, { sess.notes.value = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
        textStyle = px(9, p.text), placeholder = { Text("Type notes… saved with this call", style = px(8, p.dim)) },
    )
    Row(Modifier.padding(top = 8.dp)) { PixelChip("📝 TRANSCRIBE (SPEAKER)", false) { CallManager.startTranscript() } }
    if (transcript.isNotBlank() || partial.isNotBlank()) {
        Text("TRANSCRIPT (best effort)", style = px(7, p.accent), modifier = Modifier.padding(top = 10.dp))
        Text(transcript + if (partial.isNotBlank()) "\n$partial…" else "", style = px(8, p.dim), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CuePanel(number: String) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var cues by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(number) { cues = MagicCue.cues(context, number) }
    Card(Modifier.fillMaxWidth()) {
        Text("✨ MAGIC CUE", style = px(9, p.accent))
        (cues ?: listOf("Thinking…")).forEach { Text(it, style = px(8, p.text), modifier = Modifier.padding(top = 8.dp)) }
    }
}

/** Avatar with expanding pixel rings while a call is ringing in or out. */
@Composable
private fun RingingAvatar(ui: CallUi, active: Boolean, size: androidx.compose.ui.unit.Dp) {
    val p = LocalPalette.current
    val t = rememberInfiniteTransition(label = "ring")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "pulse")
    val ringColor = if (ui.enemy) p.bad else p.accent
    Box(Modifier.size(size * 1.45f), contentAlignment = Alignment.Center) {
        if (active) {
            listOf(0f, 0.5f).forEach { offset ->
                val f = (pulse + offset) % 1f
                Box(
                    Modifier.size(size + size * 0.45f * f).alpha(1f - f)
                        .border(4.dp, ringColor, RoundedCornerShape(18.dp)),
                )
            }
        }
        PixelAvatar(ui.name ?: ui.number, ui.photoUri, size, ui.enemy)
    }
}

@Composable
private fun blink(): Float {
    val t = rememberInfiniteTransition(label = "blink")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a")
    return a
}

@Composable
private fun dots(): String {
    var n by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(400); n = (n + 1) % 4 } }
    return ".".repeat(n)
}
