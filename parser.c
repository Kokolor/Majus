//
// Created by kokolor on 24/05/25.
//

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "errors.h"
#include "parser.h"

void parser_print_ast(const Node* node, const int indentation)
{
    if (node == NULL)
        return;

    for (int i = 0; i < indentation; i++)
        printf("\t");

    if (node->value)
        printf("%s (%s)\n", lexer_token_type_to_string(node->operation_token), node->value);
    else
        printf("%s\n", lexer_token_type_to_string(node->operation_token));

    parser_print_ast(node->left_node, indentation + 1);
    parser_print_ast(node->right_node, indentation + 1);
}

Node* parser_create_node(const TokenType operation, Node* left_node, Node* right_node, const char* value)
{
    Node* node = (Node*)malloc(sizeof(Node));
    if (node == NULL)
        error(0, "Cannot allocate node memory");

    node->operation_token = operation;
    node->left_node = left_node;
    node->right_node = right_node;
    node->value = value ? strdup(value) : NULL;

    return node;
}

TokenType parser_get_current_token_type(const Parser* parser)
{
    return parser->tokens.tokens[parser->current_token].type;
}

void parser_advance(Parser* parser)
{
    parser->current_token++;
}

Node* parser_parse_factor(Parser* parser)
{
    const Token curren_token = parser->tokens.tokens[parser->current_token];

    if (curren_token.type == t_number)
    {
        parser_advance(parser);

        return parser_create_node(t_number, NULL, NULL, curren_token.value);
    }

    error(0, "Unexpected token in factor");
    return NULL;
}

Node* parser_parse_term(Parser* parser)
{
    Node* left_node = parser_parse_factor(parser);

    while (parser_get_current_token_type(parser) == t_star || parser_get_current_token_type(parser) == t_slash)
    {
        const TokenType operation_token = parser_get_current_token_type(parser);
        parser_advance(parser);
        Node* right_node = parser_parse_factor(parser);
        left_node = parser_create_node(operation_token, left_node, right_node, NULL);
    }

    return left_node;
}

Node* parser_parse_expression(Parser* parser)
{
    Node* left_node = parser_parse_term(parser);

    while (parser_get_current_token_type(parser) == t_plus || parser_get_current_token_type(parser) == t_minus)
    {
        const TokenType operation_token = parser_get_current_token_type(parser);
        parser_advance(parser);
        Node* right_node = parser_parse_term(parser);
        left_node = parser_create_node(operation_token, left_node, right_node, NULL);
    }

    return left_node;
}

Node* parser_parse(Parser* parser)
{
    return parser_parse_expression(parser);
}

void parser_free(Node* node)
{
    if (node == NULL)
        return;

    parser_free(node->left_node);
    parser_free(node->right_node);
    free(node->value);
    free(node);
}

void parser_init(Parser* parser, const TokenList tokens)
{
    parser->tokens = tokens;
    parser->current_token = 0;
}
