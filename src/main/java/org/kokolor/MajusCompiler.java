package org.kokolor;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.*;
import java.nio.file.*;

public class MajusCompiler {
    private final MajusErrorHandler errorHandler;
    private final SymbolTable symbolTable;
    private boolean verbose;
    private int optLevel = 2;
    private boolean emitLL = false;
    private boolean emitObj = false;

    public MajusCompiler() {
        this.errorHandler = new MajusErrorHandler();
        this.symbolTable = new SymbolTable();
        this.verbose = true;
    }

    public void setOptLevel(int level) {
        this.optLevel = Math.max(0, Math.min(level, 3));
    }

    public void setEmitLL(boolean emit) {
        this.emitLL = emit;
    }

    public void setEmitObj(boolean emit) {
        this.emitObj = emit;
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
            MajusLlvmCodeGenerator codeGenerator = new MajusLlvmCodeGenerator(symbolTable, optLevel);
            codeGenerator.visit(tree);
            String generatedCode = codeGenerator.getIR();
            System.out.println("✓ Code generation completed");
            String base = stripExtension(filename);

            if (emitLL) {
                Path llPath = Paths.get(base + ".ll");
                Files.writeString(llPath, generatedCode);
                System.out.println("Wrote LLVM IR: " + llPath);
            }
            if (emitObj) {
                String objPath = base + ".o";
                codeGenerator.emitObject(objPath);
                System.out.println("Wrote object file: " + objPath);
            }

            return new CompilationResult(true, errorHandler, symbolTable, tree, generatedCode);

        } catch (Exception exception) {
            errorHandler.error(ErrorType.SYNTAX_ERROR, "Internal compiler error: " + exception.getMessage(), 0, 0);
            exception.printStackTrace();
            return new CompilationResult(false, errorHandler, null, null, null);
        }
    }

    private static String stripExtension(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String dir = slash >= 0 ? path.substring(0, slash + 1) : "";
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        return dir + base;
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
            System.out.println("Usage: [--emit-ll] [--emit-o] [--O0|--O1|--O2|--O3 | -O0|-O1|-O2|-O3] <input-file>");
            System.exit(1);
        }

        MajusCompiler compiler = new MajusCompiler();

        String filepath = null;
        for (String raw : args) {
            if (raw == null) continue;
            String arg = raw.trim();
            if (arg.isEmpty()) continue;

            if ("--emit-ll".equals(arg)) {
                compiler.setEmitLL(true);
            } else if ("--emit-o".equals(arg)) {
                compiler.setEmitObj(true);
            } else if ("--O0".equals(arg) || "-O0".equals(arg)) {
                compiler.setOptLevel(0);
            } else if ("--O1".equals(arg) || "-O1".equals(arg)) {
                compiler.setOptLevel(1);
            } else if ("--O2".equals(arg) || "-O2".equals(arg)) {
                compiler.setOptLevel(2);
            } else if ("--O3".equals(arg) || "-O3".equals(arg)) {
                compiler.setOptLevel(3);
            } else if (arg.startsWith("-O") && arg.length() == 3 && Character.isDigit(arg.charAt(2))) {
                try {
                    compiler.setOptLevel(Integer.parseInt(arg.substring(2)));
                } catch (NumberFormatException ignore) {
                }
            } else if (arg.startsWith("--")) {
                System.err.println("Unknown option: " + arg);
                System.out.println("Usage: [--emit-ll] [--emit-o] [--O0|--O1|--O2|--O3 | -O0|-O1|-O2|-O3] <input-file>");
                System.exit(1);
            } else {
                filepath = arg;
            }
        }

        if (filepath == null) {
            System.out.println("Usage: [--emit-ll] [--emit-o] [--O0|--O1|--O2|--O3 | -O0|-O1|-O2|-O3] <input-file>");
            System.exit(1);
        }

        CompilationResult result = compiler.compileFile(filepath);
        result.printResults();
        System.exit(result.isSuccess() ? 0 : 1);
    }
}