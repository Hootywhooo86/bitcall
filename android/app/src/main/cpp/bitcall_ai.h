// Bitcall on-device AI core: Qwen (llama.cpp) text generation + Whisper (whisper.cpp) speech-to-text.
// Plain C++ so it can be unit-tested on a desktop; bitcall_jni.cpp wraps it for Kotlin.
#pragma once

#include <atomic>
#include <string>
#include <vector>

struct BitLlm;

// Loads ggml CPU backend variants from `backend_dir` (the APK's native lib dir) and inits llama.
// Safe to call more than once.
void bit_backend_init(const char * backend_dir);

BitLlm *    bit_llm_load(const char * model_path, int n_ctx, int n_threads);
void        bit_llm_free(BitLlm * llm);
// Runs one stateless chat turn (system + user) through the model's own chat template.
// `thinking` false asks Qwen to skip its reasoning trace. Returns "" on failure.
std::string bit_llm_generate(BitLlm * llm, const std::string & system, const std::string & user,
                             int max_tokens, float temperature, bool thinking,
                             const std::atomic<bool> * cancel);

// Transcribes mono 16 kHz float PCM. `language` is e.g. "en", "fr" or "auto". Returns "" on failure.
std::string bit_whisper_transcribe(const char * model_path, const std::vector<float> & pcm16k,
                                   const char * language, int n_threads);
