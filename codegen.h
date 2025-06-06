//
// Created by kokolor on 25/05/25.
//

#ifndef CODEGEN_H
#define CODEGEN_H

#include "parser.h"

typedef struct
{
    LLVMContextRef context;
    LLVMBuilderRef builder;
    LLVMModuleRef module;
} Codegen;

LLVMValueRef codegen_generate_expression(const Codegen* codegen, const Node* node);

LLVMValueRef codegen_generate_statement(const Codegen* codegen, const Node* node);

void codegen_free(const Codegen* codegen);

void codegen_init(Codegen* codegen);

#endif //CODEGEN_H
