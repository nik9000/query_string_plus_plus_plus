package org.wikimedia.search.querystring;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;

/**
 * Like ANTLR's TraceListener but uses logs. Note that this never does any
 * checking if the level is enabled and it can do significant work so don't add
 * it to the parser unless trace is enabled.
 */
public class TraceParseTreeListener implements ParseTreeListener {
    private static final ESLogger log = ESLoggerFactory.getLogger(QueryParserHelper.class.getPackage().getName());

    private final Parser parser;

    public TraceParseTreeListener(Parser parser) {
        this.parser = parser;
    }

    @Override
    public void enterEveryRule(ParserRuleContext ctx) {
        log.trace("enter {}, LT(1)={}", parser.getRuleNames()[ctx.getRuleIndex()], parser.getTokenStream().LT(1).getText());
    }

    @Override
    public void visitTerminal(TerminalNode node) {
        log.trace("consume {}", node.getSymbol(), parser.getRuleNames()[parser.getContext().getRuleIndex()]);
    }

    @Override
    public void visitErrorNode(ErrorNode node) {
    }

    @Override
    public void exitEveryRule(ParserRuleContext ctx) {
        log.trace("exit {}, LT(1)={}", parser.getRuleNames()[ctx.getRuleIndex()], parser.getTokenStream().LT(1).getText());
    }
}
