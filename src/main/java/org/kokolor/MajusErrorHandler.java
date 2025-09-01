package org.kokolor;

import java.util.*;

enum ErrorType {
    SYNTAX_ERROR("Syntax Error"),
    SEMANTIC_ERROR("Semantic Error"),
    TYPE_ERROR("Type Error"),
    UNDEFINED_SYMBOL("Undefined Symbol"),
    REDEFINED_SYMBOL("Symbol Redefinition"),
    INCOMPATIBLE_TYPES("Incompatible Types"),
    FUNCTION_NOT_FOUND("Function Not Found"),
    WRONG_ARGUMENT_COUNT("Wrong Argument Count"),
    INVALID_ASSIGNMENT("Invalid Assignment"),
    UNREACHABLE_CODE("Unreachable Code"),
    UNINITIALIZED_VARIABLE("Uninitialized Variable"),
    CONSTANT_ASSIGNMENT("Constant Assignment");

    private final String description;

    ErrorType(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return description;
    }
}

class MajusError {
    private final ErrorType type;
    private final String message;
    private final int line;
    private final int column;
    private final String filename;

    public MajusError(ErrorType type, String message, int line, int column) {
        this(type, message, line, column, null);
    }

    public MajusError(ErrorType type, String message, int line, int column, String filename) {
        this.type = type;
        this.message = message;
        this.line = line;
        this.column = column;
        this.filename = filename;
    }

    public ErrorType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getFilename() {
        return filename;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();

        if (filename != null) {
            stringBuilder.append(filename).append(":");
        }

        stringBuilder.append(line).append(":").append(column).append(": ");
        stringBuilder.append("error: ").append(type).append(": ").append(message);

        return stringBuilder.toString();
    }

    public String getFormattedError(String[] sourceLines) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(toString()).append("\n");

        if (sourceLines != null && line > 0 && line <= sourceLines.length) {
            String sourceLine = sourceLines[line - 1];
            stringBuilder.append(String.format("%4d | %s\n", line, sourceLine));
            stringBuilder.append("     | ");

            for (int i = 0; i < column - 1 && i < sourceLine.length(); i++) {
                stringBuilder.append(sourceLine.charAt(i) == '\t' ? '\t' : ' ');
            }

            stringBuilder.append("^\n");
        }

        return stringBuilder.toString();
    }
}

class MajusErrorHandler {
    private final List<MajusError> errors;
    private final List<MajusError> warnings;
    private String currentFilename;
    private String[] sourceLines;

    public MajusErrorHandler() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    public void setSource(String source, String filename) {
        this.currentFilename = filename;
        this.sourceLines = source.split("\n");
    }

    public void error(ErrorType type, String message, int line, int column) {
        errors.add(new MajusError(type, message, line, column, currentFilename));
    }

    public void warning(ErrorType type, String message, int line, int column) {
        warnings.add(new MajusError(type, message, line, column, currentFilename));
    }

    public void undefinedSymbol(String symbolName, int line, int column) {
        error(ErrorType.UNDEFINED_SYMBOL, "Symbol '" + symbolName + "' is not defined", line, column);
    }

    public void redefinedSymbol(String symbolName, int line, int column) {
        error(ErrorType.REDEFINED_SYMBOL, "Symbol '" + symbolName + "' is already defined in this scope", line, column);
    }

    public void typeError(String expected, String actual, int line, int column) {
        error(ErrorType.TYPE_ERROR, "Expected type '" + expected + "' but got '" + actual + "'", line, column);
    }

    public void incompatibleTypes(String leftType, String rightType, String operation, int line, int column) {
        error(ErrorType.INCOMPATIBLE_TYPES, "Cannot apply '" + operation + "' to types '" + leftType + "' and '" + rightType + "'", line, column);
    }

    public void functionNotFound(String functionName, int argCount, int line, int column) {
        error(ErrorType.FUNCTION_NOT_FOUND, "No function '" + functionName + "' found that takes " + argCount + " arguments", line, column);
    }

    public void wrongArgumentCount(String functionName, int expected, int actual, int line, int column) {
        error(ErrorType.WRONG_ARGUMENT_COUNT, "Function '" + functionName + "' expects " + expected + " arguments but got " + actual, line, column);
    }

    public void invalidAssignment(String variableName, String reason, int line, int column) {
        error(ErrorType.INVALID_ASSIGNMENT, "Cannot assign to '" + variableName + "': " + reason, line, column);
    }

    public void uninitializedVariable(String variableName, int line, int column) {
        warning(ErrorType.UNINITIALIZED_VARIABLE, "Variable '" + variableName + "' may be used before initialization", line, column);
    }

    public void constantAssignment(String constantName, int line, int column) {
        error(ErrorType.CONSTANT_ASSIGNMENT, "Cannot assign to constant '" + constantName + "'", line, column);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public int getErrorCount() {
        return errors.size();
    }

    public int getWarningCount() {
        return warnings.size();
    }

    public List<MajusError> getErrors() {
        return new ArrayList<>(errors);
    }

    public List<MajusError> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public void clear() {
        errors.clear();
        warnings.clear();
    }

    public void printErrors() {
        if (hasErrors()) {
            System.err.println("=== ERRORS ===");
            for (MajusError error : errors) {
                System.err.println(error.getFormattedError(sourceLines));
            }
        }

        if (hasWarnings()) {
            System.err.println("=== WARNINGS ===");
            for (MajusError warning : warnings) {
                System.err.println(warning.getFormattedError(sourceLines));
            }
        }

        if (hasErrors() || hasWarnings()) {
            System.err.println("Compilation finished with " +
                    getErrorCount() + " error(s) and " +
                    getWarningCount() + " warning(s)");
        }
    }

    public String getSummary() {
        if (!hasErrors() && !hasWarnings()) {
            return "Compilation successful - no errors or warnings";
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Compilation finished with ");

        if (hasErrors()) {
            stringBuilder.append(getErrorCount()).append(" error");
            if (getErrorCount() > 1) stringBuilder.append("s");
        }

        if (hasErrors() && hasWarnings()) {
            stringBuilder.append(" and ");
        }

        if (hasWarnings()) {
            stringBuilder.append(getWarningCount()).append(" warning");
            if (getWarningCount() > 1) stringBuilder.append("s");
        }

        return stringBuilder.toString();
    }
}