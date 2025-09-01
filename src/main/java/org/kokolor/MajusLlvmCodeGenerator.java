package org.kokolor;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.llvm.LLVM.*;

import java.util.*;

import static org.bytedeco.llvm.global.LLVM.*;

public class MajusLlvmCodeGenerator extends MajusBaseVisitor<LLVMValueRef> {
    private final LLVMContextRef contextRef;
    private final LLVMModuleRef module;
    private final LLVMBuilderRef builder;
    private final Deque<Map<String, LLVMValueRef>> scopes = new ArrayDeque<>();
    private final Map<String, LLVMTypeRef> functionTypes = new HashMap<>();
    private final SymbolTable symbolTable;

    public MajusLlvmCodeGenerator(SymbolTable symbolTable) {
        this.contextRef = LLVMContextCreate();
        this.module = LLVMModuleCreateWithNameInContext("majus_module", contextRef);
        this.builder = LLVMCreateBuilderInContext(contextRef);

        scopes.push(new HashMap<>());
        this.symbolTable = symbolTable;
    }

    public String getIR() {
        BytePointer error = new BytePointer((Pointer) null);

        if (LLVMVerifyModule(module, LLVMPrintMessageAction, error) != 0) {
            String message = error.getString();
            LLVMDisposeMessage(error);

            throw new RuntimeException("LLVM module verification failed: " + message);
        }

        LLVMDisposeMessage(error);

        BytePointer ir = LLVMPrintModuleToString(module);
        String result = ir.getString();
        LLVMDisposeMessage(ir);

        return result;
    }

    private void pushScope() {
        scopes.push(new HashMap<>());
    }

    private void popScope() {
        scopes.pop();
    }

    private void defineLocal(String name, LLVMValueRef alloca) {
        assert scopes.peek() != null;
        scopes.peek().put(name, alloca);
    }

    private LLVMValueRef resolveLocal(String name) {
        for (Map<String, LLVMValueRef> scope : scopes) {
            if (scope.containsKey(name)) return scope.get(name);
        }

        throw new RuntimeException("Local variable not found: " + name);
    }

    private LLVMTypeRef mapType(String typeName) {
        return switch (typeName) {
            case "i8", "u8" -> LLVMInt8TypeInContext(contextRef);
            case "i16", "u16" -> LLVMInt16TypeInContext(contextRef);
            case "i32", "u32" -> LLVMInt32TypeInContext(contextRef);
            case "i64", "u64" -> LLVMInt64TypeInContext(contextRef);
            case "f32" -> LLVMFloatTypeInContext(contextRef);
            case "f64" -> LLVMDoubleTypeInContext(contextRef);
            case "bool" -> LLVMInt1TypeInContext(contextRef);
            case "void" -> LLVMVoidTypeInContext(contextRef);
            default -> throw new UnsupportedOperationException("Unsupported type: " + typeName);
        };
    }

    private boolean isInteger(LLVMTypeRef type) {
        return LLVMGetTypeKind(type) == LLVMIntegerTypeKind && LLVMGetIntTypeWidth(type) > 1;
    }

    private boolean isBool(LLVMTypeRef type) {
        return LLVMGetTypeKind(type) == LLVMIntegerTypeKind && LLVMGetIntTypeWidth(type) == 1;
    }

    private boolean isFloat32(LLVMTypeRef type) {
        return LLVMGetTypeKind(type) == LLVMFloatTypeKind;
    }

    private boolean isFloat64(LLVMTypeRef type) {
        return LLVMGetTypeKind(type) == LLVMDoubleTypeKind;
    }

    private boolean isFloat(LLVMTypeRef type) {
        return isFloat32(type) || isFloat64(type);
    }

