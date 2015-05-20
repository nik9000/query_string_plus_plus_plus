lexer grammar QueryLexer;

OR        : 'OR';
SHORT_OR  : '||';
AND       : 'AND';
SHORT_AND : '&&';
PLUS      : '+';
MINUS     : '-' | '!' | 'NOT';
LPAREN    : '(';
RPAREN    : ')';
QUOTE     : '"' -> pushMode(QUOTED);
TWIDDLE   : '~';
STAR      : '*';
QUESTM    : '?';
CARET     : '^';
DOT       : '.';
COLON     : ':';
COMMA     : ',';

INTEGER   : [0-9]+;
DECIMAL   : INTEGER? '.'? INTEGER;
WS        : [ \t\r\n]+;
TERM      : ~[ \t\r\n+\-!()"~*?|&^:]~[ \t\r\n()"~*?|&^:]*; // TERMs are basically everything else

mode QUOTED;
LQUOTE        : '"' -> popMode;
QUOTED_TERM   : ('\\"'|~[ \t\r\n()"])+;
QUOTED_WS     : [ \t\r\n]+ -> skip;    // skip spaces, tabs, newlines
