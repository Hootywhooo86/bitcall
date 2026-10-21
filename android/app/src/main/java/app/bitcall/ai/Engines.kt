package app.bitcall.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * JNI bridge to llama.cpp (libbitcall_llama.so). The native module isn't bundled yet, so
 * [available] is false and every AI feature falls back to its non-AI behaviour.
 */
object LlamaBridge {
    val available: Boolean = runCatching { System.loadLibrary("bitcall_llama"); true }.getOrDefault(false)

    external fun nativeLoad(path: String, contextSize: Int, threads: Int): Long
    external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int, temperature: Float): String
    external fun nativeFree(handle: Long)
}

/** JNI bridge to whisper.cpp (libbitcall_whisper.so), same deal as [LlamaBridge]. */
object WhisperBridge {
    val available: Boolean = runCatching { System.loadLibrary("bitcall_whisper"); true }.getOrDefault(false)

    /** [pcm16k] is mono 16 kHz float PCM. */
    external fun nativeTranscribe(modelPath: String, pcm16k: FloatArray, language: String): String
}

interface TextEngine {
    suspend fun generate(system: String, user: String, maxTokens: Int = 160, temperature: Float = 0.3f): String?
}

/** One loaded GGUF model. Loaded lazily, freed with [close] so the 4B never sits in RAM during calls. */
class LlamaEngine(private val path: String, private val contextSize: Int = 2048) : TextEngine {
    private val lock = Mutex()
    private var handle = 0L

    override suspend fun generate(system: String, user: String, maxTokens: Int, temperature: Float): String? =
        if (!LlamaBridge.available) null else withContext(Dispatchers.Default) {
            lock.withLock {
                if (handle == 0L) handle = LlamaBridge.nativeLoad(path, contextSize, Runtime.getRuntime().availableProcessors().coerceAtMost(6))
                if (handle == 0L) return@withLock null
                // Qwen chat template; /no_think skips the reasoning trace for short utility answers.
                val prompt = "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user /no_think<|im_end|>\n<|im_start|>assistant\n"
                LlamaBridge.nativeGenerate(handle, prompt, maxTokens, temperature)
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
