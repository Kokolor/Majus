cmake_minimum_required(VERSION 3.28)
project(Majus C)

set(CMAKE_C_STANDARD 11)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -g -fsanitize=address,undefined")
find_package(LLVM REQUIRED CONFIG)
include_directories(${LLVM_INCLUDE_DIRS})
add_definitions(${LLVM_DEFINITIONS})

add_executable(Majus
        main.c
        lexer.c
        lexer.h
        errors.c
        errors.h
        parser.c
        parser.h
        codegen.c
        codegen.h
)

target_link_libraries(Majus LLVM)