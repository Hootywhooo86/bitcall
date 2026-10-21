cmake_minimum_required(VERSION 3.22)
project(bitcall_ai LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# llama.cpp brings ggml; whisper.cpp is compiled against that same ggml so both share one runtime.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM")
    set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE) # Arm-optimised matmul kernels
    set(GGML_OPENMP ON CACHE BOOL "" FORCE)
endif()
foreach(opt LLAMA_BUILD_TESTS LLAMA_BUILD_EXAMPLES LLAMA_BUILD_TOOLS LLAMA_BUILD_SERVER LLAMA_BUILD_APP LLAMA_OPENSSL LLAMA_CURL GGML_LLAMAFILE)
    set(${opt} OFF CACHE BOOL "" FORCE)
endforeach()
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
add_subdirectory(llama.cpp build-llama)

set(WHISPER_SRC ${CMAKE_CURRENT_LIST_DIR}/whisper.cpp)
add_library(bitcall_whisper STATIC
    ${WHISPER_SRC}/src/whisper.cpp
    ${WHISPER_SRC}/src/aneforge/whisper-aneforge.cpp)
target_include_directories(bitcall_whisper PUBLIC ${WHISPER_SRC}/include ${WHISPER_SRC}/src)
set_target_properties(bitcall_whisper PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_compile_definitions(bitcall_whisper PRIVATE WHISPER_VERSION="1.9.5")
target_link_libraries(bitcall_whisper PUBLIC ggml)

add_library(bitcall_core STATIC bitcall_ai.cpp)
set_target_properties(bitcall_core PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_include_directories(bitcall_core PUBLIC llama.cpp/include llama.cpp/common)
target_link_libraries(bitcall_core PUBLIC llama llama-common bitcall_whisper)

if(ANDROID)
    add_library(bitcall_ai SHARED bitcall_jni.cpp)
    target_link_libraries(bitcall_ai PRIVATE bitcall_core android log)
else()
    # Desktop smoke test: ./bitcall_ai_test <qwen.gguf> [whisper.bin]
    add_executable(bitcall_ai_test test/host_test.cpp)
    target_link_libraries(bitcall_ai_test PRIVATE bitcall_core)
endif()
