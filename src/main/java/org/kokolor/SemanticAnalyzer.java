package org.kokolor;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.util.List;

public class SemanticAnalyzer extends MajusBaseVisitor<MajusType> {
    private final SymbolTable symbolTable;
    private final MajusErrorHandler errorHandler;
    private FunctionSymbol currentFunction;
    private boolean hasReturn;

    public SemanticAnalyzer(SymbolTable symbolTable, MajusErrorHandler errorHandler) {
        this.symbolTable = symbolTable;
        this.errorHandler = errorHandler;
        this.currentFunction = null;
        this.hasReturn = false;
    }

    private int getLine(ParseTree context) {
        if (context instanceof ParserRuleContext) {
            return ((ParserRuleContext) context).getStart().getLine();
        }

        return 0;
    }

    private int getColumn(ParseTree context) {
        if (context instanceof ParserRuleContext) {
            return ((ParserRuleContext) context).getStart().getCharPositionInLine() + 1;
        }

        return 0;
    }

    @Override
    public MajusType visitProgram(MajusParser.ProgramContext context) {
        for (MajusParser.FunctionDeclContext functionContext : context.functionDecl()) {
            collectFunctionSignature(functionContext);
        }

        for (MajusParser.ExternFunctionDeclContext externContext : context.externFunctionDecl()) {
            collectExternFunctionSignature(externContext);
        }

        for (ParseTree child : context.children) {
            visit(child);
        }

        return MajusType.VOID;
    }

    private void collectFunctionSignature(MajusParser.FunctionDeclContext context) {
        String funcName = context.IDENTIFIER().getText();
        MajusType returnType = MajusType.fromString(context.type().getText());
        FunctionSymbol funcSymbol = new FunctionSymbol(funcName, returnType, getLine(context), getColumn(context));

        if (context.parameterList() != null) {
            for (MajusParser.ParameterContext paramContext : context.parameterList().parameter()) {
                String paramName = paramContext.IDENTIFIER().getText();
                MajusType paramType = MajusType.fromString(paramContext.type().getText());
                VariableSymbol paramSymbol = new VariableSymbol(paramName, paramType, false, getLine(paramContext), getColumn(paramContext));
                funcSymbol.addParameter(paramSymbol);
            }
        }

        if (!symbolTable.define(funcSymbol)) {
            errorHandler.redefinedSymbol(funcName, getLine(context), getColumn(context));
        }
    }

    private void collectExternFunctionSignature(MajusParser.ExternFunctionDeclContext context) {
        String funcName = context.IDENTIFIER().getText();
        MajusType returnType = MajusType.fromString(context.type().getText());
        FunctionSymbol funcSymbol = new FunctionSymbol(funcName, returnType, getLine(context), getColumn(context));

        if (context.parameterList() != null) {
            for (MajusParser.ParameterContext paramContext : context.parameterList().parameter()) {
                String paramName = paramContext.IDENTIFIER().getText();
                MajusType paramType = MajusType.fromString(paramContext.type().getText());
                VariableSymbol paramSymbol = new VariableSymbol(paramName, paramType, false, getLine(paramContext), getColumn(paramContext));
                funcSymbol.addParameter(paramSymbol);
            }
        }

        if (!symbolTable.define(funcSymbol)) {
            errorHandler.redefinedSymbol(funcName, getLine(context), getColumn(context));
        }
    }

    @Override
    public MajusType visitFunctionDecl(MajusParser.FunctionDeclContext context) {
        String funcName = context.IDENTIFIER().getText();
        Symbol symbol = symbolTable.resolve(funcName);

        if (symbol instanceof FunctionSymbol) {
            currentFunction = (FunctionSymbol) symbol;
            hasReturn = false;
            symbolTable.enterScope(funcName);

            for (VariableSymbol param : currentFunction.getParameters()) {
                symbolTable.define(param);
            }

            for (MajusParser.StatementContext statementContext : context.statement()) {
                visit(statementContext);
            }

            if (currentFunction.getType() != MajusType.VOID && !hasReturn) {
                errorHandler.error(ErrorType.SEMANTIC_ERROR, "Function '" + funcName + "' must return a value", getLine(context), getColumn(context));
            }

            symbolTable.exitScope();
            currentFunction = null;
        }

        return MajusType.VOID;
    }

