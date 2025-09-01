package org.kokolor;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;
import java.nio.file.*;

public class MajusCompiler {
    private final MajusErrorHandler errorHandler;
    private final SymbolTable symbolTable;
    private boolean verbose;

    public MajusCompiler() {
        this.errorHandler = new MajusErrorHandler();
        this.symbolTable = new SymbolTable();
        this.verbose = true;
    }

    public CompilationResult compile(String sourceCode, String filename) {
        System.out.println("=== Compiling " + filename + " ===");
        errorHandler.clear();
        errorHandler.setSource(sourceCode, filename);

        try {
            System.out.println("Phase 1: Lexical and Syntax Analysis");
            CharStream input = CharStreams.fromString(sourceCode);
            ParseTree tree = getParseTree(input);

            if (errorHandler.hasErrors()) {
                return new CompilationResult(false, errorHandler, null, null, null);
            }

            System.out.println("✓ Syntax analysis completed");
            System.out.println("Phase 2: Semantic Analysis");
            SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer(symbolTable, errorHandler);
            semanticAnalyzer.visit(tree);

            if (errorHandler.hasErrors()) {
                return new CompilationResult(false, errorHandler, symbolTable, tree, null);
            }

            System.out.println("✓ Semantic analysis completed");
            System.out.println("Phase 3: Code Generation");
            MajusLlvmCodeGenerator codeGenerator = new MajusLlvmCodeGenerator(symbolTable);
            codeGenerator.visit(tree);
            String generatedCode = codeGenerator.getIR();
            System.out.println("✓ Code generation completed");

            System.out.println("✓ Code generation completed");

            return new CompilationResult(true, errorHandler, symbolTable, tree, generatedCode);

        } catch (Exception exception) {
            errorHandler.error(ErrorType.SYNTAX_ERROR, "Internal compiler error: " + exception.getMessage(), 0, 0);
            exception.printStackTrace();
            return new CompilationResult(false, errorHandler, null, null, null);
        }
    }

    private ParseTree getParseTree(CharStream input) {
        MajusParser parser = getMajusParser(input);
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String message, RecognitionException exception) {
                errorHandler.error(ErrorType.SYNTAX_ERROR, "Syntax error: " + message, line, charPositionInLine + 1);
            }
        });

        return parser.program();
    }

    private MajusParser getMajusParser(CharStream input) {
        MajusLexer lexer = new MajusLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String message, RecognitionException e) {
                errorHandler.error(ErrorType.SYNTAX_ERROR, "Lexical error: " + message, line, charPositionInLine + 1);
            }
        });

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MajusParser parser = new MajusParser(tokens);
        parser.removeErrorListeners();

        return parser;
    }

    public CompilationResult compileFile(String filepath) {
        try {
            String sourceCode = Files.readString(Paths.get(filepath));

            return compile(sourceCode, filepath);
        } catch (IOException e) {
            errorHandler.error(ErrorType.SYNTAX_ERROR, "Cannot read file: " + e.getMessage(), 0, 0);

            return new CompilationResult(false, errorHandler, null, null, null);
        }
    }

    public static class CompilationResult {
        private final boolean success;
        private final MajusErrorHandler errorHandler;
        private final String generatedCode;

        public CompilationResult(boolean success, MajusErrorHandler errorHandler, SymbolTable symbolTable, ParseTree parseTree, String generatedCode) {
            this.success = success;
            this.errorHandler = errorHandler;
            this.generatedCode = generatedCode;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getGeneratedCode() {
            return generatedCode;
        }

        public void printResults() {
            if (success) {
                System.out.println("=== COMPILATION SUCCESSFUL ===");

                if (generatedCode != null) {
                    System.out.println("\n=== GENERATED CODE ===");
                    System.out.println(generatedCode);
                }
            } else {
                System.err.println("=== COMPILATION FAILED ===");
            }

            if (errorHandler.hasErrors()) {
                errorHandler.printErrors();
            }
        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java MajusCompiler <input-file>");
            System.exit(1);
        }

        MajusCompiler compiler = new MajusCompiler();
        CompilationResult result = compiler.compileFile(args[0]);
        result.printResults();
        System.exit(result.isSuccess() ? 0 : 1);
    }
}