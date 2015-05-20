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
import org.wikimedia.search.querystring.QueryParser.AndContext;
import org.wikimedia.search.querystring.QueryParser.BasicTermContext;
import org.wikimedia.search.querystring.QueryParser.BoostedContext;
import org.wikimedia.search.querystring.QueryParser.FieldContext;
import org.wikimedia.search.querystring.QueryParser.FieldExistsContext;
import org.wikimedia.search.querystring.QueryParser.FieldedContext;
import org.wikimedia.search.querystring.QueryParser.FieldsContext;
import org.wikimedia.search.querystring.QueryParser.FuzzyContext;
import org.wikimedia.search.querystring.QueryParser.MustContext;
import org.wikimedia.search.querystring.QueryParser.MustNotContext;
import org.wikimedia.search.querystring.QueryParser.OrContext;
import org.wikimedia.search.querystring.QueryParser.PhraseContext;
import org.wikimedia.search.querystring.QueryParser.PrefixContext;
import org.wikimedia.search.querystring.QueryParser.PrefixOpContext;
import org.wikimedia.search.querystring.QueryParser.UnmarkedContext;
import org.wikimedia.search.querystring.QueryParser.WildcardContext;
import org.wikimedia.search.querystring.query.DefaultingQueryBuilder;
import org.wikimedia.search.querystring.query.FieldDefinition;

public class QueryParserHelper {
    public static List<FieldDefinition> parseFields(FieldsHelper fieldsHelper, String str) {
        QueryLexer l = new QueryLexer(new ANTLRInputStream(str));
        QueryParser p = new QueryParser(new BufferedTokenStream(l));
        return fieldsFromContext(fieldsHelper, p.fields());
    }

    private static final ESLogger log = ESLoggerFactory.getLogger(QueryParserHelper.class.getPackage().getName());
    private final FieldsHelper fieldsHelper;
    private final DefaultingQueryBuilder rootBuilder;
    private final boolean defaultIsAnd;
    private final boolean emptyIsMatchAll;

    public QueryParserHelper(FieldsHelper fieldsHelper, DefaultingQueryBuilder rootBuilder, boolean defaultIsAnd, boolean emptyIsMatchAll) {
        this.fieldsHelper = fieldsHelper;
        this.rootBuilder = rootBuilder;
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
            return emptyIsMatchAll ? rootBuilder.matchAll() : rootBuilder.matchNone();
        }
        if (c.getOccur() == Occur.MUST_NOT) {
            // If we get a negated clause we should faithfully search for not
            // that.
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
        private DefaultingQueryBuilder builder;

        public Visitor() {
            builder = rootBuilder;
        }

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
            return wrap(bq);
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
            return wrap(bq);
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
            return wrap(bq);
        }

        @Override
        public BooleanClause visitMustNot(MustNotContext ctx) {
            return new BooleanClause(visit(ctx.fielded()).getQuery(), Occur.MUST_NOT);
        }

        @Override
        public BooleanClause visitMust(MustContext ctx) {
            return new BooleanClause(visit(ctx.fielded()).getQuery(), Occur.MUST);
        }

        @Override
        public BooleanClause visitFielded(FieldedContext ctx) {
            FieldsContext fieldCtx = ctx.fields();
            if (fieldCtx == null) {
                return visit(ctx.boosted());
            }
            DefaultingQueryBuilder lastBuilder = builder;
            builder = builder.forFields(fieldsFromContext(fieldsHelper, fieldCtx));
            try {
                return visit(ctx.boosted());
            } finally {
                builder = lastBuilder;
            }
        }

        @Override
        public BooleanClause visitBoosted(BoostedContext ctx) {
            if (ctx.boost == null) {
                return visit(ctx.term());
            }
            if (ctx.boost.basicTerm() != null) {
                // This looks like garbage instead of a useful boost - lets just pretend this is a term.
                return wrap(builder.termQuery(ctx.getText()));
            }
            BooleanClause term = visit(ctx.term());
            term.getQuery().setBoost(Float.parseFloat(ctx.boost.getText()));
            return term;
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
                // The slop is the number after the ~
                int slop = Integer.parseInt(ctx.slop.getText(), 10);
                pq = builder.phraseQuery(text, slop, ctx.useNormalTerm == null);
            }
            return wrap(pq);
        }

        @Override
        public BooleanClause visitBasicTerm(BasicTermContext ctx) {
            return wrap(builder.termQuery(ctx.getText()));
        }

        @Override
        public BooleanClause visitFuzzy(FuzzyContext ctx) {
            if (ctx.fuzziness == null) {
                return wrap(builder.fuzzyQuery(ctx.TERM().getText()));
            }
            if (ctx.fuzziness.basicTerm() != null) {
                // This looks like garbage in place of the slop. Icky.
                return wrap(builder.termQuery(ctx.getText()));
            }
            return wrap(builder.fuzzyQuery(ctx.TERM().getText(), Float.parseFloat(ctx.fuzziness.getText())));
        }

        @Override
        public BooleanClause visitPrefix(PrefixContext ctx) {
            return wrap(builder.prefixQuery(ctx.TERM().getText()));
        }

        @Override
        public BooleanClause visitFieldExists(FieldExistsContext ctx) {
            // TODO dip into elasticsearch to build these properly
            return wrap(builder.prefixQuery(""));
        }

        @Override
        public BooleanClause visitWildcard(WildcardContext ctx) {
            return wrap(builder.wildcardQuery(ctx.getText()));
        }

        private void add(BooleanQuery bq, BooleanClause clause, Occur defaultOccur) {
            if (clause.getOccur() == null) {
                bq.add(clause.getQuery(), defaultOccur);
            } else {
                bq.add(clause);
            }
        }

        /**
         * Wrap a query into the default, non-opinionated result.
         */
        private BooleanClause wrap(Query query) {
            return new BooleanClause(query, null);
        }
    }

    private static List<FieldDefinition> fieldsFromContext(FieldsHelper fieldsHelper, FieldsContext ctx) {
        List<FieldDefinition> fields = new ArrayList<>();
        for (FieldContext field : ctx.field()) {
            float boost = 1;
            if (field.boost != null) {
                boost = Float.parseFloat(field.boost.getText());
            }
            for (String fieldName : fieldsHelper.resolveSynonyms(field.fieldName().getText())) {
                fields.add(new FieldDefinition(fieldName, fieldName, boost));
            }
        }
        return fields;
    }
}
