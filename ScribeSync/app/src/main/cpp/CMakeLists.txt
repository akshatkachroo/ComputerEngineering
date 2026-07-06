cmake_minimum_required(VERSION 3.22.1)
project("scribesync")

# AGP passes CMAKE_BUILD_TYPE=Debug for debug variants, which compiles at -O0.
# ggml inference (whisper + llama) is 10-50x slower unoptimized - a summary that takes
# ~30s optimized took ~30min at -O0. Keep full optimization in every variant; this only
# affects native code and does not hinder Java/Kotlin debugging.
set(CMAKE_C_FLAGS_DEBUG "${CMAKE_C_FLAGS_DEBUG} -O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O3 -DNDEBUG")

set(WHISPER_REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp")

# Core source files for Whisper and GGML
# These are grouped by their location in the whisper.cpp modular structure.

set(WHISPER_SOURCES
    whisper_jni.cpp

    # Whisper Engine
    "${WHISPER_REPO_ROOT}/src/whisper.cpp"

    # GGML Core (Essential Math and Infrastructure)
    "${WHISPER_REPO_ROOT}/ggml/src/ggml.c"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-alloc.c"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-backend.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-backend-reg.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-backend-meta.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-quants.c"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-threading.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/gguf.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-backend-dl.cpp"

    # CPU Backend and Ops
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/ggml-cpu.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/ggml-cpu.c"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/ops.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/binary-ops.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/unary-ops.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/vec.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/quants.c"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/traits.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/repack.cpp"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/llamafile/sgemm.cpp"
)

# Architecture-specific implementation logic
if(ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "armeabi-v7a")
    list(APPEND WHISPER_SOURCES
        "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/arch/arm/quants.c"
        "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/arch/arm/repack.cpp"
    )
elseif(ANDROID_ABI STREQUAL "x86_64" OR ANDROID_ABI STREQUAL "x86")
    list(APPEND WHISPER_SOURCES
        "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/arch/x86/quants.c"
        "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu/arch/x86/repack.cpp"
    )
endif()

add_library(scribesync SHARED ${WHISPER_SOURCES})

# Include directories
target_include_directories(scribesync PUBLIC
    "${WHISPER_REPO_ROOT}/include"
    "${WHISPER_REPO_ROOT}/src"
    "${WHISPER_REPO_ROOT}/ggml/include"
    "${WHISPER_REPO_ROOT}/ggml/src"
    "${WHISPER_REPO_ROOT}/ggml/src/ggml-cpu"
    "${CMAKE_CURRENT_SOURCE_DIR}"
)

# Link against Android system libraries
find_library(log-lib log)
find_library(dl-lib dl)

# Core feature definitions - scoped to the whisper target only, so they don't leak
# into the llama.cpp subdirectory build (its ggml defines its own version macros).
target_compile_definitions(scribesync PRIVATE
    GGML_USE_CPU
    GGML_USE_LLAMAFILE
    WHISPER_VERSION="1.7.1"
    GGML_VERSION="1"
    GGML_COMMIT="unknown"
)

# For Android 15+ support (large page size)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

target_link_libraries(scribesync
    ${log-lib}
    ${dl-lib}
    m
    atomic
)

# ---------------------------------------------------------------------------
# On-device summarization: llama.cpp built as a separate shared library.
#
# whisper.cpp (above) and llama.cpp each bundle their own copy of ggml. They are
# kept apart by building llama + its ggml as static archives inside libsummarizer.so
# and hiding those symbols (--exclude-libs,ALL), so neither library can resolve the
# other's ggml at runtime.
#
# Privacy: common/tools/server (the parts of llama.cpp with networking) are not built.
# ---------------------------------------------------------------------------
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
# llamafile sgemm uses fp16 NEON intrinsics unavailable on armeabi-v7a; disable it
# so all three ABIs build identically (minor prompt-processing perf cost).
set(GGML_LLAMAFILE       OFF CACHE BOOL "" FORCE)
add_subdirectory(llama.cpp)

add_library(summarizer SHARED llama_jni.cpp)

target_include_directories(summarizer PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include"
    "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include"
)

target_link_libraries(summarizer PRIVATE
    llama
    ${log-lib}
)

target_link_options(summarizer PRIVATE
    -Wl,--exclude-libs,ALL
    -Wl,-z,max-page-size=16384
)
