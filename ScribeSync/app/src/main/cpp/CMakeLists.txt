cmake_minimum_required(VERSION 3.22.1)
project("scribesync")

add_library(scribesync SHARED
    whisper_jni.cpp
    whisper.cpp/whisper.cpp
)

target_include_directories(scribesync PRIVATE
    whisper.cpp
)

find_library(log-lib log)

target_link_libraries(scribesync
    ${log-lib}
)
