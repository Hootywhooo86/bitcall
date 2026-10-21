package app.bitcall.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * JNI bridge to libbitcall_ai.so (llama.cpp + whisper.cpp, built from android/app/src/main/cpp).
 * The ggml CPU backend ships as several variants (plain armv8, dotprod, i8mm, SVE…); [init] loads
 * whichever ones this phone's CPU supports from the APK's native library folder.
 */
object LlamaBridge {
    val available: Boolean by lazy { runCatching { System.loadLibrary("bitcall_ai"); true }.getOrDefault(false) }
    @Volatile private var initialized = false

    fun init(context: Context): Boolean {
        if (!available) return false
        if (!initialized) synchronized(this) {
            if (!initialized) { nativeInit(context.applicationInfo.nativeLibraryDir); initialized = true }
        }
        return true
    }

    external fun nativeInit(nativeLibDir: String)
    external fun nativeLoad(path: String, contextSize: Int, threads: Int): Long
    external fun nativeGenerate(handle: Long, system: String, user: String, maxTokens: Int, temperature: Float, thinking: Boolean): String
    external fun nativeCancel()
    external fun nativeFree(handle: Long)
}

object WhisperBridge {
    /** [pcm16k] is mono 16 kHz float PCM; [language] "en", "fr" or "auto". */
    external fun nativeTranscribe(modelPath: String, pcm16k: FloatArray, language: String, threads: Int): String
}

/** Phones have 2–4 big cores; using more than that just makes the little cores the bottleneck. */
internal val aiThreads: Int get() = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 4)

interface TextEngine {
    suspend fun generate(system: String, user: String, maxTokens: Int = 160, temperature: Float = 0.3f): String?
}

/** One loaded GGUF model. Loaded lazily, freed with [close] so the 4B never sits in RAM during calls. */
class LlamaEngine(private val context: Context, private val path: String, private val contextSize: Int = 2048) : TextEngine {
    private val lock = Mutex()
    private var handle = 0L

    override suspend fun generate(system: String, user: String, maxTokens: Int, temperature: Float): String? =
        if (!LlamaBridge.init(context)) null else withContext(Dispatchers.Default) {
            lock.withLock {
                if (handle == 0L) handle = LlamaBridge.nativeLoad(path, contextSize, aiThreads)
                if (handle == 0L) return@withLock null
                // Keep prompts well inside the context window (~4 chars per token).
                LlamaBridge.nativeGenerate(handle, system, user.take(contextSize * 2), maxTokens, temperature, false)
                    .replace(Regex("(?s)<think>.*?</think>"), "")
                    .trim()
                    .ifEmpty { null }
            }
        }

    suspend fun close() = lock.withLock {
        if (handle != 0L) LlamaBridge.nativeFree(handle)
        handle = 0L
    }
}
