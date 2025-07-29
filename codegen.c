//
// Created by kokolor on 25/05/25.
//

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <llvm-c/Core.h>
#include <llvm-c/Types.h>
#include "errors.h"
#include "codegen.h"

LLVMTypeRef type_to_llvm(const Codegen* codegen, const char* type, int line)
{
    if (!strcmp(type, "byte"))
        return LLVMInt8TypeInContext(codegen->context);
    if (!strcmp(type, "hword"))
        return LLVMInt16TypeInContext(codegen->context);
    if (!strcmp(type, "word"))
        return LLVMInt32TypeInContext(codegen->context);
    if (!strcmp(type, "qword"))
        return LLVMInt64TypeInContext(codegen->context);

    if (!strcmp(type, "ubyte"))
        return LLVMInt8TypeInContext(codegen->context);
    if (!strcmp(type, "uhword"))
        return LLVMInt16TypeInContext(codegen->context);
    if (!strcmp(type, "uword"))
        return LLVMInt32TypeInContext(codegen->context);
    if (!strcmp(type, "uqword"))
        return LLVMInt64TypeInContext(codegen->context);

    error(line, "Unknown type");
}

LLVMValueRef codegen_generate_expression(const Codegen* codegen, const Node* node, const char* type)
{
    if (node == NULL)
        return NULL;

    switch (node->operation_token)
    {
    case t_number:
        const int value = atoi(node->value);
        return LLVMConstInt(type_to_llvm(codegen, type, node->line), value, 0);
    case t_plus:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node, type);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node, type);

            return LLVMBuildAdd(codegen->builder, left, right, "addtmp");
        }
    case t_minus:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node, type);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node, type);

            return LLVMBuildSub(codegen->builder, left, right, "subtmp");
        }
    case t_star:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node, type);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node, type);

            return LLVMBuildMul(codegen->builder, left, right, "multmp");
        }
    case t_slash:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node, type);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node, type);

            return LLVMBuildSDiv(codegen->builder, left, right, "divtmp");
        }
    case t_not_equal:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node, type);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node, type);

            return LLVMBuildICmp(codegen->builder, LLVMIntNE, left, right, "netmp");
        }
    case t_less:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node, type);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node, type);

            return LLVMBuildICmp(codegen->builder, LLVMIntSLT, left, right, "lttmp");
        }
    case t_less_equal:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node, type);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node, type);

            return LLVMBuildICmp(codegen->builder, LLVMIntSLE, left, right, "letmp");
        }
    case t_greater:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node, type);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node, type);

            return LLVMBuildICmp(codegen->builder, LLVMIntSGT, left, right, "gttmp");
        }
    case t_greater_equal:
        {
            LLVMValueRef left = codegen_generate_expression(codegen, node->left_node, type);
            LLVMValueRef right = codegen_generate_expression(codegen, node->right_node, type);

            return LLVMBuildICmp(codegen->builder, LLVMIntSGE, left, right, "getmp");
        }
    default:
        error(node->line, "Unsupported operation");
    }

    return NULL;
}

LLVMValueRef codegen_generate_statement(const Codegen* codegen, const Node* node)
{
    if (node == NULL)
        return NULL;

    switch (node->type)
    {
    case n_variable_declaration:
        LLVMValueRef alloca = LLVMBuildAlloca(codegen->builder, type_to_llvm(codegen, node->data_type, node->line),
                                              node->variable_name);
        LLVMValueRef value = codegen_generate_expression(codegen, node->right_node, node->data_type);

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
