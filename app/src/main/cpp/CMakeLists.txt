cmake_minimum_required(VERSION 3.22.1)

project("retro_bridge")

add_library(
    retro_bridge
    SHARED
    bridge.cpp
)

target_include_directories(
    retro_bridge
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    retro_bridge
    ${log-lib}
    ${android-lib}
)
