cmake_minimum_required(VERSION 3.22.1)
project("cheremshabridge")

# Собираем Unicorn как статическую библиотеку
set(UNICORN_ARCH "aarch64" CACHE STRING "" FORCE)
set(UNICORN_BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(UNICORN_STATIC_MSVC_RUNTIME OFF CACHE BOOL "" FORCE)
set(UNICORN_INSTALL OFF CACHE BOOL "" FORCE)
set(UNICORN_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(UNICORN_BUILD_SAMPLES OFF CACHE BOOL "" FORCE)

add_subdirectory(unicorn)

add_library(cheremshabridge SHARED
    unicorn_bridge.cpp
)

target_include_directories(cheremshabridge PRIVATE
    unicorn/include
)

target_link_libraries(cheremshabridge
    unicorn
    log
)
