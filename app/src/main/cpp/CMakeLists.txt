cmake_minimum_required(VERSION 3.22.1)

project("chromaprint_jni")

add_library(
    chromaprint_jni
    SHARED
    chromaprint_jni.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    chromaprint_jni
    ${log-lib}
)
