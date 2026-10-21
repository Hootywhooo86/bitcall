#include "bitcall_ai.h"

#include <algorithm>
#include <mutex>

#include "chat.h"
#include "common.h"
#include "llama.h"
#include "sampling.h"
#include "whisper.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "BitcallAI", __VA_ARGS__)
#else
#include <cstdio>
#define LOGE(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#endif

namespace {
constexpr int BATCH = 512;
std::once_flag g_backend_once;
}

struct BitLlm {
    llama_model *             model   = nullptr;
    llama_context *           ctx     = nullptr;
    common_chat_templates_ptr templates;
    int                       n_ctx   = 0;
};

void bit_backend_init(const char * backend_dir) {
    std::call_once(g_backend_once, [&] {
        if (backend_dir && *backend_dir) ggml_backend_load_all_from_path(backend_dir);
        else ggml_backend_load_all();
        llama_backend_init();
    });
}

BitLlm * bit_llm_load(const char * model_path, int n_ctx, int n_threads) {
    llama_model_params mp = llama_model_default_params(); // mmap on: a 2.7 GB model isn't copied into RAM
    llama_model * model = llama_model_load_from_file(model_path, mp);
    if (!model) { LOGE("model load failed: %s", model_path); return nullptr; }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx           = std::min<int>(n_ctx, llama_model_n_ctx_train(model));
    cp.n_batch         = BATCH;
    cp.n_ubatch        = BATCH;
    cp.n_threads       = n_threads;
    cp.n_threads_batch = n_threads;
    llama_context * ctx = llama_init_from_model(model, cp);
    if (!ctx) { LOGE("context init failed"); llama_model_free(model); return nullptr; }

    auto * llm      = new BitLlm();
    llm->model      = model;
    llm->ctx        = ctx;
    llm->n_ctx      = (int) llama_n_ctx(ctx);
    llm->templates  = common_chat_templates_init(model, "");
    return llm;
}

void bit_llm_free(BitLlm * llm) {
    if (!llm) return;
    llm->templates.reset();
    llama_free(llm->ctx);
    llama_model_free(llm->model);
    delete llm;
}

// Renders the chat with the model's template, falling back to ChatML (Qwen's format) if it fails.
static std::vector<llama_token> render_prompt(BitLlm * llm, const std::string & system, const std::string & user, bool thinking) {
    std::vector<llama_token> tokens;
    try {
        common_chat_templates_inputs in;
        common_chat_msg sys; sys.role = "system"; sys.content = system;
        common_chat_msg usr; usr.role = "user";   usr.content = user;
        in.messages              = {sys, usr};
        in.add_generation_prompt = true;
        in.use_jinja             = true;
        in.enable_thinking       = thinking;
        const auto params = common_chat_templates_apply(llm->templates.get(), in);
        bool first = true;
        for (const auto & part : params.prompt.parts) {
            // Template text may contain special tokens; user/system text must never be parsed as them.
            auto t = common_tokenize(llm->ctx, part.text, first, !part.is_input);
            tokens.insert(tokens.end(), t.begin(), t.end());
            first = false;
        }
        if (!tokens.empty()) return tokens;
    } catch (const std::exception & e) {
        LOGE("chat template failed (%s), using ChatML", e.what());
    }
    const std::string head = "<|im_start|>system\n";
    const std::string mid  = "<|im_end|>\n<|im_start|>user\n";
    const std::string tail = std::string("<|im_end|>\n<|im_start|>assistant\n") + (thinking ? "" : "<think>\n\n</think>\n\n");
    for (auto [text, special] : std::vector<std::pair<std::string, bool>>{{head, true}, {system, false}, {mid, true}, {user, false}, {tail, true}}) {
        auto t = common_tokenize(llm->ctx, text, false, special);
        tokens.insert(tokens.end(), t.begin(), t.end());
    }
    return tokens;
}

std::string bit_llm_generate(BitLlm * llm, const std::string & system, const std::string & user,
                             int max_tokens, float temperature, bool thinking,
                             const std::atomic<bool> * cancel) {
    if (!llm) return "";
    auto tokens = render_prompt(llm, system, user, thinking);
    const int budget = llm->n_ctx - max_tokens - 4;
    if ((int) tokens.size() > budget) { LOGE("prompt too long: %d tokens", (int) tokens.size()); return ""; }

    llama_memory_clear(llama_get_memory(llm->ctx), true); // every request starts fresh
    common_batch batch(llm->ctx);
    for (size_t i = 0; i < tokens.size(); i += BATCH) {
        batch.clear();
        const size_t n = std::min<size_t>(BATCH, tokens.size() - i);
        for (size_t j = 0; j < n; j++) batch.add(tokens[i + j], (llama_pos) (i + j), 0, i + j == tokens.size() - 1);
        if (llama_process(llm->ctx, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) { LOGE("prompt decode failed"); return ""; }
    }

    common_params_sampling sp;
    sp.temp = temperature;
    common_sampler * smpl = common_sampler_init(llm->model, sp);
    const llama_vocab * vocab = llama_model_get_vocab(llm->model);

    std::string out;
    llama_pos pos = (llama_pos) tokens.size();
    for (int i = 0; i < max_tokens; i++) {
        if (cancel && cancel->load()) break;
        const llama_token tok = common_sampler_sample(smpl, llm->ctx, -1);
        common_sampler_accept(smpl, tok, true);
        if (llama_vocab_is_eog(vocab, tok)) break;
        out += common_token_to_piece(llm->ctx, tok, false);
        batch.clear();
        batch.add(tok, pos++, 0, true);
        if (llama_process(llm->ctx, LLAMA_PROCESS_TYPE_DECODE, batch.get()) != 0) { LOGE("decode failed"); break; }
    }
    common_sampler_free(smpl);
    return out;
}

std::string bit_whisper_transcribe(const char * model_path, const std::vector<float> & pcm16k,
                                   const char * language, int n_threads) {
    whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    whisper_context * ctx = whisper_init_from_file_with_params(model_path, cp);
    if (!ctx) { LOGE("whisper load failed: %s", model_path); return ""; }

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads        = n_threads;
    p.language         = language;
    p.translate        = false;
    p.print_progress   = false;
    p.print_realtime   = false;
    p.print_timestamps = false;
    p.no_timestamps    = true;

    std::string text;
    if (whisper_full(ctx, p, pcm16k.data(), (int) pcm16k.size()) == 0) {
        const int n = whisper_full_n_segments(ctx);
        for (int i = 0; i < n; i++) text += whisper_full_get_segment_text(ctx, i);
    } else {
        LOGE("whisper_full failed");
    }
    whisper_free(ctx);
    return text;
}
