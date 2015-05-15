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
term      : fuzzy | basicTerm | paren | phrase;
fuzzy     : TERM TWIDDLE fuzziness=(INTEGER | DECIMAL)?;
paren     : LPAREN infix RPAREN;
phrase    : QUOTE QUOTED_TERM* LQUOTE ((TWIDDLE slop=INTEGER))? useNormalTerm=TWIDDLE?;
basicTerm : TERM | OR | AND | PLUS | TWIDDLE;
