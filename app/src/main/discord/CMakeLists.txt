# Discord status bridge: PixelPlayer's JNI wrapper around the Discord Social SDK (Prefab package
# from app/libs/discord_partner_sdk.aar). Only built when that AAR is present (app/build.gradle.kts).
cmake_minimum_required(VERSION 3.22.1)
project(pixelplay_discord LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(discord_partner_sdk REQUIRED CONFIG)

add_library(pixelplay_discord SHARED discord_presence_jni.cpp)
find_library(log-lib log)
target_link_libraries(pixelplay_discord discord_partner_sdk::discord_partner_sdk ${log-lib})
