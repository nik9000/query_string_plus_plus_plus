parser grammar QueryParser;
options { tokenVocab=QueryLexer; }

query : infixOp EOF;

infixOp   : unmarked;
unmarked  : or+;
or        : and (OR and)*;
and       : prefixOp (AND prefixOp)*;
prefixOp  : must | mustNot | term;
must      : PLUS term;
mustNot   : MINUS term;
term      : fuzzy | prefix | basicTerm | paren | phrase;
fuzzy     : TERM TWIDDLE fuzziness=(INTEGER | DECIMAL)?;
prefix    : TERM STAR;
paren     : LPAREN infixOp RPAREN;
phrase    : QUOTE QUOTED_TERM* LQUOTE ((TWIDDLE slop=INTEGER))? useNormalTerm=TWIDDLE?;
basicTerm : TERM | OR | AND | PLUS | TWIDDLE;
