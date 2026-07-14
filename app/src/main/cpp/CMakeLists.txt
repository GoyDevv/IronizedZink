cmake_minimum_required(VERSION 3.22.1)
project(ironized_zink C)

# Produces libironized_zink.so — referenced by DLOPEN=libironized_zink.so in the
# plugin manifest's pojavEnv.
add_library(ironized_zink SHARED ironized_zink.c)

# 16 KB page-size alignment (required on Android 15+ devices with 16 KB pages).
target_link_options(ironized_zink PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384")

find_library(log-lib log)
target_link_libraries(ironized_zink ${log-lib})
