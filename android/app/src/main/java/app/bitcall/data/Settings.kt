package app.bitcall.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Every user-tweakable option in one immutable snapshot. */
data class BitSettings(
    val palette: String = "PIXEL_NIGHT",
    val keyColors: Map<Int, Long> = emptyMap(), // keypad index (0..11) -> ARGB override
    val crt: Boolean = true,
    val keySounds: Boolean = true,
    val chipRingtone: Boolean = true,
    // Call Assist
    val rejectSpam: Boolean = false,
    val silenceUnknown: Boolean = false,
    val autoRecord: Boolean = false,
    val recordOnSpeaker: Boolean = true,
    val holdForMe: Boolean = true,
    val liveTranscript: Boolean = false,
    val magicCue: Boolean = true,
    // Do Not Disturb schedule (minutes after midnight)
    val dndEnabled: Boolean = false,
    val dndStart: Int = 22 * 60,
    val dndEnd: Int = 7 * 60,
    val dndAllowFavorites: Boolean = true,
    // Lists
    val quickReplies: List<String> = DEFAULT_REPLIES,
    val speedDial: Map<Int, String> = emptyMap(), // digit 2..9 -> number
    val spamNumbers: Set<String> = emptySet(),    // normalized keys
    val googleAccount: String? = null,
    // On-device AI: off until the user opts in after install.
    val aiMode: String = "OFF", // OFF | FAST | FULL
    val aiAsked: Boolean = false,
    val aiMainModel: String = "qwen3.5-4b",
    val aiFastModel: String = "qwen3.5-0.8b",
    val aiSpeechModel: String = "whisper-base",
) {
    companion object {
        val DEFAULT_REPLIES = listOf(
            "Can't talk now. What's up?",
            "I'll call you right back.",
            "I'll call you later.",
            "Can't talk now. Call me later?",
        )
    }
}

object Settings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(BitSettings())
    val state: StateFlow<BitSettings> = _state
    val now: BitSettings get() = _state.value

    fun init(context: Context) {
        prefs = context.getSharedPreferences("bitcall", Context.MODE_PRIVATE)
        _state.value = load()
    }

    fun update(block: (BitSettings) -> BitSettings) {
        val next = block(_state.value)
        _state.value = next
        save(next)
    }

    private fun load(): BitSettings {
        val d = BitSettings()
        return BitSettings(
            palette = prefs.getString("palette", d.palette)!!,
            keyColors = prefs.getString("keyColors", null)?.let { j ->
                JSONObject(j).let { o -> o.keys().asSequence().associate { it.toInt() to o.getLong(it) } }
            } ?: d.keyColors,
            crt = prefs.getBoolean("crt", d.crt),
            keySounds = prefs.getBoolean("keySounds", d.keySounds),
            chipRingtone = prefs.getBoolean("chipRingtone", d.chipRingtone),
            rejectSpam = prefs.getBoolean("rejectSpam", d.rejectSpam),
            silenceUnknown = prefs.getBoolean("silenceUnknown", d.silenceUnknown),
            autoRecord = prefs.getBoolean("autoRecord", d.autoRecord),
            recordOnSpeaker = prefs.getBoolean("recordOnSpeaker", d.recordOnSpeaker),
            holdForMe = prefs.getBoolean("holdForMe", d.holdForMe),
            liveTranscript = prefs.getBoolean("liveTranscript", d.liveTranscript),
            magicCue = prefs.getBoolean("magicCue", d.magicCue),
            dndEnabled = prefs.getBoolean("dndEnabled", d.dndEnabled),
            dndStart = prefs.getInt("dndStart", d.dndStart),
            dndEnd = prefs.getInt("dndEnd", d.dndEnd),
            dndAllowFavorites = prefs.getBoolean("dndAllowFavorites", d.dndAllowFavorites),
            quickReplies = prefs.getString("quickReplies", null)?.let { j ->
                JSONArray(j).let { a -> List(a.length()) { a.getString(it) } }
            } ?: d.quickReplies,
            speedDial = prefs.getString("speedDial", null)?.let { j ->
                JSONObject(j).let { o -> o.keys().asSequence().associate { it.toInt() to o.getString(it) } }
            } ?: d.speedDial,
            spamNumbers = prefs.getStringSet("spamNumbers", d.spamNumbers)!!.toSet(),
            googleAccount = prefs.getString("googleAccount", null),
            aiMode = prefs.getString("aiMode", d.aiMode)!!,
            aiAsked = prefs.getBoolean("aiAsked", d.aiAsked),
            aiMainModel = prefs.getString("aiMainModel", d.aiMainModel)!!,
            aiFastModel = prefs.getString("aiFastModel", d.aiFastModel)!!,
            aiSpeechModel = prefs.getString("aiSpeechModel", d.aiSpeechModel)!!,
        )
    }

    private fun save(s: BitSettings) {
        prefs.edit()
            .putString("palette", s.palette)
            .putString("keyColors", JSONObject(s.keyColors.mapKeys { it.key.toString() }).toString())
            .putBoolean("crt", s.crt)
            .putBoolean("keySounds", s.keySounds)
            .putBoolean("chipRingtone", s.chipRingtone)
            .putBoolean("rejectSpam", s.rejectSpam)
            .putBoolean("silenceUnknown", s.silenceUnknown)
            .putBoolean("autoRecord", s.autoRecord)
            .putBoolean("recordOnSpeaker", s.recordOnSpeaker)
            .putBoolean("holdForMe", s.holdForMe)
            .putBoolean("liveTranscript", s.liveTranscript)
            .putBoolean("magicCue", s.magicCue)
            .putBoolean("dndEnabled", s.dndEnabled)
            .putInt("dndStart", s.dndStart)
            .putInt("dndEnd", s.dndEnd)
            .putBoolean("dndAllowFavorites", s.dndAllowFavorites)
            .putString("quickReplies", JSONArray(s.quickReplies).toString())
            .putString("speedDial", JSONObject(s.speedDial.mapKeys { it.key.toString() }).toString())
            .putStringSet("spamNumbers", s.spamNumbers)
            .putString("googleAccount", s.googleAccount)
            .putString("aiMode", s.aiMode)
            .putBoolean("aiAsked", s.aiAsked)
            .putString("aiMainModel", s.aiMainModel)
            .putString("aiFastModel", s.aiFastModel)
            .putString("aiSpeechModel", s.aiSpeechModel)
            .apply()
    }

    fun inDndWindow(minuteOfDay: Int, s: BitSettings = now): Boolean {
        if (!s.dndEnabled) return false
        return if (s.dndStart <= s.dndEnd) minuteOfDay in s.dndStart until s.dndEnd
        else minuteOfDay >= s.dndStart || minuteOfDay < s.dndEnd // wraps midnight
    }
}
