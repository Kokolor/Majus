package org.kokolor;

import java.util.*;

enum MajusType {
    I8("i8"),
    I16("i16"),
    I32("i32"),
    I64("i64"),
    U8("u8"),
    U16("u16"),
    U32("u32"),
    U64("u64"),
    F32("f32"),
    F64("f64"),
    BOOL("bool"),
    STRING("string"),
    VOID("void"),
    UNKNOWN("unknown");

    private final String name;

    MajusType(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static MajusType fromString(String type) {
        for (MajusType _type : values()) {
            if (_type.name.equals(type)) {
                return _type;
            }
        }

        return UNKNOWN;
    }

    public boolean isNumeric() {
        return this == I8 || this == I16 || this == I32 || this == I64 || this == U8 || this == U16 || this == U32 || this == U64 || this == F32 || this == F64;
    }

    public boolean isInteger() {
        return this == I8 || this == I16 || this == I32 || this == I64 || this == U8 || this == U16 || this == U32 || this == U64;
    }

    public boolean isFloat() {
        return this == F32 || this == F64;
    }
}

abstract class Symbol {
    protected String name;
    protected MajusType type;
    protected int line;
    protected int column;

    public Symbol(String name, MajusType type, int line, int column) {
        this.name = name;
        this.type = type;
        this.line = line;
        this.column = column;
    }

    public String getName() {
        return name;
    }

    public MajusType getType() {
        return type;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public abstract String getKind();
}

class VariableSymbol extends Symbol {
    private final boolean isConstant;
    private boolean isInitialized;

    public VariableSymbol(String name, MajusType type, boolean isConstant, int line, int column) {
        super(name, type, line, column);
        this.isConstant = isConstant;
        this.isInitialized = false;
    }

    public boolean isConstant() {
        return isConstant;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public void setInitialized(boolean initialized) {
        this.isInitialized = initialized;
    }

    @Override
    public String getKind() {
        return isConstant ? "constant" : "variable";
    }
}

class FunctionSymbol extends Symbol {
    private final List<VariableSymbol> parameters;
    private Scope localScope;

    public FunctionSymbol(String name, MajusType returnType, int line, int column) {
        super(name, returnType, line, column);
        this.parameters = new ArrayList<>();
    }

    public void addParameter(VariableSymbol param) {
        parameters.add(param);
    }

    public List<VariableSymbol> getParameters() {
        return parameters;
    }

    public void setLocalScope(Scope scope) {
        this.localScope = scope;
    }

    public Scope getLocalScope() {
        return localScope;
    }

    @Override
    public String getKind() {
        return "function";
    }

    public String getSignature() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(name).append("(");

        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) stringBuilder.append(", ");
            stringBuilder.append(parameters.get(i).getType());
        }

        stringBuilder.append(") : ").append(type);

        return stringBuilder.toString();
    }
}

class Scope {
    private final String name;
    private final Scope parent;
    private final Map<String, Symbol> symbols;
    private final List<Scope> children;

    public Scope(String name, Scope parent) {
        this.name = name;
        this.parent = parent;
        this.symbols = new HashMap<>();
        this.children = new ArrayList<>();

        if (parent != null) {
            parent.addChild(this);
        }
    }

    public void addChild(Scope child) {
        children.add(child);
    }

    public boolean define(Symbol symbol) {
        if (symbols.containsKey(symbol.getName())) {
            return false;
        }

        symbols.put(symbol.getName(), symbol);

        return true;
    }

    public Symbol resolve(String name) {
        Symbol symbol = symbols.get(name);

        if (symbol != null) {
            return symbol;
        }

        if (parent != null) {
            return parent.resolve(name);
        }

        return null;
    }

    public Symbol resolveLocal(String name) {
        return symbols.get(name);
    }

    public String getName() {
        return name;
    }

    public Scope getParent() {
        return parent;
    }

    public Map<String, Symbol> getSymbols() {
        return symbols;
    }

    public List<Scope> getChildren() {
        return children;
    }
}

class SymbolTable {
    private final Scope globalScope;
    private Scope currentScope;
    private int scopeCounter;

    public SymbolTable() {
        this.globalScope = new Scope("global", null);
        this.currentScope = globalScope;
        this.scopeCounter = 0;

        addBuiltinFunctions();
    }

    private void addBuiltinFunctions() {
        FunctionSymbol printFunc = new FunctionSymbol("print", MajusType.VOID, 0, 0);
        printFunc.addParameter(new VariableSymbol("value", MajusType.STRING, false, 0, 0));
        globalScope.define(printFunc);
        FunctionSymbol printlnFunc = new FunctionSymbol("println", MajusType.VOID, 0, 0);
        printlnFunc.addParameter(new VariableSymbol("value", MajusType.STRING, false, 0, 0));
        globalScope.define(printlnFunc);
        FunctionSymbol toStringFunc = new FunctionSymbol("toString", MajusType.STRING, 0, 0);
        toStringFunc.addParameter(new VariableSymbol("value", MajusType.I32, false, 0, 0));
        globalScope.define(toStringFunc);
    }

    public void enterScope(String name) {
        currentScope = new Scope(name != null ? name : "scope_" + (++scopeCounter), currentScope);
    }

    public void exitScope() {
        if (currentScope.getParent() != null) {
            currentScope = currentScope.getParent();
        }
    }

    public boolean define(Symbol symbol) {
        return currentScope.define(symbol);
    }

    public Symbol resolve(String name) {
        return currentScope.resolve(name);
    }

    public Symbol resolveLocal(String name) {
        return currentScope.resolveLocal(name);
    }

    public Scope getGlobalScope() {
        return globalScope;
    }

    public Scope getCurrentScope() {
        return currentScope;
    }

    public void printScopes() {
        printScope(globalScope, 0);
    }

    private void printScope(Scope scope, int depth) {
        String indent = "  ".repeat(depth);
        System.out.println(indent + "Scope: " + scope.getName());

        for (Symbol symbol : scope.getSymbols().values()) {
            System.out.println(indent + "  " + symbol.getKind() + ": " + symbol.getName() + " : " + symbol.getType());
        }

        for (Scope child : scope.getChildren()) {
            printScope(child, depth + 1);
        }
    }
}