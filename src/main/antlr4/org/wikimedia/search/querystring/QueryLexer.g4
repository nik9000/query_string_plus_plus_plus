lexer grammar QueryLexer;

OR      : 'OR' | '||';
AND     : 'AND' | '&&';
PLUS    : '+';
MINUS   : '-' | '!' | 'NOT';
LPAREN  : '(';
RPAREN  : ')';
QUOTE   : '"' -> pushMode(QUOTED);
TWIDDLE : '~';
STAR    : '*';

INTEGER : [0-9]+;
DECIMAL : (INTEGER '.'? INTEGER?) | ('.' INTEGER);
WS      : [ \t\r\n]+ -> skip;            // skip spaces, tabs, newlines
TERM    : ~[ \t\r\n+\-!()"~*]~[ \t\r\n()"~*]*; // TERMs are basically everything else

mode QUOTED;
LQUOTE        : '"' -> popMode;
QUOTED_TERM   : ('\\"'|~[ \t\r\n()"])+;
QUOTED_WS     : [ \t\r\n]+ -> skip;    // skip spaces, tabs, newlines
