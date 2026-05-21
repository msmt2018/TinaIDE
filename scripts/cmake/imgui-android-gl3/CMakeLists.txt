cmake_minimum_required(VERSION 3.22)

project(imgui_android_gl3 LANGUAGES CXX)

if(NOT DEFINED IMGUI_SOURCE_DIR)
    message(FATAL_ERROR "IMGUI_SOURCE_DIR is required")
endif()

get_filename_component(IMGUI_SOURCE_DIR "${IMGUI_SOURCE_DIR}" ABSOLUTE)
if(NOT EXISTS "${IMGUI_SOURCE_DIR}/imgui.h")
    message(FATAL_ERROR "Invalid IMGUI_SOURCE_DIR: ${IMGUI_SOURCE_DIR}")
endif()

set(IMGUI_CORE_SOURCES
    "${IMGUI_SOURCE_DIR}/imgui.cpp"
    "${IMGUI_SOURCE_DIR}/imgui_demo.cpp"
    "${IMGUI_SOURCE_DIR}/imgui_draw.cpp"
    "${IMGUI_SOURCE_DIR}/imgui_tables.cpp"
    "${IMGUI_SOURCE_DIR}/imgui_widgets.cpp"
)

set(IMGUI_BACKEND_SOURCES
    "${IMGUI_SOURCE_DIR}/backends/imgui_impl_android.cpp"
    "${IMGUI_SOURCE_DIR}/backends/imgui_impl_opengl3.cpp"
)

add_library(imgui_android_gl3 SHARED
    ${IMGUI_CORE_SOURCES}
    ${IMGUI_BACKEND_SOURCES}
)

target_compile_features(imgui_android_gl3 PRIVATE cxx_std_17)

target_compile_options(imgui_android_gl3 PRIVATE
    -fno-exceptions
    -fno-rtti
)

target_compile_definitions(imgui_android_gl3 PRIVATE
    IMGUI_IMPL_OPENGL_ES3
)

target_link_options(imgui_android_gl3 PRIVATE
    -static-libstdc++
)

target_include_directories(imgui_android_gl3 PUBLIC
    "${IMGUI_SOURCE_DIR}"
    "${IMGUI_SOURCE_DIR}/backends"
)

target_link_libraries(imgui_android_gl3 PRIVATE
    android
    EGL
    GLESv3
    log
)

set_target_properties(imgui_android_gl3 PROPERTIES
    OUTPUT_NAME "imgui_android_gl3"
    CXX_VISIBILITY_PRESET default
    VISIBILITY_INLINES_HIDDEN OFF
)
