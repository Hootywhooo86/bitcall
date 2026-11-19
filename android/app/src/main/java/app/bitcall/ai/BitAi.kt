package app.bitcall.ai

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.CallLog
import app.bitcall.data.CallEntry
import app.bitcall.data.Contact
import app.bitcall.data.Phone
import app.bitcall.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Every AI feature in one place. Each call returns a result with or without a model: with AI off
 * (the default) or no model downloaded, the rule-based fallback answers instead.
 *
 * Model split: the FAST model handles in-call jobs (decline texts) so the phone stays cool;
 * the MAIN model handles after-call work (summaries, notes, call-log questions).
 */
object BitAi {
    private var main: Pair<String, LlamaEngine>? = null
    private var fast: Pair<String, LlamaEngine>? = null

    val nativeReady get() = LlamaBridge.available
    private val speechLock = Mutex()

    private fun engine(context: Context, role: ModelRole): LlamaEngine? {
        val s = Settings.now
        if (s.aiMode == "OFF" || !LlamaBridge.available) return null
        val wantMain = role == ModelRole.MAIN && s.aiMode == "FULL"
        val id = if (wantMain) s.aiMainModel else s.aiFastModel
        val spec = ModelCatalog.byId(id) ?: return null
        if (ModelManager.status(context, spec) != ModelStatus.Ready) return null
        val path = ModelManager.file(context, spec).absolutePath
        val slot = if (wantMain) main else fast
        if (slot?.first == path) return slot.second
        return LlamaEngine(context.applicationContext, path).also { if (wantMain) main = path to it else fast = path to it }
    }

    /** Drop the big model from memory (called when a call starts). */
    suspend fun releaseMain() { main?.second?.close(); main = null }

    // --- Family AI (other 128bit apps, via FamilyAiService) ----------------------------------

    private fun familyRole() = if (Settings.now.aiMode == "FULL") ModelRole.MAIN else ModelRole.FAST

    /** True when the model the family would get is downloaded and the engine is in this build. */
    fun familyModelReady(context: Context): Boolean {
        if (!LlamaBridge.available) return false
        val s = Settings.now
        val spec = ModelCatalog.byId(if (familyRole() == ModelRole.MAIN) s.aiMainModel else s.aiFastModel) ?: return false
        return ModelManager.status(context, spec) == ModelStatus.Ready
    }

    /** Same model choice as bitcall's own features: MAIN in FULL mode, otherwise FAST. */
    suspend fun generateForFamily(context: Context, system: String, user: String, maxTokens: Int, temperature: Float): String? =
        engine(context, familyRole())?.generate(system, user, maxTokens.coerceIn(1, 400), temperature)

    // --- Voicemail brain ---------------------------------------------------------------------

    suspend fun summarizeVoicemail(context: Context, transcript: String?): String? {
        if (transcript.isNullOrBlank()) return null
        engine(context, ModelRole.MAIN)?.let { e -> summarizeWith(e, transcript)?.let { return it } }
        val first = transcript.split(Regex("(?<=[.!?])\\s")).first().trim()
        return if (first.length > 70) first.take(67) + "…" else first
    }

    /** Whisper speech-to-text for voicemail and call recordings. Null when AI is off or the model isn't downloaded. */
    suspend fun transcribe(context: Context, audio: Uri): String? {
        val s = Settings.now
        if (s.aiMode == "OFF" || !LlamaBridge.init(context)) return null
        val spec = ModelCatalog.byId(s.aiSpeechModel) ?: return null
        if (ModelManager.status(context, spec) != ModelStatus.Ready) return null
        return withContext(Dispatchers.Default) {
            speechLock.withLock {
                val pcm = AudioDecoder.decode(context, audio) ?: return@withLock null
                WhisperBridge.nativeTranscribe(ModelManager.file(context, spec).absolutePath, pcm, "auto", aiThreads)
                    .trim().ifEmpty { null }
            }
        }
    }