    private LLVMValueRef castToType(LLVMValueRef value, LLVMTypeRef destType) {
        LLVMTypeRef srcType = LLVMTypeOf(value);
        if (srcType.equals(destType)) {
            return value;
        }

        if (isInteger(srcType) && isInteger(destType)) {
            int srcW = LLVMGetIntTypeWidth(srcType);
            int dstW = LLVMGetIntTypeWidth(destType);
            if (srcW == dstW) {
                return value;
            } else if (srcW < dstW) {
                return LLVMBuildSExt(builder, value, destType, "sext");
            } else {
                return LLVMBuildTrunc(builder, value, destType, "trunc");
            }
        }

        if (isFloat(srcType) && isFloat(destType)) {
            if (isFloat32(srcType) && isFloat64(destType)) {
                return LLVMBuildFPExt(builder, value, destType, "fpext");
            } else if (isFloat64(srcType) && isFloat32(destType)) {
                return LLVMBuildFPTrunc(builder, value, destType, "fptrunc");
            } else {
                return value;
            }
        }

        if (isInteger(srcType) && isFloat(destType)) {
            return LLVMBuildSIToFP(builder, value, destType, "sitofp");
        }

        if (isFloat(srcType) && isInteger(destType)) {
            return LLVMBuildFPToSI(builder, value, destType, "fptosi");
        }

        return value;
    }

    @Override
    public LLVMValueRef visitProgram(MajusParser.ProgramContext context) {
        for (MajusParser.FunctionDeclContext function : context.functionDecl()) {
            visit(function);
        }

        return null;
    }

    @Override
    public LLVMValueRef visitFunctionDecl(MajusParser.FunctionDeclContext context) {
        String functionName = context.IDENTIFIER().getText();
        LLVMTypeRef returnType = mapType(context.type().getText());

        List<LLVMTypeRef> paramTypes = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();
        if (context.parameterList() != null) {
            for (MajusParser.ParameterContext p : context.parameterList().parameter()) {
                paramTypes.add(mapType(p.type().getText()));
                paramNames.add(p.IDENTIFIER().getText());
            }
        }

        PointerPointer<LLVMTypeRef> paramsArray = new PointerPointer<>(paramTypes.size());
        for (int i = 0; i < paramTypes.size(); i++) {
            paramsArray.put(i, paramTypes.get(i));
        }

        LLVMTypeRef functionType = LLVMFunctionType(returnType, paramsArray, paramTypes.size(), 0);
        LLVMValueRef function = LLVMAddFunction(module, functionName, functionType);
        functionTypes.put(functionName, functionType);

        for (int i = 0; i < paramNames.size(); i++) {
            LLVMValueRef param = LLVMGetParam(function, i);
            String pNameStr = paramNames.get(i);
            LLVMSetValueName2(param, new BytePointer(pNameStr), pNameStr.length());
        }

        LLVMBasicBlockRef entry = LLVMAppendBasicBlock(function, "entry");
        LLVMPositionBuilderAtEnd(builder, entry);

        pushScope();

        for (int i = 0; i < paramNames.size(); i++) {
            String paramName = paramNames.get(i);
            LLVMValueRef param = LLVMGetParam(function, i);
            LLVMTypeRef pty = paramTypes.get(i);
            LLVMValueRef alloca = LLVMBuildAlloca(builder, pty, paramName);
            LLVMBuildStore(builder, param, alloca);

            defineLocal(paramName, alloca);
        }

        for (MajusParser.StatementContext statement : context.statement()) {
            visit(statement);
        }

        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null && LLVMGetTypeKind(LLVMGetReturnType(functionType)) == LLVMVoidTypeKind) {
            LLVMBuildRetVoid(builder);
        }

        popScope();

