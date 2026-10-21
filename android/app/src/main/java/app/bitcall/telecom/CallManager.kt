package app.bitcall.telecom

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.Connection
import android.telecom.VideoProfile
import app.bitcall.assist.ChipSound
import app.bitcall.assist.Transcriber
import app.bitcall.data.CallMemo
import app.bitcall.data.ContactsRepo
import app.bitcall.data.NotesStore
import app.bitcall.data.Settings
import app.bitcall.ai.BitAi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

data class CallUi(
    val call: Call,
    val number: String,
    val name: String?,
    val photoUri: String?,
    val starred: Boolean,
    val state: Int,
    val incoming: Boolean,
    val connectTime: Long,
    val enemy: Boolean,
    val policyNote: String?,
)

data class AudioUi(val muted: Boolean = false, val speaker: Boolean = false)

enum class HoldState { OFF, LISTENING, HUMAN }

/** Per-call scratchpad: notes, transcript, recording. Saved to [NotesStore] when the call ends. */
class CallSession(val number: String, val name: String?, val photoUri: String?, val starred: Boolean, val start: Long) {
    val id = "$start-$number"
    val notes = MutableStateFlow("")
    val transcript = MutableStateFlow("")
    var recordingUri: String? = null
}

object CallManager {
    var service: BitcallInCallService? = null
    private lateinit var app: Context

    private val _calls = MutableStateFlow<List<CallUi>>(emptyList())
    val calls: StateFlow<List<CallUi>> = _calls
    val audio = MutableStateFlow(AudioUi())
    val recording = MutableStateFlow(false)
    val hold = MutableStateFlow(HoldState.OFF)
    val partial = MutableStateFlow("")
    val session = MutableStateFlow<CallSession?>(null)

    private val sessions = HashMap<Call, CallSession>()
    private val decisions = HashMap<Call, CallPolicy.Decision>()
    private var transcriber: Transcriber? = null

    val primary: CallUi? get() = _calls.value.firstOrNull { it.state != Call.STATE_DISCONNECTED }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = onChanged(call)
        override fun onDetailsChanged(call: Call, details: Call.Details) = refresh()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun add(context: Context, call: Call) {
        app = context.applicationContext
        scope.launch { BitAi.releaseMain() } // keep the phone cool: only the small model during calls
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        val verificationFailed = call.details.callerNumberVerificationStatus == Connection.VERIFICATION_STATUS_FAILED
        val incoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
        decisions[call] = if (incoming) CallPolicy.decide(app, number, verificationFailed)
        else CallPolicy.Decision(false, false, false, null)
        val contact = ContactsRepo.lookup(app, number)
        sessions[call] = CallSession(number, contact?.name, contact?.photoUri, contact?.starred == true, System.currentTimeMillis())
        // Lift phone DND for a ★ favorite before the incoming-call screen posts, or DND hides it.
        if (call.state == Call.STATE_RINGING && decisions[call]?.let { !it.silence && !it.reject } == true) {
            Ringer.liftDndForFavorite(app, contact?.starred == true)
        }
        call.registerCallback(callback)
        refresh()
        if (call.state == Call.STATE_RINGING) Ringer.start(app, decisions[call]!!, contact?.starred == true)
    }

    fun remove(call: Call) {
        call.unregisterCallback(callback)
        sessions.remove(call)?.let(::finish)
        decisions.remove(call)
        refresh()
        if (primary == null) {
            Ringer.stop()
            stopAssist()
            if (recording.value) RecorderService.stop(app)
            CallNotifications.cancel(app)
        }
    }

    private fun onChanged(call: Call) {
        if (call.state != Call.STATE_RINGING) Ringer.stop()
        if (call.state == Call.STATE_ACTIVE) {
            if (Settings.now.autoRecord && !recording.value) RecorderService.start(app)
            if (Settings.now.liveTranscript) ensureTranscriber()
        }
        refresh()
    }

