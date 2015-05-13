package org.wikimedia.search.querystring;

import java.util.List;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.wikimedia.search.querystring.QueryParser.AndContext;
import org.wikimedia.search.querystring.QueryParser.OrContext;
import org.wikimedia.search.querystring.QueryParser.PrefixContext;
import org.wikimedia.search.querystring.QueryParser.QueryContext;
import org.wikimedia.search.querystring.QueryParser.UnmarkedContext;

public class QueryParserHelper {
    private final boolean defaultIsAnd = true;

    public Query parse(String str) {
        QueryLexer l = new QueryLexer(new ANTLRInputStream(str));
        QueryParser p = new QueryParser(new BufferedTokenStream(l));
        // We don't want the console error listener....
        // p.removeErrorListeners();
        return new Visitor().visit(p.query());
    }

    private class Visitor extends QueryBaseVisitor<Query> {
        @Override
        public Query visitQuery(QueryContext ctx) {
            return visit(ctx.infix());
        }

        @Override
        public Query visitUnmarked(UnmarkedContext ctx) {
            if (ctx.getChildCount() == 1) {
                return visit(ctx.getChild(0));
            }
            BooleanQuery bq = new BooleanQuery();
            if (defaultIsAnd) {
                for (ParseTree and : ctx.children) {
                    bq.add(visit(and), Occur.MUST);
                }
            } else {
                bq.setMinimumNumberShouldMatch(1);
                for (ParseTree and : ctx.children) {
                    bq.add(visit(and), Occur.SHOULD);
                }
            }
            return bq;
        }

        @Override
        public Query visitOr(OrContext ctx) {
            List<AndContext> ands = ctx.and();
            if (ands.size() == 1) {
                return visit(ands.get(0));
            }
            BooleanQuery bq = new BooleanQuery();
            bq.setMinimumNumberShouldMatch(1);
            for (AndContext and : ands) {
                bq.add(visit(and), Occur.SHOULD);
            }
            return bq;
        }

        @Override
        public Query visitAnd(AndContext ctx) {
            List<PrefixContext> prefixes = ctx.prefix();
            if (prefixes.size() == 1) {
                return visit(prefixes.get(0));
            }
            BooleanQuery bq = new BooleanQuery();
            for (PrefixContext prefix: prefixes) {
                bq.add(visit(prefix), Occur.MUST);
            }
            return bq;
        }

        @Override
        public Query visitTerminal(TerminalNode node) {
            return new TermQuery(new Term("term", node.getText()));
        }
    }
}
