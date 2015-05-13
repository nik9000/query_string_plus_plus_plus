package org.wikimedia.search.querystring;

import java.util.List;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.wikimedia.search.querystring.QueryParser.AndContext;
import org.wikimedia.search.querystring.QueryParser.MustContext;
import org.wikimedia.search.querystring.QueryParser.MustNotContext;
import org.wikimedia.search.querystring.QueryParser.OrContext;
import org.wikimedia.search.querystring.QueryParser.PrefixContext;
import org.wikimedia.search.querystring.QueryParser.QueryContext;
import org.wikimedia.search.querystring.QueryParser.UnmarkedContext;

public class QueryParserHelper {
    private final boolean defaultIsAnd;

    public QueryParserHelper(boolean defaultIsAnd) {
        this.defaultIsAnd = defaultIsAnd;
    }

    public Query parse(String str) {
        QueryLexer l = new QueryLexer(new ANTLRInputStream(str));
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
    private class Visitor extends QueryBaseVisitor<BooleanClause> {
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
        public BooleanClause visitTerminal(TerminalNode node) {
            return new BooleanClause(new TermQuery(new Term("term", node.getText())), null);
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