    /** Settings → "Test AI": proves the engine and chosen model work on this phone. */
    suspend fun selfTest(context: Context): String {
        if (!LlamaBridge.init(context)) return "Engine missing from this build."
        val role = if (Settings.now.aiMode == "FULL") ModelRole.MAIN else ModelRole.FAST
        val e = engine(context, role) ?: return "Download the selected model first."
        val t0 = SystemClock.elapsedRealtime()
        val out = summarizeWith(e, "Hi it's Dr. Patel's dental office confirming your cleaning Thursday at 2 PM. Call 555-0100 to reschedule.")
        val secs = (SystemClock.elapsedRealtime() - t0) / 1000.0
        return if (out == null) "Model loaded but returned nothing." else "✓ \"$out\"  (%.1fs)".format(secs)
    }

    private suspend fun summarizeWith(e: TextEngine, transcript: String) = e.generate(
        "You summarize voicemails in ONE line under 10 words, like: Dentist, confirm Thursday 2 PM. No quotes.",
        transcript, maxTokens = 40, temperature = 0f,
    )?.lines()?.first()

    // --- Ask your call log -------------------------------------------------------------------

    data class CallQuery(
        val name: String? = null,
        val types: Set<Int> = emptySet(),
        val sinceDays: Int? = null,
        val untilDays: Int? = null,
        val call: Boolean = false,
    )

    suspend fun parseQuestion(context: Context, question: String, contacts: List<Contact>): CallQuery {
        // Rules first: they're instant and exact for the common phrasings. The model handles the rest.
        val rules = RuleParser.parse(question, contacts)
        if (rules.call || rules.name != null || rules.types.isNotEmpty() || rules.sinceDays != null) return rules
        engine(context, ModelRole.MAIN)?.generate(
            """Turn a question about the user's phone call history into JSON with keys:
               action ("filter" or "call"), name (string or null), types (array of "missed","incoming","outgoing"),
               since_days (int days ago or null), until_days (int days ago or null). Reply with JSON only.""".trimIndent(),
            question, maxTokens = 80, temperature = 0f,
        )?.let { raw -> runCatching { return fromJson(raw) } }
        return rules
    }

    private fun fromJson(raw: String): CallQuery {
        val o = JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
        val types = o.optJSONArray("types")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
        return CallQuery(
            name = o.optString("name").takeIf { it.isNotBlank() && it != "null" },
            types = types.flatMap { RuleParser.typeIds(it) }.toSet(),
            sinceDays = o.optInt("since_days", 0).takeIf { it > 0 },
            untilDays = o.optInt("until_days", 0).takeIf { it > 0 },
            call = o.optString("action") == "call",
        )
    }