    @Override
    public MajusType visitVariableDecl(MajusParser.VariableDeclContext context) {
        String variableName = context.IDENTIFIER().getText();
        MajusType declaredType = MajusType.fromString(context.type().getText());
        MajusType expressionType = visit(context.expression());
        if (isAssignableType(declaredType, expressionType)) {
            errorHandler.typeError(declaredType.toString(), expressionType.toString(), getLine(context), getColumn(context));
        }

        VariableSymbol variableSymbol = new VariableSymbol(variableName, declaredType, false, getLine(context), getColumn(context));
        variableSymbol.setInitialized(true);

        if (!symbolTable.define(variableSymbol)) {
            errorHandler.redefinedSymbol(variableName, getLine(context), getColumn(context));
        }

        return declaredType;
    }

    @Override
    public MajusType visitAssignmentStmt(MajusParser.AssignmentStmtContext context) {
        String variableName = context.IDENTIFIER().getText();
        Symbol symbol = symbolTable.resolve(variableName);

        if (symbol == null) {
            errorHandler.undefinedSymbol(variableName, getLine(context), getColumn(context));

            return MajusType.UNKNOWN;
        }

        if (!(symbol instanceof VariableSymbol varSymbol)) {
            errorHandler.error(ErrorType.INVALID_ASSIGNMENT, "'" + variableName + "' is not a variable", getLine(context), getColumn(context));

            return MajusType.UNKNOWN;
        }

        if (varSymbol.isConstant()) {
            errorHandler.constantAssignment(variableName, getLine(context), getColumn(context));

            return MajusType.UNKNOWN;
        }

        MajusType expressionType = visit(context.expression());

        if (expressionType == MajusType.UNKNOWN || varSymbol.getType() != expressionType) {
            errorHandler.typeError(varSymbol.getType().toString(), expressionType.toString(), getLine(context), getColumn(context));
        }

        varSymbol.setInitialized(true);

        return varSymbol.getType();
    }

    @Override
    public MajusType visitIfStmt(MajusParser.IfStmtContext context) {
        MajusType conditionType = visit(context.expression());

        if (conditionType != MajusType.BOOL) {
            errorHandler.typeError("bool", conditionType.toString(),
                    getLine(context.expression()), getColumn(context.expression()));
        }

        symbolTable.enterScope("if");
        visit(context.statement(0));
        symbolTable.exitScope();

        if (context.statement().size() > 1) {
            symbolTable.enterScope("else");
            visit(context.statement(1));
            symbolTable.exitScope();
        }

        return MajusType.VOID;
    }

    @Override
    public MajusType visitWhileStmt(MajusParser.WhileStmtContext context) {
        MajusType conditionType = visit(context.expression());

        if (conditionType != MajusType.BOOL) {
            errorHandler.typeError("bool", conditionType.toString(), getLine(context.expression()), getColumn(context.expression()));
        }

        symbolTable.enterScope("while");
        visit(context.statement());
        symbolTable.exitScope();

        return MajusType.VOID;
    }

    @Override
    public MajusType visitReturnStmt(MajusParser.ReturnStmtContext context) {
        if (currentFunction == null) {
            errorHandler.error(ErrorType.SEMANTIC_ERROR, "Return statement outside function", getLine(context), getColumn(context));

            return MajusType.UNKNOWN;
        }

        hasReturn = true;

        if (context.expression() != null) {
            MajusType returnType = visit(context.expression());

            if (isAssignableType(currentFunction.getType(), returnType)) {
                errorHandler.typeError(currentFunction.getType().toString(), returnType.toString(), getLine(context), getColumn(context));
            }
        } else if (currentFunction.getType() != MajusType.VOID) {
            errorHandler.error(ErrorType.SEMANTIC_ERROR, "Function must return a value of type " + currentFunction.getType(), getLine(context), getColumn(context));
        }

        return currentFunction.getType();
    }

