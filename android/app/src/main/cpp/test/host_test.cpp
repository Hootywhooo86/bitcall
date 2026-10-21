// Desktop smoke test for the native AI core (not shipped in the APK).
#include <cmath>
#include <cstdio>
#include <string>
#include <vector>

#include "../bitcall_ai.h"

int main(int argc, char ** argv) {
    if (argc < 2) { fprintf(stderr, "usage: %s <qwen.gguf> [whisper.bin]\n", argv[0]); return 2; }
    bit_backend_init(nullptr);
    BitLlm * llm = bit_llm_load(argv[1], 2048, 4);
    if (!llm) return 1;

    const auto summary = bit_llm_generate(llm,
        "You summarize voicemails in ONE line under 10 words, like: Dentist, confirm Thursday 2 PM. No quotes.",
        "Hi it's Dr. Patel's dental office calling to confirm your cleaning appointment this Thursday at 2 PM. "
        "Please call us back at 555 0100 if you need to reschedule. Thanks!",
        48, 0.0f, false, nullptr);
    printf("SUMMARY: [%s]\n", summary.c_str());

    const auto json = bit_llm_generate(llm,
        "Turn a question about the user's phone call history into JSON with keys: action (\"filter\" or \"call\"), "
        "name (string or null), types (array of \"missed\",\"incoming\",\"outgoing\"), since_days (int or null), "
        "until_days (int or null). Reply with JSON only.",
        "who called me from mom last week?", 96, 0.0f, false, nullptr);
    printf("QUERY: [%s]\n", json.c_str());
    bit_llm_free(llm);

    if (argc > 2) {
        // argv[3]: optional 16 kHz mono 16-bit WAV (e.g. whisper.cpp/samples/jfk.wav); default 1 s of silence.
        std::vector<float> pcm(16000, 0.0f);
        if (argc > 3) {
            FILE * f = fopen(argv[3], "rb");
            if (!f) return 1;
            fseek(f, 44, SEEK_SET); // canonical WAV header
            pcm.clear();
            short s;
            while (fread(&s, 2, 1, f) == 1) pcm.push_back(s / 32768.0f);
            fclose(f);
        }
        printf("WHISPER: [%s]\n", bit_whisper_transcribe(argv[2], pcm, "en", 4).c_str());
    }
    return summary.empty() || json.empty();
}
