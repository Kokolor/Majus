#include <stdio.h>
#include <llvm-c-18/llvm-c/Core.h>
#include <llvm-c-18/llvm-c/Types.h>

#include "codegen.h"
#include "lexer.h"
#include "parser.h"


int main(void)
{
    Lexer lexer;
    lexer_init(&lexer, "42 + 3 * 2");
    lexer_scan(&lexer);

    for (int i = 0; i < lexer.tokens.count; i++)
    {
        printf("%s (%s) ", lexer_token_type_to_string(lexer.tokens.tokens[i].type), lexer.tokens.tokens[i].value);
    }
    printf("\n");

    Parser parser;
    parser_init(&parser, lexer.tokens);

    Node* ast = parser_parse(&parser);
    parser_print_ast(ast, 0);

    Codegen codegen;
    codegen_init(&codegen);

    LLVMTypeRef main_type = LLVMFunctionType(LLVMInt32TypeInContext(codegen.context), NULL, 0, 0);
    LLVMValueRef main_func = LLVMAddFunction(codegen.module, "main", main_type);
    LLVMBasicBlockRef entry = LLVMAppendBasicBlockInContext(codegen.context, main_func, "entry");
    LLVMPositionBuilderAtEnd(codegen.builder, entry);
    LLVMValueRef result = codegen_expression(&codegen, ast);
    LLVMBuildRet(codegen.builder, result);
    LLVMDumpModule(codegen.module);

    parser_free(ast);
    lexer_free(&lexer);
    codegen_free(&codegen);

    return 0;
}
