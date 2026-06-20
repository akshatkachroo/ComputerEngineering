cmake_minimum_required(VERSION 3.22.1)
project("scribesync")

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

# Core feature definitions
add_definitions(-DGGML_USE_CPU)
add_definitions(-DGGML_USE_LLAMAFILE)
add_definitions(-DWHISPER_VERSION="1.7.1")
add_definitions(-DGGML_VERSION="1")
add_definitions(-DGGML_COMMIT="unknown")

# For Android 15+ support (large page size)
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

target_link_libraries(scribesync
    ${log-lib}
    ${dl-lib}
    m
    atomic
)
