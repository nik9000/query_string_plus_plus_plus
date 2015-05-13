grammar Query;

query : infix EOF;

infix    : unmarked;
unmarked : or+;
or       : and (OR and)*;
and      : prefix (AND prefix)*;
prefix   : term;
term     : TERM | OR | AND;

OR : 'OR';
AND : 'AND';

TERM : ~[ \t\r\n]+;      // TERMs are just not whitespace
WS : [ \t\r\n]+ -> skip; // skip spaces, tabs, newlines