    private fun refresh() {
        _calls.value = sessions.keys.map { c ->
            val s = sessions[c]!!
            val d = decisions[c]
            CallUi(
                call = c, number = s.number, name = s.name, photoUri = s.photoUri, starred = s.starred,
                state = c.state, incoming = c.details.callDirection == Call.Details.DIRECTION_INCOMING,
                connectTime = c.details.connectTimeMillis, enemy = d?.enemy == true, policyNote = d?.reason,
            )
        }
        session.value = primary?.let { sessions[it.call] }
        primary?.let { CallNotifications.show(app, it) }
    }

    private fun finish(s: CallSession) {
        NotesStore.upsert(CallMemo(s.id, s.number, s.name, s.start, s.notes.value, s.transcript.value, s.recordingUri))
    }

    /** The session a recording belongs to; the file can finish saving after its call has ended. */
    var recordingSession: CallSession? = null

    // --- controls ---------------------------------------------------------------------------

    fun answer() { primary?.call?.answer(VideoProfile.STATE_AUDIO_ONLY); Ringer.stop() }
    fun hangup() { primary?.call?.let { if (it.state == Call.STATE_RINGING) it.reject(false, null) else it.disconnect() } }
    fun declineWithText(text: String) { primary?.call?.reject(true, text); Ringer.stop() }
    fun silence() = Ringer.stop()

    fun setMuted(muted: Boolean) { service?.setMuted(muted) }
    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE)
    }
    fun toggleHold() {
        val c = primary?.call ?: return
        if (c.state == Call.STATE_HOLDING) c.unhold() else c.hold()
    }
    fun dtmf(ch: Char) { primary?.call?.run { playDtmfTone(ch); stopDtmfTone() } }

    fun onAudio(state: CallAudioState) {
        audio.value = AudioUi(state.isMuted, state.route == CallAudioState.ROUTE_SPEAKER)
    }

    fun onRecordingSaved(uri: String) {
        recording.value = false
        recordingSession?.let { it.recordingUri = uri; finish(it) }
        recordingSession = null
    }

    // --- Hold for Me + live transcript (speaker + mic workaround) ----------------------------

    fun startHoldForMe() {
        setSpeaker(true)
        setMuted(true)
        hold.value = HoldState.LISTENING
        ensureTranscriber()
    }

    fun stopHoldForMe() {
        hold.value = HoldState.OFF
        setMuted(false)
        if (!Settings.now.liveTranscript) stopAssist()
    }

    fun startTranscript() { setSpeaker(true); ensureTranscriber() }

    private fun ensureTranscriber() {
        if (transcriber != null) return
        transcriber = Transcriber(app) { text, final ->
            partial.value = if (final) "" else text
            if (final) session.value?.transcript?.let { it.value = (it.value + "\n" + text).trim() }
            if (hold.value == HoldState.LISTENING && HoldDetector.isHuman(text)) humanDetected()
        }.also { it.start() }
    }

    private fun stopAssist() {
        transcriber?.stop()
        transcriber = null
        partial.value = ""
        if (hold.value != HoldState.OFF) hold.value = HoldState.OFF
    }

    private fun humanDetected() {
        hold.value = HoldState.HUMAN
        ChipSound.powerUp()
        app.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 600), -1))
        CallNotifications.humanOnLine(app)
    }
}

object HoldDetector {
    private val holdPhrases = listOf(
        "your call is important", "continue to hold", "stay on the line", "all of our", "representative",
        "wait time", "in the queue", "press", "please hold", "next available", "calls may be recorded",
    )
    private val humanPhrases = listOf(
        "hello", "hi there", "are you there", "thanks for holding", "thank you for holding", "thanks for waiting",
        "thank you for waiting", "my name is", "speaking", "how can i help", "how may i help", "who am i speaking",
    )

    fun isHuman(text: String): Boolean {
        val t = text.lowercase()
        if (holdPhrases.any { it in t }) return false
        return humanPhrases.any { it in t }
    }
}