    @Override
    public MajusType visitBlock(MajusParser.BlockContext context) {
        symbolTable.enterScope("block");

        for (MajusParser.StatementContext stmtcontext : context.statement()) {
            visit(stmtcontext);
        }

        symbolTable.exitScope();

        return MajusType.VOID;
    }

    @Override
    public MajusType visitBinaryExpr(MajusParser.BinaryExprContext context) {
        MajusType leftType = visit(context.expression(0));
        MajusType rightType = visit(context.expression(1));
        String operator = context.getChild(1).getText();

        return checkBinaryOperation(leftType, rightType, operator, getLine(context), getColumn(context));
    }

    @Override
    public MajusType visitComparisonExpr(MajusParser.ComparisonExprContext context) {
        MajusType leftType = visit(context.expression(0));
        MajusType rightType = visit(context.expression(1));
        String operator = context.getChild(1).getText();

        if (!areComparableTypes(leftType, rightType)) {
            errorHandler.incompatibleTypes(leftType.toString(), rightType.toString(), operator, getLine(context), getColumn(context));
        }

        return MajusType.BOOL;
    }

    @Override
    public MajusType visitLogicalExpr(MajusParser.LogicalExprContext context) {
        MajusType leftType = visit(context.expression(0));
        MajusType rightType = visit(context.expression(1));
        String operator = context.getChild(1).getText();

        if (leftType != MajusType.BOOL) {
            errorHandler.typeError("bool", leftType.toString(), getLine(context.expression(0)), getColumn(context.expression(0)));
        }
        if (rightType != MajusType.BOOL) {
            errorHandler.typeError("bool", rightType.toString(), getLine(context.expression(1)), getColumn(context.expression(1)));
        }

        return MajusType.BOOL;
    }

    @Override
    public MajusType visitUnaryExpr(MajusParser.UnaryExprContext context) {
        MajusType expressionType = visit(context.expression());
        String operator = context.getChild(0).getText();

        if (operator.equals("!")) {
            if (expressionType != MajusType.BOOL) {
                errorHandler.typeError("bool", expressionType.toString(), getLine(context), getColumn(context));
            }

            return MajusType.BOOL;
        } else if (operator.equals("-")) {
            if (!expressionType.isNumeric()) {
                errorHandler.incompatibleTypes("numeric", expressionType.toString(), "unary -", getLine(context), getColumn(context));
            }

            return expressionType;
        }

        return MajusType.UNKNOWN;
    }

    @Override
    public MajusType visitFuncCallExpr(MajusParser.FuncCallExprContext context) {
        return visitFunctionCall(context.functionCall());
    }

    @Override
    public MajusType visitFunctionCall(MajusParser.FunctionCallContext context) {
        String funcName = context.IDENTIFIER().getText();
        Symbol symbol = symbolTable.resolve(funcName);

        if (symbol == null) {
            errorHandler.undefinedSymbol(funcName, getLine(context), getColumn(context));

            return MajusType.UNKNOWN;
        }

        if (!(symbol instanceof FunctionSymbol funcSymbol)) {
            errorHandler.error(ErrorType.FUNCTION_NOT_FOUND, "'" + funcName + "' is not a function", getLine(context), getColumn(context));

            return MajusType.UNKNOWN;
        }

        int expectedArgs = funcSymbol.getParameters().size();
        int actualArgs = 0;

        if (context.argumentList() != null) {
            actualArgs = context.argumentList().expression().size();
        }

        if (expectedArgs != actualArgs) {
            errorHandler.wrongArgumentCount(funcName, expectedArgs, actualArgs, getLine(context), getColumn(context));

            return funcSymbol.getType();
        }

        if (context.argumentList() != null) {
            List<MajusParser.ExpressionContext> args = context.argumentList().expression();

            for (int i = 0; i < args.size(); i++) {
                MajusType argType = visit(args.get(i));
                MajusType expectedType = funcSymbol.getParameters().get(i).getType();

                if (isAssignableType(expectedType, argType)) {
                    errorHandler.typeError(expectedType.toString(), argType.toString(), getLine(args.get(i)), getColumn(args.get(i)));
                }
            }
        }

        return funcSymbol.getType();
    }

