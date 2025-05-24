#include <stdio.h>
#include "lexer.h"

int main(void)
{
    Lexer lexer;
    lexer_init(&lexer, "42 + 3");
    lexer_scan(&lexer);

    for (int i = 0; i < lexer.tokens.count; i++)
    {
        printf("%s (%s) ", lexer_token_type_to_string(lexer.tokens.tokens[i].type), lexer.tokens.tokens[i].value);
    }

    lexer_free(&lexer);

    return 0;
}
