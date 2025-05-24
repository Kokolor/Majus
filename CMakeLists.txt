cmake_minimum_required(VERSION 3.31)
project(Majus C)

set(CMAKE_C_STANDARD 11)

add_executable(Majus main.c
        lexer.c
        lexer.h
        errors.c
        errors.h)
