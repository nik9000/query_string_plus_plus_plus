parser grammar QueryParser;
options { tokenVocab=QueryLexer; }

query : infix EOF;

infix     : unmarked;
unmarked  : or+;
or        : and (OR and)*;
and       : prefix (AND prefix)*;
prefix    : must | mustNot | term;
must      : PLUS term;
mustNot   : MINUS term;
term      : basicTerm | paren | phrase;
basicTerm : TERM | OR | AND | PLUS;
paren     : LPAREN infix RPAREN;
phrase    : QUOTE QUOTED_TERM* LQUOTE;
