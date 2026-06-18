// Placeholder for whisper.h
#pragma once
struct whisper_context;
struct whisper_full_params;
whisper_context* whisper_init_from_file(const char* path);
void whisper_free(whisper_context* ctx);
int whisper_full(whisper_context* ctx, whisper_full_params params, const float* samples, int n_samples);
whisper_full_params whisper_full_default_params(int strategy);
