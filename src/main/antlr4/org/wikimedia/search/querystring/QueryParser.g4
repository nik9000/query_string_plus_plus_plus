parser grammar QueryParser;
options { tokenVocab=QueryLexer; }

query     : infixOp? WS? EOF;
justFields : fields EOF;

infixOp   : unmarked;
unmarked  : or (WS or)*;
or        : and ((WS OR WS and)|(WS? SHORT_OR WS? and))*;
and       : prefixOp ((WS AND WS prefixOp)|(WS? SHORT_AND WS? prefixOp))*;
prefixOp  : must | mustNot | fielded;
must      : PLUS WS? fielded;
mustNot   : MINUS WS? fielded;
fielded   : (fields COLON)? boosted;
boosted   : term (CARET boost=decimalPlease)?;
term      : fuzzy | prefix | fieldExists | wildcard | paren | phrase | regex | basicTerm;
fuzzy     : TERM TWIDDLE fuzziness=decimalPlease?;
prefix    : TERM STAR;
fieldExists : STAR;
wildcard  : TERM? (STAR | QUESTM) (TERM | STAR | QUESTM)*;
paren     : LPAREN WS? infixOp WS? RPAREN;
phrase    : QUOTE (phraseTerm (WS phraseTerm)*)? ((QUOTE (TWIDDLE slop=INTEGER)? useNormalTerm=TWIDDLE?) | EOF);
phraseTerm :fuzzy | prefix | fieldExists | wildcard | basicTerm;
regex     : SLASH content=regexContent? SLASH;
regexContent : basicTerm (WS basicTerm)*;
basicTerm : (basicTermPart)+?;
basicTermPart : TERM | OR | SHORT_OR | AND | SHORT_AND | PLUS | MINUS | TWIDDLE | STAR | COMMA | DECIMAL | INTEGER | COLON | SLASH;


decimalPlease   : INTEGER | DECIMAL | basicTerm; // Basic term is required to handle weird queries - those aren't valid but we have to degrade.
fields    : field (COMMA WS? field)*;
field     : fieldName (CARET boost=decimalPlease)?;
fieldName : TERM (DOT TERM)*;
