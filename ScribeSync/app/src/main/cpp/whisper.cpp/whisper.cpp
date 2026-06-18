#include "whisper.h"

struct whisper_context {
    // Empty placeholder
};

struct whisper_full_params {
    int strategy;
};

whisper_context* whisper_init_from_file(const char* path) {
    return new whisper_context();
}

void whisper_free(whisper_context* ctx) {
    delete ctx;
}

int whisper_full(whisper_context* ctx, whisper_full_params params, const float* samples, int n_samples) {
    return 0;
}

whisper_full_params whisper_full_default_params(int strategy) {
    return { strategy };
}
