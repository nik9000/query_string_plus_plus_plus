package org.wikimedia.search.querystring;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.wikimedia.search.querystring.QueryParser.AndContext;
import org.wikimedia.search.querystring.QueryParser.MustContext;
import org.wikimedia.search.querystring.QueryParser.MustNotContext;
import org.wikimedia.search.querystring.QueryParser.OrContext;
import org.wikimedia.search.querystring.QueryParser.ParenContext;
import org.wikimedia.search.querystring.QueryParser.PhraseContext;
import org.wikimedia.search.querystring.QueryParser.PrefixContext;
import org.wikimedia.search.querystring.QueryParser.QueryContext;
import org.wikimedia.search.querystring.QueryParser.UnmarkedContext;

public class QueryParserHelper {
    private final boolean defaultIsAnd;
    private final QueryBuilder builder;

    public QueryParserHelper(QueryBuilder builder, boolean defaultIsAnd) {
        this.builder = builder;
        this.defaultIsAnd = defaultIsAnd;
    }

    public Query parse(String str) {
        QueryLexer l = new QueryLexer(new ANTLRInputStream(str));
        // BufferedTokenStream s = new BufferedTokenStream(new QueryLexer(new
        // ANTLRInputStream(str)));
        // s.fill();
        // for (Token t : s.getTokens()) {
        // System.err.println(t);
        // }

        QueryParser p = new QueryParser(new BufferedTokenStream(l));
        // We don't want the console error listener....
        // p.removeErrorListeners();
        return new Visitor().visit(p.query()).getQuery();
    }

    /**
     * Visits the parse tree and builds BooleanClauses. BooleanClauses with null
     * occurs means "use the default". If the Occur is set then it means an
     * override from + or - or NOT.
     */
    private class Visitor extends QueryParserBaseVisitor<BooleanClause> {
        @Override
        public BooleanClause visitQuery(QueryContext ctx) {
            // Throw out the EOF.
            return visit(ctx.infix());
        }

        @Override
        public BooleanClause visitUnmarked(UnmarkedContext ctx) {
            if (ctx.getChildCount() == 1) {
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
            for (ParseTree and : ctx.children) {
                add(bq, visit(and), defaultOccur);
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
            List<PrefixContext> prefixes = ctx.prefix();
            if (prefixes.size() == 1) {
                return visit(prefixes.get(0));
            }
            BooleanQuery bq = new BooleanQuery();
            for (PrefixContext prefix : prefixes) {
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
        public BooleanClause visitParen(ParenContext ctx) {
            // Throw out the parens
            return visit(ctx.infix());
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
            TerminalNode number = ctx.NUMBER();
            if (number == null) {
                pq = builder.phraseQuery(text);
            } else {
                pq = builder.phraseQuery(text, Integer.parseInt(number.getText(), 10));
            }
            return new BooleanClause(pq, null);
        }

        @Override
        public BooleanClause visitTerminal(TerminalNode node) {
            // TODO a term query here is wrong but its fine for now
            return new BooleanClause(builder.termQuery(node.getText()), null);
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
