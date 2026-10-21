package app.bitcall.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What Bitcall remembers about a call beyond the system call log. */
data class CallMemo(
    val id: String,
    val number: String,
    val name: String?,
    val start: Long,
    val notes: String = "",
    val transcript: String = "",
    val recordingUri: String? = null,
)

object NotesStore {
    private lateinit var file: File
    private val _memos = MutableStateFlow<List<CallMemo>>(emptyList())
    val memos: StateFlow<List<CallMemo>> = _memos

    fun init(context: Context) {
        file = File(context.filesDir, "call_memos.json")
        _memos.value = runCatching {
            val a = JSONArray(file.readText())
            List(a.length()) { i ->
                a.getJSONObject(i).run {
                    CallMemo(
                        getString("id"), getString("number"), optString("name").ifEmpty { null }, getLong("start"),
                        optString("notes"), optString("transcript"), optString("recordingUri").ifEmpty { null },
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun forNumber(number: String?): List<CallMemo> {
        val k = Phone.key(number)
        return _memos.value.filter { Phone.key(it.number) == k }.sortedByDescending { it.start }
    }

    /** Memo for a call-log row: same number, started within a minute of the log's timestamp. */
    fun match(number: String, date: Long): CallMemo? =
        forNumber(number).firstOrNull { kotlin.math.abs(it.start - date) < 60_000 }

    fun upsert(memo: CallMemo) {
        if (memo.notes.isBlank() && memo.transcript.isBlank() && memo.recordingUri == null) return
        _memos.value = _memos.value.filterNot { it.id == memo.id } + memo
        persist()
    }

    private fun persist() {
        val a = JSONArray()
        _memos.value.forEach { m ->
            a.put(JSONObject().apply {
                put("id", m.id); put("number", m.number); put("name", m.name ?: ""); put("start", m.start)
                put("notes", m.notes); put("transcript", m.transcript); put("recordingUri", m.recordingUri ?: "")
            })
        }
        file.writeText(a.toString())
    }
}
