//
// Created by kokolor on 24/05/25.
//

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include "errors.h"
#include "parser.h"

Node* parser_create_node(const TokenType operation, Node* left_node, Node* right_node, const char* value, int line)
{
    Node* node = (Node*)malloc(sizeof(Node));
    if (node == NULL)
        error(line, "Cannot allocate node memory");

    node->operation_token = operation;
    node->left_node = left_node;
    node->right_node = right_node;
    node->value = value ? strdup(value) : NULL;
    node->data_type = NULL;
    node->line = line;

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
    const Token current_token = parser->tokens.tokens[parser->current_token];
    const int line = current_token.line;

    if (current_token.type == t_number)
    {
        parser_advance(parser);

        return parser_create_node(t_number, NULL, NULL, current_token.value, line);
    }

    error(line, "Unexpected token in factor");
    return NULL;
}

Node* parser_parse_term(Parser* parser)
{
    Node* left_node = parser_parse_factor(parser);

    while (parser_get_current_token_type(parser) == t_star || parser_get_current_token_type(parser) == t_slash)
    {
        const Token operation_token = parser->tokens.tokens[parser->current_token];
        const TokenType operation_token_type = operation_token.type;
        const int line = operation_token.line;
        parser_advance(parser);
        Node* right_node = parser_parse_factor(parser);
        left_node = parser_create_node(operation_token_type, left_node, right_node, NULL, line);
    }

    return left_node;
}

Node* parser_parse_expression(Parser* parser)
{
    Node* left_node = parser_parse_term(parser);

    while (parser_get_current_token_type(parser) == t_plus || parser_get_current_token_type(parser) == t_minus)
    {
        const Token operation_token = parser->tokens.tokens[parser->current_token];
        const TokenType operation_token_type = operation_token.type;
        const int line = operation_token.line;
        parser_advance(parser);
        Node* right_node = parser_parse_term(parser);
        left_node = parser_create_node(operation_token_type, left_node, right_node, NULL, line);
    }

    return left_node;
}

Node* parser_parse_statement(Parser* parser)
{
    switch (parser_get_current_token_type(parser))
    {
    case t_var:
        return parser_parse_variable_declaration(parser);
    default:
        return parser_parse_expression(parser);
    }
}

Node* parser_parse_variable_declaration(Parser* parser)
{
    const Token variable_token = parser->tokens.tokens[parser->current_token];
    const int line = variable_token.line;

    parser_advance(parser);

    if (parser_get_current_token_type(parser) != t_identifier) {}

    const char* variable_name = parser->tokens.tokens[parser->current_token].value;
    parser_advance(parser);

    if (parser_get_current_token_type(parser) != t_colon)
        error(line, "Expected ':' after variable name");

    parser_advance(parser);

    if (parser_get_current_token_type(parser) != t_primitive_type)
        error(line, "Expected data type after colon");

    const char* data_type = parser->tokens.tokens[parser->current_token].value;
    parser_advance(parser);

    if (parser_get_current_token_type(parser) != t_equal)
        error(line, "Expected '=' after variable name");

    parser_advance(parser);

    Node* expression_node = parser_parse_expression(parser);

    if (parser_get_current_token_type(parser) != t_semicolon)
        error(line, "Expected ';' after variable declaration");

    parser_advance(parser);

    Node* node = parser_create_node(t_var, NULL, expression_node, NULL, line);
    node->type = n_variable_declaration;
    node->variable_name = strdup(variable_name);
    node->data_type = (char*)data_type;

    return node;
}

Node* parser_parse(Parser* parser)
{
    Node* program_node = malloc(sizeof(Node));
    if (!program_node)
        error(parser->tokens.tokens[parser->current_token].line, "Cannot allocate program node");

    program_node->type = n_program;
    program_node->operation_token = t_eof;
    program_node->value = NULL;
    program_node->variable_name = NULL;
    program_node->left_node = NULL;
    program_node->right_node = NULL;
    program_node->statements = NULL;
    program_node->statements_count = 0;

    while (parser_get_current_token_type(parser) != t_eof)
    {
        Node* statement = parser_parse_statement(parser);

        if (statement != NULL)
        {
            program_node->statements = realloc(program_node->statements,
                                               sizeof(Node*) * (program_node->statements_count + 1));
            program_node->statements[program_node->statements_count++] = statement;
        }
    }

    return program_node;
}

void parser_free(Node* node)
{
    if (node == NULL)
        return;

    if (node->type == n_program)
    {
        for (int i = 0; i < node->statements_count; i++)
            parser_free(node->statements[i]);
        free(node->statements);
    }
    else if (node->type == n_variable_declaration)
    {
        free(node->variable_name);
        parser_free(node->right_node);
    }
    else
    {
        parser_free(node->left_node);
        parser_free(node->right_node);
        if (node->value)
            free(node->value);
    }

    free(node);
}

void parser_init(Parser* parser, const TokenList tokens)
{
    parser->tokens = tokens;
    parser->current_token = 0;
}
