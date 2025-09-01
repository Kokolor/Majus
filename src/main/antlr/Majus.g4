grammar Majus;

@header {
package org.kokolor;
}

program: (declaration | functionDecl)* EOF;
declaration: variableDecl;
variableDecl: IDENTIFIER ':' type '=' expression ';';
functionDecl: ':' IDENTIFIER '(' parameterList? ')' ':' type '{' statement* '}';
parameterList: parameter (',' parameter)*;
parameter: IDENTIFIER ':' type;
type: 'i8' | 'i16' | 'i32' | 'i64' | 'u8' | 'u16' | 'u32' | 'u64' | 'f32' | 'f64' | 'bool' | 'string' | 'void';

statement:
    variableDecl
    | assignmentStmt
    | ifStmt
    | whileStmt
    | forStmt
    | returnStmt
    | expressionStmt
    | block
    ;

assignmentStmt: IDENTIFIER '=' expression ';';
ifStmt: 'if' '(' expression ')' statement ('else' statement)?;
whileStmt: 'while' '(' expression ')' statement;
forStmt: 'for' '(' (variableDecl | assignmentStmt)? ';' expression? ';' assignmentStmt? ')' statement;
returnStmt: 'return' expression? ';';
expressionStmt: expression ';';
block: '{' statement* '}';

expression:
    '(' expression 'as' ':' type ')' # CastExpr
    | '(' expression ')' # ParenExpr
    | expression ('*' | '/' | '%') expression # BinaryExpr
    | expression ('+' | '-') expression # BinaryExpr
    | expression ('<' | '<=' | '>' | '>=' | '==' | '!=') expression # ComparisonExpr
    | expression ('&&' | '||') expression # LogicalExpr
    | '!' expression # UnaryExpr
    | '-' expression # UnaryExpr
    | functionCall # FuncCallExpr
    | IDENTIFIER # IdentifierExpr
    | literal # LiteralExpr
    ;

functionCall: IDENTIFIER '(' argumentList? ')';

argumentList: expression (',' expression)*;

literal:
    INTEGER_LITERAL # IntLiteral
    | FLOAT_LITERAL # FloatLiteral
    | STRING_LITERAL # StringLiteral
    | BOOLEAN_LITERAL # BoolLiteral
    ;

INTEGER_LITERAL: [0-9]+;
FLOAT_LITERAL: [0-9]+ '.' [0-9]+;
STRING_LITERAL: '"' (~["\r\n] | '\\"')* '"';
BOOLEAN_LITERAL: 'true' | 'false';
IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_]*;
WS: [ \t\r\n]+ -> skip;
LINE_COMMENT: '//' ~[\r\n]* -> skip;
BLOCK_COMMENT: '/*' .*? '*/' -> skip;