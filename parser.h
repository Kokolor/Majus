//
// Created by kokolor on 24/05/25.
//

#ifndef PARSER_H
#define PARSER_H

#include "lexer.h"

typedef struct Node Node;

typedef enum
{
    n_expression,
    n_variable_declaration,
    n_program
} NodeType;

struct Node
{
    TokenType operation_token;
    NodeType type;
    Node* left_node;
    Node* right_node;
    Node** statements;
    char* value;
    char* variable_name;
    int statements_count;
};

typedef struct
{
    TokenList tokens;
    int current_token;
} Parser;

void parser_print_ast(const Node* node, const int indentation);

Node* parser_create_node(const TokenType operation, Node* left_node, Node* right_node, const char* value);

TokenType parser_get_current_token_type(const Parser* parser);

void parser_advance(Parser* parser);

Node* parser_parse_factor(Parser* parser);

Node* parser_parse_term(Parser* parser);

Node* parser_parse_expression(Parser* parser);

Node* parser_parse_statement(Parser* parser);

Node* parser_parse_variable_declaration(Parser* parser);

Node* parser_parse(Parser* parser);

void parser_free(Node* node);

void parser_init(Parser* parser, const TokenList tokens);

#endif //PARSER_H
