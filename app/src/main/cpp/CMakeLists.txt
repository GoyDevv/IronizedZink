cmake_minimum_required(VERSION 3.22.1)
project(ironized_zink C)

find_library(log-lib log)

# 16 KB page-size alignment (required on Android 15+ devices with 16 KB pages).
set(IZ_LINK_OPTS "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")

# Runtime config shim — applies the user's saved preset (env) in the game process.
# Referenced by DLOPEN=libironized_zink.so in the plugin manifest's pojavEnv.
add_library(ironized_zink SHARED ironized_zink.c)
target_link_options(ironized_zink PRIVATE ${IZ_LINK_OPTS})
target_link_libraries(ironized_zink ${log-lib})
