package org.wikimedia.search.querystring;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.wikimedia.search.querystring.QueryLexer;
import org.wikimedia.search.querystring.QueryParser;
import org.wikimedia.search.querystring.QueryParserBaseVisitor;
import org.wikimedia.search.querystring.QueryParser.AndContext;
import org.wikimedia.search.querystring.QueryParser.BasicTermContext;
import org.wikimedia.search.querystring.QueryParser.FuzzyContext;
import org.wikimedia.search.querystring.QueryParser.MustContext;
import org.wikimedia.search.querystring.QueryParser.MustNotContext;
import org.wikimedia.search.querystring.QueryParser.OrContext;
import org.wikimedia.search.querystring.QueryParser.PhraseContext;
import org.wikimedia.search.querystring.QueryParser.PrefixContext;
import org.wikimedia.search.querystring.QueryParser.PrefixOpContext;
import org.wikimedia.search.querystring.QueryParser.UnmarkedContext;
import org.wikimedia.search.querystring.QueryParser.WildcardContext;

public class QueryParserHelper {
    private static final ESLogger log = ESLoggerFactory.getLogger(QueryParserHelper.class.getPackage().getName());
    private final boolean defaultIsAnd;
    private final boolean emptyIsMatchAll;
    private final QueryBuilder builder;

    public QueryParserHelper(QueryBuilder builder, boolean defaultIsAnd, boolean emptyIsMatchAll) {
        this.builder = builder;
        this.defaultIsAnd = defaultIsAnd;
        this.emptyIsMatchAll = emptyIsMatchAll;
    }

    public Query parse(String str) {
        if (log.isDebugEnabled()) {
            BufferedTokenStream s = new BufferedTokenStream(new QueryLexer(new ANTLRInputStream(str)));
            s.fill();
            for (Token t : s.getTokens()) {
                log.debug("Token:  {} {}", QueryLexer.VOCABULARY.getDisplayName(t.getType()), t.getText());
            }
        }

        QueryLexer l = new QueryLexer(new ANTLRInputStream(str));
        QueryParser p = new QueryParser(new BufferedTokenStream(l));
        // We don't want the console error listener....
        // p.removeErrorListeners();
        if (log.isTraceEnabled()) {
            p.addParseListener(new TraceParseTreeListener(p));
        }
        BooleanClause c = new Visitor().visit(p.query());
        if (c == null) {
            // We've just parsed an empty query
            return emptyIsMatchAll ? builder.matchAll() : builder.matchNone();
        }
        if (c.getOccur() == Occur.MUST_NOT) {
            // If we get a negated clause we should faithfully search for not that.
            BooleanQuery bq = new BooleanQuery();
            bq.add(c);
            return bq;
        }
        return c.getQuery();
    }

    /**
     * Visits the parse tree and builds BooleanClauses. BooleanClauses with null
     * occurs means "use the default". If the Occur is set then it means an
     * override from + or - or NOT.
     */
    private class Visitor extends QueryParserBaseVisitor<BooleanClause> {
        /**
         * Control default aggregation of results from multi-child nodes in the
         * parse tree. The ANTLR default is to always take the result from the
         * last node but we switch to taking the the last <strong>non
         * null</string> result.
         */
        @Override
        protected BooleanClause aggregateResult(BooleanClause aggregate, BooleanClause nextResult) {
            return nextResult == null ? aggregate : nextResult;
        }

        @Override
        public BooleanClause visitUnmarked(UnmarkedContext ctx) {
            List<OrContext> ors = ctx.or();
            if (ors.size() == 1) {
                return visit(ctx.getChild(0));
            }
            BooleanQuery bq = new BooleanQuery();
            Occur defaultOccur;
            if (defaultIsAnd) {
                defaultOccur = Occur.MUST;
            } else {
                bq.setMinimumNumberShouldMatch(1);
                defaultOccur = Occur.SHOULD;
            }
            for (OrContext or : ors) {
                add(bq, visit(or), defaultOccur);
            }
            return new BooleanClause(bq, null);
        }

        @Override
        public BooleanClause visitOr(OrContext ctx) {
            List<AndContext> ands = ctx.and();
            if (ands.size() == 1) {
                return visit(ands.get(0));
            }
            BooleanQuery bq = new BooleanQuery();
            bq.setMinimumNumberShouldMatch(1);
            for (AndContext and : ands) {
                add(bq, visit(and), Occur.SHOULD);
            }
            return new BooleanClause(bq, null);
        }

        @Override
        public BooleanClause visitAnd(AndContext ctx) {
            List<PrefixOpContext> prefixes = ctx.prefixOp();
            if (prefixes.size() == 1) {
                return visit(prefixes.get(0));
            }
            BooleanQuery bq = new BooleanQuery();
            for (PrefixOpContext prefix : prefixes) {
                add(bq, visit(prefix), Occur.MUST);
            }
            return new BooleanClause(bq, null);
        }

        @Override
        public BooleanClause visitMustNot(MustNotContext ctx) {
            return new BooleanClause(visit(ctx.term()).getQuery(), Occur.MUST_NOT);
        }

        @Override
        public BooleanClause visitMust(MustContext ctx) {
            return new BooleanClause(visit(ctx.term()).getQuery(), Occur.MUST);
        }

        @Override
        public BooleanClause visitPhrase(PhraseContext ctx) {
            // TODO this isn't quite right for non-english I think.
            List<TerminalNode> terms = ctx.QUOTED_TERM();
            List<String> text = new ArrayList<>(terms.size());
            for (TerminalNode term : terms) {
                text.add(term.getText().replace("\\\"", "\""));
            }
            Query pq;
            if (ctx.slop == null) {
                pq = builder.phraseQuery(text, ctx.useNormalTerm == null);
            } else {
                // The slop is the integer after the ~
                int slop = Integer.parseInt(ctx.slop.getText(), 10);
                pq = builder.phraseQuery(text, slop, ctx.useNormalTerm == null);
            }
            return new BooleanClause(pq, null);
        }

        @Override
        public BooleanClause visitBasicTerm(BasicTermContext ctx) {
            // TODO a term query here is wrong but its fine for now
            return new BooleanClause(builder.termQuery(ctx.getText()), null);
        }

        @Override
        public BooleanClause visitFuzzy(FuzzyContext ctx) {
            if (ctx.fuzziness == null) {
                return new BooleanClause(builder.fuzzyQuery(ctx.TERM().getText()), null);
            } else {
                return new BooleanClause(builder.fuzzyQuery(ctx.TERM().getText(), Float.parseFloat(ctx.fuzziness.getText())), null);
            }
        }

        @Override
        public BooleanClause visitPrefix(PrefixContext ctx) {
            return new BooleanClause(builder.prefixQuery(ctx.TERM().getText()), null);
        }

        @Override
        public BooleanClause visitWildcard(WildcardContext ctx) {
            return new BooleanClause(builder.wildcardQuery(ctx.getText()), null);
        }

        private void add(BooleanQuery bq, BooleanClause clause, Occur defaultOccur) {
            if (clause.getOccur() == null) {
                bq.add(clause.getQuery(), defaultOccur);
            } else {
                bq.add(clause);
            }
        }
    }
}