    fun apply(q: CallQuery, calls: List<CallEntry>): List<CallEntry> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        return calls.filter { e ->
            (q.name == null || (e.name ?: "").contains(q.name, true) || e.number.contains(q.name)) &&
                (q.types.isEmpty() || e.type in q.types) &&
                (q.sinceDays == null || e.date >= now - q.sinceDays * day) &&
                (q.untilDays == null || e.date <= now - q.untilDays * day)
        }
    }

    // --- Smart decline + after-call notes ----------------------------------------------------

    suspend fun smartDecline(context: Context, caller: String?, nextEvent: String?): String? =
        engine(context, ModelRole.FAST)?.generate(
            "Write one short, friendly text (max 12 words) declining a phone call. Mention when you can call back if obvious. No quotes.",
            "Caller: ${caller ?: "unknown"}. My next calendar event: ${nextEvent ?: "none"}.",
            maxTokens = 40, temperature = 0.6f,
        )

    suspend fun tidyNote(context: Context, note: String): String {
        engine(context, ModelRole.MAIN)?.generate(
            "Clean up this phone-call note into short bullet points. Keep every fact, names, dates, numbers. No preamble.",
            note, maxTokens = 200,
        )?.let { return it }
        return note.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n") { "• " + it.replaceFirstChar(Char::uppercase) }
    }

    // --- Spam sniffer (pattern-based; no model needed) ---------------------------------------

    /** 0–100. 70+ is a "boss level" warning. */
    fun spamScore(number: String, isContact: Boolean, history: List<CallEntry>, verificationFailed: Boolean = false): Int {
        if (isContact) return 0
        var score = 10
        if (Phone.isSpam(number)) score += 60
        if (verificationFailed) score += 35
        val mine = history.filter { Phone.key(it.number) == Phone.key(number) }
        if (mine.count { it.type == CallLog.Calls.MISSED_TYPE || it.type == CallLog.Calls.REJECTED_TYPE } >= 3) score += 20
        if (mine.size >= 2 && mine.all { it.durationSec < 15 }) score += 10
        if (mine.none { it.outgoing } && mine.size >= 4) score += 10
        val digits = number.filter { it.isDigit() }
        if (digits.removePrefix("1").take(3) in setOf("800", "888", "877", "866", "855", "844", "833")) score += 10
        if (number.startsWith("+") && !number.startsWith("+1")) score += 10
        if (number.isBlank()) score += 20
        return score.coerceIn(0, 100)
    }

    // --- Relationship nudges -----------------------------------------------------------------

    fun nudges(contacts: List<Contact>, calls: List<CallEntry>, days: Int = 21): List<String> {
        val now = System.currentTimeMillis()
        return contacts.filter { it.starred }.mapNotNull { c ->
            val keys = c.numbers.map(Phone::key).toSet()
            val last = calls.firstOrNull { Phone.key(it.number) in keys && it.durationSec > 0 }?.date
            val since = last?.let { ((now - it) / 86_400_000L).toInt() }
            when {
                since == null -> null // no history in the log window; don't nag
                since >= days -> "You haven't called ${c.name.substringBefore(' ')} in ${if (since >= 14) "${since / 7} weeks" else "$since days"}."
                else -> null
            }
        }.take(3)
    }
}

/** Plain-language call-log questions without a model: "missed calls from mom yesterday". */
object RuleParser {
    fun typeIds(word: String): List<Int> = when (word.lowercase()) {
        "missed" -> listOf(CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE)
        "incoming" -> listOf(CallLog.Calls.INCOMING_TYPE, CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE)
        "outgoing" -> listOf(CallLog.Calls.OUTGOING_TYPE)
        else -> emptyList()
    }

    fun parse(question: String, contacts: List<Contact>): BitAi.CallQuery {
        val q = question.lowercase().trim()
        val call = Regex("^(call|dial|ring)\\b").containsMatchIn(q)
        val types = when {
            "missed" in q -> typeIds("missed")
            Regex("\\b(i called|outgoing|did i call)\\b").containsMatchIn(q) -> typeIds("outgoing")
            Regex("\\b(called me|who called|incoming)\\b").containsMatchIn(q) -> typeIds("incoming")
            else -> emptyList()
        }.toSet()
        val (since, until) = when {
            "today" in q -> 1 to null
            "yesterday" in q -> 2 to 1
            "last week" in q -> 14 to 7
            "this week" in q -> 7 to null
            "last month" in q -> 60 to 30
            "this month" in q -> 30 to null
            else -> Regex("last (\\d+) days?").find(q)?.groupValues?.get(1)?.toIntOrNull()?.let { it to null } ?: (null to null)
        }
        // Longest contact name (or first name) mentioned in the question wins.
        val name = contacts.map { it.name }
            .flatMap { n -> listOf(n, n.substringBefore(' ')) }
            .filter { it.length >= 2 && Regex("\\b${Regex.escape(it.lowercase())}('s)?\\b").containsMatchIn(q) }
            .maxByOrNull { it.length }
            ?: if (call) q.replace(Regex("^(call|dial|ring)\\s+"), "").replace(Regex("'s? (cell|mobile|work|home).*$"), "").trim().ifEmpty { null } else null
        return BitAi.CallQuery(name, types, since, until, call)
    }
}
