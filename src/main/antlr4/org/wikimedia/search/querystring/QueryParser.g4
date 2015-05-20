parser grammar QueryParser;
options { tokenVocab=QueryLexer; }

query     : infixOp? EOF;
infixOp   : unmarked;
unmarked  : or (WS or)* WS?;
or        : and ((WS OR WS and)|(WS? SHORT_OR WS? and))*;
and       : prefixOp ((WS AND WS prefixOp)|(WS? SHORT_AND WS? prefixOp))*;
prefixOp  : must | mustNot | boosted;
must      : PLUS WS? boosted;
mustNot   : MINUS WS? boosted;
boosted   : term (CARET boost=decimal)?;
term      : basicTerm | fuzzy | prefix | wildcard | paren | phrase;
fuzzy     : TERM TWIDDLE fuzziness=decimal?;
prefix    : TERM STAR;
wildcard  : TERM? (STAR | QUESTM) (TERM | STAR | QUESTM)*;
paren     : LPAREN WS? infixOp WS? RPAREN;
phrase    : QUOTE QUOTED_TERM* LQUOTE ((TWIDDLE slop=INTEGER))? useNormalTerm=TWIDDLE?;
basicTerm : TERM | OR | SHORT_OR | AND | SHORT_AND | PLUS | MINUS | TWIDDLE | STAR;

decimal   : INTEGER | DECIMAL;