    @Override
    public MajusType visitExternFunctionDecl(MajusParser.ExternFunctionDeclContext context) {
        return MajusType.VOID;
    }

    @Override
    public MajusType visitIdentifierExpr(MajusParser.IdentifierExprContext context) {
        String varName = context.IDENTIFIER().getText();
        Symbol symbol = symbolTable.resolve(varName);

        if (symbol == null) {
            errorHandler.undefinedSymbol(varName, getLine(context), getColumn(context));
            return MajusType.UNKNOWN;
        }

        if (symbol instanceof VariableSymbol) {
            VariableSymbol varSymbol = (VariableSymbol) symbol;

            if (!varSymbol.isInitialized()) {
                errorHandler.uninitializedVariable(varName, getLine(context), getColumn(context));
            }

            return varSymbol.getType();
        }

        return symbol.getType();
    }

    @Override
    public MajusType visitIntLiteral(MajusParser.IntLiteralContext context) {
        return MajusType.I32;
    }

    @Override
    public MajusType visitFloatLiteral(MajusParser.FloatLiteralContext context) {
        return MajusType.F32;
    }

    @Override
    public MajusType visitStringLiteral(MajusParser.StringLiteralContext context) {
        return MajusType.STRING;
    }

    @Override
    public MajusType visitBoolLiteral(MajusParser.BoolLiteralContext context) {
        return MajusType.BOOL;
    }

    @Override
    public MajusType visitCastExpr(MajusParser.CastExprContext context) {
        MajusType sourceType = visit(context.expression());
        MajusType targetType = MajusType.fromString(context.type().getText());

        if (!isExplicitCastAllowed(sourceType, targetType)) {
            errorHandler.incompatibleTypes(sourceType.toString(), targetType.toString(), "as", getLine(context), getColumn(context));
        }

        return targetType;
    }

    private boolean isAssignableType(MajusType target, MajusType source) {
        if (target == source) return false;
        if (source == MajusType.UNKNOWN) return true;
        if (target == MajusType.I64 && source == MajusType.I32) return false;
        if (target == MajusType.F64 && source == MajusType.F32) return false;
        if (target == MajusType.F32 && source.isInteger()) return false;

        return target != MajusType.F64 || !source.isNumeric();
    }

    private boolean areComparableTypes(MajusType left, MajusType right) {
        if (left == right) return true;

        return left.isNumeric() && right.isNumeric();
    }

    private boolean isExplicitCastAllowed(MajusType source, MajusType target) {
        if (source == MajusType.UNKNOWN || target == MajusType.UNKNOWN) return true;
        if (source == target) return true;
        // if (source == MajusType.BOOL && target.isInteger()) return true;
        // if (source.isInteger() && target == MajusType.BOOL) return true;

        return source.isNumeric() && target.isNumeric();
    }


    private MajusType checkBinaryOperation(MajusType left, MajusType right, String operator, int line, int col) {
        if (operator.equals("+") || operator.equals("-") || operator.equals("*") || operator.equals("/") || operator.equals("%")) {

            if (!left.isNumeric() || !right.isNumeric()) {
                errorHandler.incompatibleTypes(left.toString(), right.toString(), operator, line, col);

                return MajusType.UNKNOWN;
            }

            if (left == MajusType.F64 || right == MajusType.F64) return MajusType.F64;
            if (left == MajusType.F32 || right == MajusType.F32) return MajusType.F32;
            if (left == MajusType.I64 || right == MajusType.I64) return MajusType.I64;

            return MajusType.I32;
        }

        return MajusType.UNKNOWN;
    }
}