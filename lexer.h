//
// Created by kokolor on 24/05/25.
//

#ifndef LEXER_H
#define LEXER_H

typedef enum
{
    t_plus,
    t_minus,
    t_star,
    t_slash,
    t_equal,
    t_number,
    t_identifier,
    t_primitive_type,
    t_colon,
    t_semicolon,
    t_var,
    t_eof
} TokenType;

typedef struct
{
    TokenType type;
    const char* value;
} Token;

typedef struct
{
    Token* tokens;
    int count;
    int capacity;
} TokenList;

typedef struct
{
    TokenList tokens;
    const char* source;
    int line;
} Lexer;

const char* lexer_token_type_to_string(const TokenType type);

void lexer_add(Lexer* lexer, const TokenType type, const char* value);

void lexer_scan(Lexer* lexer);

void lexer_free(const Lexer* lexer);

void lexer_init(Lexer* lexer, const char* source);

#endif //LEXER_H
