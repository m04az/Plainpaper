cmake_minimum_required(VERSION 3.22)
project(plainpaper_scan LANGUAGES CXX)

# PP_PLATFORM selects the flavour (research R2-R4):
#   android: shared library + JNI bridge, linked by AGP's externalNativeBuild
#   host:    shared library + JNI bridge for the JVM quality suite
#   ios:     static library (no JNI); Kotlin/Native cinterop includes it into the klib
set(PP_PLATFORM "host" CACHE STRING "android | ios | host")
set_property(CACHE PP_PLATFORM PROPERTY STRINGS android ios host)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
include(FindPlainpaperOpenCV)

set(PP_CORE_SOURCES
    src/debug.cpp
    src/engine.cpp
    src/detect.cpp
    src/geometry.cpp
    src/deskew.cpp
    src/orient.cpp
    src/flatten.cpp
    src/illumination.cpp
    src/render.cpp
)

if(PP_PLATFORM STREQUAL "ios")
    add_library(plainpaper_scan STATIC ${PP_CORE_SOURCES})
else()
    add_library(plainpaper_scan SHARED ${PP_CORE_SOURCES} jni/plainpaper_jni.cpp)
endif()

target_include_directories(plainpaper_scan
    PUBLIC include
    PRIVATE src)
target_compile_options(plainpaper_scan PRIVATE -Wall -Wextra -Werror -fexceptions -frtti)
target_compile_definitions(plainpaper_scan PRIVATE PP_BUILDING)

plainpaper_link_opencv(plainpaper_scan)

if(PP_PLATFORM STREQUAL "android")
    target_compile_definitions(plainpaper_scan PRIVATE PP_ANDROID=1)
    target_link_libraries(plainpaper_scan PRIVATE jnigraphics log)
    # Dead-strip unused OpenCV code and keep the exported surface to pp_* / JNI symbols.
    target_link_options(plainpaper_scan PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
elseif(PP_PLATFORM STREQUAL "host")
    find_package(JNI REQUIRED)
    target_compile_definitions(plainpaper_scan PRIVATE PP_HOST=1)
    target_include_directories(plainpaper_scan PRIVATE ${JNI_INCLUDE_DIRS})
    if(APPLE)
        target_link_options(plainpaper_scan PRIVATE -Wl,-dead_strip)
    else()
        target_link_options(plainpaper_scan PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
    endif()
else()
    target_compile_definitions(plainpaper_scan PRIVATE PP_IOS=1)
endif()
