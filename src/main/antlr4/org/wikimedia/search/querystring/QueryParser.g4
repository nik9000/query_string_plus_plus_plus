parser grammar QueryParser;
options { tokenVocab=QueryLexer; }

query     : WS? infixOp? WS? EOF;
justFields : fields EOF;

infixOp   : unmarked;
unmarked  : or (WS or)*;
or        : and ((WS OR WS and)|(WS? SHORT_OR WS? and))*;
and       : prefixOp ((WS AND WS prefixOp)|(WS? SHORT_AND WS? prefixOp))*;
prefixOp  : must | mustNot | fielded;
must      : PLUS WS? fielded;
mustNot   : MINUS WS? fielded;
fielded   : (fields COLON)? boosted;
boosted   : term (CARET boost=number)?;
term      : fuzzy | prefix | fieldExists | phrase | paren | regex | wildcard | basicTerm;
fuzzy     : TERM TWIDDLE fuzziness=number?;
prefix    : TERM STAR;
fieldExists : STAR;
wildcard  : basicTerm? (STAR | QUESTM) (basicTerm | STAR | QUESTM)*;
paren     : LPAREN WS? infixOp WS? RPAREN;
phrase    : QUOTE (phraseTerm (WS phraseTerm)*)? ((QUOTE (TWIDDLE slop=INTEGER)? useNormalTerm=TWIDDLE?) | EOF);
phraseTerm : fuzzy | prefix | fieldExists | wildcard | basicTerm;
regex     : SLASH content=regexContent? SLASH;
regexContent : basicTerm (WS basicTerm)*;
basicTerm : (basicTermPart)+?;
basicTermPart : TERM | OR | SHORT_OR | AND | SHORT_AND | PLUS | MINUS | TWIDDLE | COMMA | DECIMAL | INTEGER | COLON | SLASH
          | QUOTE    // Term with a quote in it - its not a phrase, just an "I rolled my face on the keyboard" kind of term.
          | LPAREN   // Term containing a parenthesis - not a parenthetical term, just some term that actually contains the paren. Like a product name.
          | RPAREN
          | CARET    // Term containing a caret rather than a proper boost
          | DOT;     // Term containing a dot rather than a proper field name

number    : INTEGER | DECIMAL;
fields    : field (COMMA WS? field)*;
field     : fieldName (CARET boost=number)?;
fieldName : TERM (DOT TERM)*;