        return function;
    }

    @Override
    public LLVMValueRef visitBlock(MajusParser.BlockContext context) {
        pushScope();

        for (MajusParser.StatementContext s : context.statement()) {
            visit(s);
        }

        popScope();

        return null;
    }

    @Override
    public LLVMValueRef visitVariableDecl(MajusParser.VariableDeclContext context) {
        String name = context.IDENTIFIER().getText();
        LLVMTypeRef type = mapType(context.type().getText());
        LLVMValueRef init = visit(context.expression());
        LLVMValueRef alloca = LLVMBuildAlloca(builder, type, name);
        LLVMValueRef initCasted = castToType(init, type);
        LLVMBuildStore(builder, initCasted, alloca);
        defineLocal(name, alloca);

        return alloca;
    }

    @Override
    public LLVMValueRef visitAssignmentStmt(MajusParser.AssignmentStmtContext context) {
        String name = context.IDENTIFIER().getText();
        LLVMValueRef alloca = resolveLocal(name);
        LLVMValueRef value = visit(context.expression());
        LLVMTypeRef destType = LLVMGetAllocatedType(alloca);
        LLVMValueRef casted = castToType(value, destType);
        LLVMBuildStore(builder, casted, alloca);

        return null;
    }

    @Override
    public LLVMValueRef visitIfStmt(MajusParser.IfStmtContext context) {
        LLVMValueRef conditionValue = visit(context.expression());
        LLVMTypeRef conditionType = LLVMTypeOf(conditionValue);

        if (!isBool(conditionType)) {
            throw new UnsupportedOperationException("The if condition must be bool (i1) in this version.");
        }

        LLVMBasicBlockRef currentBlock = LLVMGetInsertBlock(builder);
        LLVMValueRef currentFunction = LLVMGetBasicBlockParent(currentBlock);
        LLVMBasicBlockRef thenBB = LLVMAppendBasicBlock(currentFunction, "then");
        LLVMBasicBlockRef elseBB = LLVMAppendBasicBlock(currentFunction, "else");
        LLVMBasicBlockRef mergeBB = LLVMAppendBasicBlock(currentFunction, "endif");

        LLVMBuildCondBr(builder, conditionValue, thenBB, elseBB);

        LLVMPositionBuilderAtEnd(builder, thenBB);
        visit(context.statement(0));

        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildBr(builder, mergeBB);
        }

        LLVMPositionBuilderAtEnd(builder, elseBB);

        if (context.statement().size() > 1) {
            visit(context.statement(1));
        }

        if (LLVMGetBasicBlockTerminator(LLVMGetInsertBlock(builder)) == null) {
            LLVMBuildBr(builder, mergeBB);
        }

        LLVMPositionBuilderAtEnd(builder, mergeBB);

        return null;
    }

    @Override
    public LLVMValueRef visitWhileStmt(MajusParser.WhileStmtContext context) {
        throw new UnsupportedOperationException("While loop: not implemented yet.");
    }

    @Override
    public LLVMValueRef visitForStmt(MajusParser.ForStmtContext context) {
        throw new UnsupportedOperationException("For loop: not implemented yet.");
    }

    @Override
    public LLVMValueRef visitReturnStmt(MajusParser.ReturnStmtContext context) {
        if (context.expression() != null) {
            LLVMValueRef visit = visit(context.expression());
            LLVMBasicBlockRef bb = LLVMGetInsertBlock(builder);
            LLVMValueRef function = LLVMGetBasicBlockParent(bb);
            LLVMTypeRef functionType = LLVMTypeOf(function);

            if (LLVMGetTypeKind(functionType) == LLVMFunctionTypeKind) {
                LLVMTypeRef returnType = LLVMGetReturnType(functionType);
                visit = castToType(visit, returnType);
            }

            return LLVMBuildRet(builder, visit);
        } else {
            return LLVMBuildRetVoid(builder);
        }
    }

    @Override
    public LLVMValueRef visitExpressionStmt(MajusParser.ExpressionStmtContext context) {
        visit(context.expression());

        return null;
    }

    @Override
    public LLVMValueRef visitIdentifierExpr(MajusParser.IdentifierExprContext context) {
        String name = context.IDENTIFIER().getText();
        LLVMValueRef alloca = resolveLocal(name);

        return LLVMBuildLoad2(builder, LLVMGetAllocatedType(alloca), alloca, name + "_val");
    }

    @Override
    public LLVMValueRef visitIntLiteral(MajusParser.IntLiteralContext context) {
        long integerValue = Long.parseLong(context.INTEGER_LITERAL().getText());

        return LLVMConstInt(LLVMInt32TypeInContext(contextRef), integerValue, 1);
    }

    @Override
    public LLVMValueRef visitBoolLiteral(MajusParser.BoolLiteralContext context) {
        boolean booleanValue = Boolean.parseBoolean(context.BOOLEAN_LITERAL().getText());

        return LLVMConstInt(LLVMInt1TypeInContext(contextRef), booleanValue ? 1 : 0, 0);
    }

    @Override
    public LLVMValueRef visitFloatLiteral(MajusParser.FloatLiteralContext context) {
        float floatValue = Float.parseFloat(context.FLOAT_LITERAL().getText());

        return LLVMConstReal(LLVMFloatTypeInContext(contextRef), floatValue);
    }

    @Override
    public LLVMValueRef visitStringLiteral(MajusParser.StringLiteralContext context) {
        throw new UnsupportedOperationException("String is not supported for now.");
    }

    @Override
    public LLVMValueRef visitParenExpr(MajusParser.ParenExprContext context) {
        return visit(context.expression());
    }

    @Override
    public LLVMValueRef visitUnaryExpr(MajusParser.UnaryExprContext context) {
        String operator = context.getChild(0).getText();
        LLVMValueRef valueRef = visit(context.expression());

        if ("-".equals(operator)) {
            LLVMTypeRef type = LLVMTypeOf(valueRef);

            if (isInteger(type)) {
                return LLVMBuildNeg(builder, valueRef, "neg");
            } else if (isFloat(type)) {
                return LLVMBuildFNeg(builder, valueRef, "fneg");
            } else {
                throw new UnsupportedOperationException("Unary - only supported for numeric types for now.");
            }
        } else if ("!".equals(operator)) {
            LLVMTypeRef type = LLVMTypeOf(valueRef);

            if (!isBool(type)) {
                throw new UnsupportedOperationException("Logical ! only supported for bool for now.");
            }

            LLVMValueRef one = LLVMConstInt(type, 1, 0);
            return LLVMBuildXor(builder, valueRef, one, "not");
        }

        throw new UnsupportedOperationException("Unsupported unary operator: " + operator);
    }

    @Override
    public LLVMValueRef visitBinaryExpr(MajusParser.BinaryExprContext context) {
        LLVMValueRef left = visit(context.expression(0));
        LLVMValueRef right = visit(context.expression(1));
        String operator = context.getChild(1).getText();
        LLVMTypeRef leftType = LLVMTypeOf(left);
        LLVMTypeRef rightType = LLVMTypeOf(right);

        if (isInteger(leftType) && isInteger(rightType)) {
            return switch (operator) {
                case "+" -> LLVMBuildAdd(builder, left, right, "add");
                case "-" -> LLVMBuildSub(builder, left, right, "sub");
                case "*" -> LLVMBuildMul(builder, left, right, "mul");
                case "/" -> LLVMBuildSDiv(builder, left, right, "sdiv");
                case "%" -> LLVMBuildSRem(builder, left, right, "srem");
                default -> throw new UnsupportedOperationException("Unsupported i32 binary operator: " + operator);
            };
        } else if (isFloat32(leftType) && isFloat32(rightType)) {
            return switch (operator) {
                case "+" -> LLVMBuildFAdd(builder, left, right, "fadd");
                case "-" -> LLVMBuildFSub(builder, left, right, "fsub");
                case "*" -> LLVMBuildFMul(builder, left, right, "fmul");
                case "/" -> LLVMBuildFDiv(builder, left, right, "fdiv");
                default -> throw new UnsupportedOperationException("Unsupported f32 binary operator: " + operator);
            };
        }

        throw new UnsupportedOperationException("Unsupported type combination for " + operator);
    }

    @Override
    public LLVMValueRef visitComparisonExpr(MajusParser.ComparisonExprContext context) {
        LLVMValueRef left = visit(context.expression(0));
        LLVMValueRef right = visit(context.expression(1));
        String operator = context.getChild(1).getText();
        LLVMTypeRef leftType = LLVMTypeOf(left);
        LLVMTypeRef rightType = LLVMTypeOf(right);

        if (isInteger(leftType) && isInteger(rightType)) {
            int pred = switch (operator) {
                case "<" -> LLVMIntSLT;
                case "<=" -> LLVMIntSLE;
                case ">" -> LLVMIntSGT;
                case ">=" -> LLVMIntSGE;
                case "==" -> LLVMIntEQ;
                case "!=" -> LLVMIntNE;
                default -> throw new UnsupportedOperationException("Unsupported integer comparator: " + operator);
            };
            return LLVMBuildICmp(builder, pred, left, right, "icmp");
        } else if (isFloat32(leftType) && isFloat32(rightType)) {
            int pred = switch (operator) {
                case "<" -> LLVMRealOLT;
                case "<=" -> LLVMRealOLE;
                case ">" -> LLVMRealOGT;
                case ">=" -> LLVMRealOGE;
                case "==" -> LLVMRealOEQ;
                case "!=" -> LLVMRealONE;
                default -> throw new UnsupportedOperationException("Unsupported float comparator: " + operator);
            };

            return LLVMBuildFCmp(builder, pred, left, right, "fcmp");
        }

        throw new UnsupportedOperationException("Comparison: unsupported types.");
    }

    @Override
    public LLVMValueRef visitLogicalExpr(MajusParser.LogicalExprContext context) {
        String op = context.getChild(1).getText();
        LLVMValueRef l = visit(context.expression(0));
        LLVMValueRef r = visit(context.expression(1));

        if (!isBool(LLVMTypeOf(l)) || !isBool(LLVMTypeOf(r))) {
            throw new UnsupportedOperationException("&& and || require bool (i1).");
        }

        if ("&&".equals(op)) {
            return LLVMBuildAnd(builder, l, r, "and");
        } else if ("||".equals(op)) {
            return LLVMBuildOr(builder, l, r, "or");
        }

        throw new UnsupportedOperationException("Unsupported logical operator: " + op);
    }

    @Override
    public LLVMValueRef visitFuncCallExpr(MajusParser.FuncCallExprContext context) {
        return visitFunctionCall(context.functionCall());
    }

    @Override
    public LLVMValueRef visitFunctionCall(MajusParser.FunctionCallContext context) {
        String functionName = context.IDENTIFIER().getText();
        LLVMValueRef function = LLVMGetNamedFunction(module, functionName);

        if (function == null) {
            throw new RuntimeException("Undefined function: " + functionName);
        }

        LLVMTypeRef functionType = functionTypes.get(functionName);

        if (functionType == null || LLVMGetTypeKind(functionType) != LLVMFunctionTypeKind) {
            throw new IllegalStateException("The type of '" + functionName + "' is not a function type.");
        }

        List<LLVMValueRef> args = new ArrayList<>();

        if (context.argumentList() != null) {
            for (MajusParser.ExpressionContext e : context.argumentList().expression()) {
                args.add(visit(e));
            }
        }

        int paramCount = (int) LLVMCountParamTypes(functionType);
        PointerPointer<LLVMTypeRef> paramTypes = new PointerPointer<>(paramCount);
        LLVMGetParamTypes(functionType, paramTypes);

        for (int i = 0; i < args.size(); i++) {
            LLVMTypeRef expected = paramTypes.get(LLVMTypeRef.class, i);
            args.set(i, castToType(args.get(i), expected));
        }

        PointerPointer<LLVMValueRef> argsArray = new PointerPointer<>(args.size());

        for (int i = 0; i < args.size(); i++) argsArray.put(i, args.get(i));

        LLVMTypeRef returnType = LLVMGetReturnType(functionType);
        String callName = LLVMGetTypeKind(returnType) == LLVMVoidTypeKind ? "" : "calltmp";

        return LLVMBuildCall2(builder, functionType, function, argsArray, args.size(), callName);
    }
}