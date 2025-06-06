//
// Created by kokolor on 25/05/25.
//

#include <stdlib.h>
#include <llvm-c-18/llvm-c/Core.h>
#include <llvm-c-18/llvm-c/Types.h>
#include "errors.h"
#include "codegen.h"

LLVMValueRef codegen_generate_expression(const Codegen* codegen, const Node* node)
{
    if (node == NULL)
        return NULL;

    switch (node->operation_token)
    {
    case t_number:
        const int value = atoi(node->value);

        return LLVMConstInt(LLVMInt32TypeInContext(codegen->context), value, 0);
    case t_plus:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node);

            return LLVMBuildAdd(codegen->builder, left, right, "addtmp");
        }
    case t_minus:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node);

            return LLVMBuildSub(codegen->builder, left, right, "subtmp");
        }
    case t_star:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node);

            return LLVMBuildMul(codegen->builder, left, right, "multmp");
        }
    case t_slash:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node);

            return LLVMBuildSDiv(codegen->builder, left, right, "divtmp");
        }
    default:
        error(0, "Unsupported operation");
    }

    return NULL;
}

LLVMValueRef codegen_generate_statement(const Codegen* codegen, const Node* node)
{
    if (node == NULL)
        return NULL;

    switch (node->type)
    {
    case n_expression:
        return codegen_generate_expression(codegen, node);
    case n_variable_declaration:
        LLVMValueRef alloca = LLVMBuildAlloca(codegen->builder, LLVMInt32TypeInContext(codegen->context),
                                              node->variable_name);
        LLVMValueRef value = codegen_generate_expression(codegen, node->right_node);

        return LLVMBuildStore(codegen->builder, value, alloca);
    case n_program:
        LLVMValueRef result = NULL;

        for (int i = 0; i < node->statements_count; i++)
        {
            result = codegen_generate_statement(codegen, node->statements[i]);
        }

        return result;
    }
}

void codegen_free(const Codegen* codegen)
{
    LLVMDisposeBuilder(codegen->builder);
    LLVMDisposeModule(codegen->module);
    LLVMContextDispose(codegen->context);
}

void codegen_init(Codegen* codegen)
{
    codegen->context = LLVMContextCreate();
    codegen->builder = LLVMCreateBuilderInContext(codegen->context);
    codegen->module = LLVMModuleCreateWithNameInContext("main_module", codegen->context);
}
