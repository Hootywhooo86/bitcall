package app.bitcall.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

enum class ModelRole { MAIN, FAST, SPEECH }

data class ModelSpec(
    val id: String,
    val label: String,
    val role: ModelRole,
    val url: String,
    val fileName: String,
    val bytes: Long,
    val note: String,
)

/**
 * Downloadable on-device models. GGUF files run through llama.cpp; Whisper ggml files through
 * whisper.cpp. Nothing ships inside the APK, so people who never turn AI on never pay for it.
 */
object ModelCatalog {
    private const val HF = "https://huggingface.co"

    val all = listOf(
        ModelSpec("qwen3.5-4b", "Qwen3.5 4B · Q4_K_M", ModelRole.MAIN,
            "$HF/unsloth/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf", "Qwen3.5-4B-Q4_K_M.gguf", 2_740_937_888,
            "Newest small Qwen. Voicemail summaries, notes, call-log questions."),
        ModelSpec("qwen3-4b", "Qwen3 4B · Q4_K_M", ModelRole.MAIN,
            "$HF/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf", "Qwen3-4B-Q4_K_M.gguf", 2_497_280_256,
            "Official Qwen3 build, widest llama.cpp support."),
        ModelSpec("qwen3.5-2b", "Qwen3.5 2B · Q4_K_M", ModelRole.FAST,
            "$HF/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_K_M.gguf", "Qwen3.5-2B-Q4_K_M.gguf", 1_280_835_840,
            "Fast helper with a bit more brains."),
        ModelSpec("qwen3.5-0.8b", "Qwen3.5 0.8B · Q4_K_M", ModelRole.FAST,
            "$HF/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf", "Qwen3.5-0.8B-Q4_K_M.gguf", 532_517_120,
            "Instant jobs during calls: decline texts, spam sorting."),
        ModelSpec("qwen3-1.7b", "Qwen3 1.7B · Q8_0", ModelRole.FAST,
            "$HF/Qwen/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q8_0.gguf", "Qwen3-1.7B-Q8_0.gguf", 1_834_426_016,
            "Official Qwen3 fast helper."),
        ModelSpec("qwen3-0.6b", "Qwen3 0.6B · Q8_0", ModelRole.FAST,
            "$HF/Qwen/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q8_0.gguf", "Qwen3-0.6B-Q8_0.gguf", 639_446_688,
            "Smallest official Qwen3."),
        ModelSpec("whisper-base", "Whisper base · q5_1", ModelRole.SPEECH,
            "$HF/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin", "ggml-base-q5_1.bin", 59_707_625,
            "Voicemail → text, English + French."),
        ModelSpec("whisper-small", "Whisper small · q5_1", ModelRole.SPEECH,
            "$HF/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin", "ggml-small-q5_1.bin", 190_085_487,
            "More accurate voicemail transcripts."),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

sealed interface ModelStatus {
    data object Missing : ModelStatus
    data class Downloading(val fraction: Float) : ModelStatus
    data object Ready : ModelStatus
}

object ModelManager {
    private fun dir(context: Context) = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
    fun file(context: Context, spec: ModelSpec) = File(dir(context), spec.fileName)
    private fun prefs(context: Context) = context.getSharedPreferences("ai_downloads", Context.MODE_PRIVATE)

    fun status(context: Context, spec: ModelSpec): ModelStatus {
        val id = prefs(context).getLong(spec.id, -1)
        if (id >= 0) {
            val dm = context.getSystemService(DownloadManager::class.java)
            dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                if (c.moveToFirst()) {
                    val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    when (st) {
                        DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED ->
                            return ModelStatus.Downloading(done.toFloat() / spec.bytes)
                        DownloadManager.STATUS_FAILED -> prefs(context).edit().remove(spec.id).apply()
                        else -> prefs(context).edit().remove(spec.id).apply()
                    }
                }
            }
        }
        val f = file(context, spec)
        return if (f.exists() && f.length() > spec.bytes * 0.95) ModelStatus.Ready else ModelStatus.Missing
    }

    fun download(context: Context, spec: ModelSpec, wifiOnly: Boolean = true) {
        file(context, spec).delete()
        val req = DownloadManager.Request(Uri.parse(spec.url))
            .setTitle("Bitcall AI · ${spec.label}")
            .setDescription("On-device model")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, "models", spec.fileName)
            .setAllowedOverMetered(!wifiOnly)
        val id = context.getSystemService(DownloadManager::class.java).enqueue(req)
        prefs(context).edit().putLong(spec.id, id).apply()
    }

    fun delete(context: Context, spec: ModelSpec) {
        val id = prefs(context).getLong(spec.id, -1)
        if (id >= 0) context.getSystemService(DownloadManager::class.java).remove(id)
        prefs(context).edit().remove(spec.id).apply()
        file(context, spec).delete()
    }
}
