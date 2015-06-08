lexer grammar QueryLexer;

OR        : 'OR';
SHORT_OR  : '|' '|'?;
AND       : 'AND';
SHORT_AND : '&' '&'?;
PLUS      : '+';
MINUS     : '-' | '!' | 'NOT';
LPAREN    : '(';
RPAREN    : ')';
QUOTE     : '"';
TWIDDLE   : '~';
STAR      : '*';
QUESTM    : '?';
CARET     : '^';
DOT       : '.';
COLON     : ':';
COMMA     : ',';
SLASH     : '/';

INTEGER   : [0-9]+;
DECIMAL   : INTEGER? '.'? INTEGER;
WS        : [ \t\r\n]+;
TERM      : (~[ \t\r\n+\-!()"~*?|&^:,/]|'\\"')(~[ \t\r\n()"~*?|&^:,/]|'\\"')*; // TERMs are basically everything else
