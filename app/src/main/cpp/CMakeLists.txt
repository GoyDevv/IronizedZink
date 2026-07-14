cmake_minimum_required(VERSION 3.22.1)
project(ironized_zink C)

# Produces libironized_zink.so — referenced by DLOPEN=libironized_zink.so in the
# plugin manifest's pojavEnv.
add_library(ironized_zink SHARED ironized_zink.c)

find_library(log-lib log)
target_link_libraries(ironized_zink ${log-lib})
