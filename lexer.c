//
// Created by kokolor on 24/05/25.
//

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <ctype.h>
#include "errors.h"
#include "lexer.h"

const char* lexer_token_type_to_string(const TokenType type)
{
    switch (type)
    {
    case t_plus: return "PLUS";
    case t_minus: return "MINUS";
    case t_star: return "STAR";
    case t_slash: return "SLASH";
    case t_equal: return "EQUAL";
    case t_not_equal: return "NOT EQUAL";
    case t_less: return "LESS";
    case t_less_equal: return "LESS EQUAL";
    case t_greater: return "GREATER";
    case t_greater_equal: return "GREATER EQUAL";
    case t_number: return "NUMBER";
    case t_identifier: return "IDENTIFIER";
    case t_primitive_type: return "TYPE";
    case t_colon: return "COLON";
    case t_semicolon: return "SEMI";
    case t_var: return "VAR";
    case t_fun: return "FUN";
    case t_eof: return "EOF";
    default: return "UNKNOWN";
    }
}

void lexer_add(Lexer* lexer, const TokenType type, const char* value)
{
    if (lexer->tokens.count >= lexer->tokens.capacity)
    {
        lexer->tokens.capacity = (lexer->tokens.capacity == 0) ? 8 : lexer->tokens.capacity * 2;
        void* tokens = realloc(lexer->tokens.tokens, lexer->tokens.capacity * sizeof(Token));

        if (tokens != NULL)
            lexer->tokens.tokens = tokens;
        else
            error(lexer->line, "Cannot realloc tokens");
    }

    lexer->tokens.tokens[lexer->tokens.count].type = type;
    lexer->tokens.tokens[lexer->tokens.count].value = strdup(value);
    lexer->tokens.tokens[lexer->tokens.count].line = lexer->line;
    lexer->tokens.count++;
}

void lexer_scan(Lexer* lexer)
{
    for (int i = 0; i < strlen(lexer->source); i++)
    {
        switch (lexer->source[i])
        {
        case ' ':
            break;
        case '\n':
            lexer->line++;
            break;
        case '+':
            lexer_add(lexer, t_plus, "+");
            break;
        case '-':
            lexer_add(lexer, t_minus, "-");
            break;
        case '*':
            lexer_add(lexer, t_star, "*");
            break;
        case '/':
            lexer_add(lexer, t_slash, "/");
            break;
        case '=':
            lexer_add(lexer, t_equal, "=");
            break;
        case '!':
            if (lexer->source[i + 1] == '=')
            {
                lexer_add(lexer, t_not_equal, "!=");
                i++;
            }
            else
                error(lexer->line, "Expected '=' after '!'");
            break;
        case '<':
            if (lexer->source[i + 1] == '=')
            {
                lexer_add(lexer, t_less_equal, "<=");
                i++;
            }
            else
                lexer_add(lexer, t_less, "<");
            break;
        case '>':
            if (lexer->source[i + 1] == '=')
            {
                lexer_add(lexer, t_greater_equal, ">=");
                i++;
            }
            else
                lexer_add(lexer, t_greater, ">");
            break;
        case ':':
            lexer_add(lexer, t_colon, ":");
            break;
        case ';':
            lexer_add(lexer, t_semicolon, ";");
            break;
        default:
            if (isdigit(lexer->source[i]))
            {
                const int number_start = i;
                int number_end = number_start;

                while (isdigit(lexer->source[number_end]))
                    number_end++;

                const int number_length = number_end - number_start;
                char* number = malloc(number_length + 1);
                strncpy(number, &lexer->source[number_start], number_length);
                number[number_length] = '\0';
                lexer_add(lexer, t_number, number);
                free(number);
                i = number_end - 1;
            }
            else if (isalnum(lexer->source[i]))
            {
                const int identifier_start = i;
                int identifier_end = identifier_start;

                while (isalnum(lexer->source[identifier_end]))
                    identifier_end++;

                const int identifier_length = identifier_end - identifier_start;
                char* identifier = malloc(identifier_length + 1);
                strncpy(identifier, &lexer->source[identifier_start], identifier_length);
                identifier[identifier_length] = '\0';

                if (!strcmp(identifier, "byte") || !strcmp(identifier, "hword") || !strcmp(identifier, "word") || !
                    strcmp(identifier, "qword") || !strcmp(identifier, "ubyte") || !strcmp(identifier, "uhword") || !
                    strcmp(identifier, "uword") || !strcmp(identifier, "uqword"))
                    lexer_add(lexer, t_primitive_type, identifier);
                else if (!strcmp(identifier, "var"))
                    lexer_add(lexer, t_var, identifier);
                else if (!strcmp(identifier, "fun"))
                    lexer_add(lexer, t_fun, identifier);
                else
                    lexer_add(lexer, t_identifier, identifier);

                free(identifier);
                i = identifier_end - 1;
            }
            else
            {
                error(lexer->line, "Unknown Token");
            }

            break;
        }
    }

    lexer_add(lexer, t_eof, "eof");
}

void lexer_free(const Lexer* lexer)
{
    for (int i = 0; i < lexer->tokens.count; i++)
        free((void*)lexer->tokens.tokens[i].value);

    free(lexer->tokens.tokens);
}

void lexer_init(Lexer* lexer, const char* source)
{
    lexer->source = source;
    lexer->line = 0;
    lexer->tokens.tokens = NULL;
    lexer->tokens.count = 0;
    lexer->tokens.capacity = 0;
}
