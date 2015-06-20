package org.wikimedia.search.querystring;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.wikimedia.search.querystring.FieldsHelper.UnauthorizedAction;
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
import org.wikimedia.search.querystring.QueryParser.PhraseTermContext;
import org.wikimedia.search.querystring.QueryParser.PrefixContext;
import org.wikimedia.search.querystring.QueryParser.PrefixOpContext;
import org.wikimedia.search.querystring.QueryParser.QueryContext;
import org.wikimedia.search.querystring.QueryParser.RegexContext;
import org.wikimedia.search.querystring.QueryParser.UnmarkedContext;
import org.wikimedia.search.querystring.QueryParser.WildcardContext;
import org.wikimedia.search.querystring.query.DefaultingQueryBuilder;
import org.wikimedia.search.querystring.query.FieldReference;
import org.wikimedia.search.querystring.query.FieldUsage;
import org.wikimedia.search.querystring.query.PhraseTerm;
import org.wikimedia.search.querystring.query.phraseterm.FuzzyPhraseTerm;
import org.wikimedia.search.querystring.query.phraseterm.PrefixPhraseTerm;
import org.wikimedia.search.querystring.query.phraseterm.SimpleStringPhraseTerm;
import org.wikimedia.search.querystring.query.phraseterm.WildcardPhraseTerm;

public class QueryParserHelper {
    public static List<FieldReference> parseFields(String str) {
        return fieldsFromContext(buildParser(str).justFields().fields());
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
        QueryParser parser = buildParser(str);
        QueryContext query = parser.query();
        if (log.isTraceEnabled()) {
            log.trace("Parse tree: {}", query.toStringTree(parser));
        }
        BooleanClause c = new Visitor().visit(query);
        if (c == null || c.getQuery() == null) {
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

    private static QueryParser buildParser(String toParse) {
        if (log.isDebugEnabled()) {
            BufferedTokenStream s = new BufferedTokenStream(new QueryLexer(new ANTLRInputStream(toParse)));
            s.fill();
            for (Token t : s.getTokens()) {
                log.debug("Token:  {} {}", QueryLexer.VOCABULARY.getDisplayName(t.getType()), t.getText());
            }
        }

        QueryLexer l = new QueryLexer(new ANTLRInputStream(toParse));
        QueryParser p = new QueryParser(new BufferedTokenStream(l));
        // We don't want the console error listener....
        // p.removeErrorListeners();
        if (log.isTraceEnabled()) {
            p.addParseListener(new TraceParseTreeListener(p));
        }
        return p;
    }

    /**
     * Visits the parse tree and builds BooleanClauses. BooleanClauses with null
     * occurs means "use the default". If the Occur is set then it means an
     * override from + or - or NOT.
     */
    private class Visitor extends PickLastAggregatingVisitor<BooleanClause> {
        private PhraseTermVisitor phraseTermVisitor;
        private DefaultingQueryBuilder builder;

        public Visitor() {
            builder = rootBuilder;
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
            List<FieldUsage> fields = fieldsHelper.resolve(fieldsFromContext(fieldCtx), UnauthorizedAction.REMOVE);
            if (fields.isEmpty()) {
                /*
                 * The user specified some field that can't be searched. That is
                 * ok - they probably want to search for something with a colon
                 * in it. Lets just treat that like a term query for now even
                 * though we might decide later some different handling makes
                 * sense.
                 */
                return wrap(builder.termQuery(ctx.getText()));
            }
            DefaultingQueryBuilder lastBuilder = builder;
            builder = builder.forFields(fields);
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
                // This looks like garbage instead of a useful boost - lets just
                // pretend this is a term.
                return wrap(builder.termQuery(ctx.getText()));
            }
            BooleanClause term = visit(ctx.term());
            term.getQuery().setBoost(Float.parseFloat(ctx.boost.getText()));
            return term;
        }

        @Override
        public BooleanClause visitPhrase(PhraseContext ctx) {
            List<PhraseTermContext> terms = ctx.phraseTerm();
            List<PhraseTerm> text = new ArrayList<>(terms.size());
            if (phraseTermVisitor == null) {
                phraseTermVisitor = new PhraseTermVisitor();
            }
            for (PhraseTermContext term : terms) {
                text.add(phraseTermVisitor.visitPhraseTerm(term));
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
            float fuzziness = Float.NEGATIVE_INFINITY;
            if (ctx.fuzziness != null) {
                if (ctx.fuzziness.basicTerm() != null) {
                    // This looks like garbage in place of the slop. Icky.
                    return wrap(builder.termQuery(ctx.getText()));
                }
                fuzziness = Float.parseFloat(ctx.fuzziness.getText());
            }
            return wrap(builder.fuzzyQuery(ctx.TERM().getText(), fuzziness));
        }

        @Override
        public BooleanClause visitPrefix(PrefixContext ctx) {
            return wrap(builder.prefixQuery(ctx.TERM().getText()));
        }

        @Override
        public BooleanClause visitFieldExists(FieldExistsContext ctx) {
            return wrap(builder.fieldExists());
        }

        @Override
        public BooleanClause visitWildcard(WildcardContext ctx) {
            return wrap(builder.wildcardQuery(ctx.getText()));
        }

        @Override
        public BooleanClause visitRegex(RegexContext ctx) {
            String regex = ctx.content == null ? "" : ctx.content.getText();
            return wrap(builder.regexQuery(regex));
        }

        private void add(BooleanQuery bq, BooleanClause clause, Occur defaultOccur) {
            if (clause.getQuery() == null) {
                return;
            }
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

    private static class PhraseTermVisitor extends PickLastAggregatingVisitor<PhraseTerm> {
        @Override
        public PhraseTerm visitBasicTerm(BasicTermContext ctx) {
            return new SimpleStringPhraseTerm(getText(ctx));
        }

        @Override
        public PhraseTerm visitPrefix(PrefixContext ctx) {
            return new PrefixPhraseTerm(getText(ctx.TERM()));
        }

        @Override
        public PhraseTerm visitWildcard(WildcardContext ctx) {
            return new WildcardPhraseTerm(getText(ctx));
        }

        @Override
        public PhraseTerm visitFuzzy(FuzzyContext ctx) {
            float fuzziness = Float.NEGATIVE_INFINITY;
            if (ctx.fuzziness != null) {
                if (ctx.fuzziness.basicTerm() != null) {
                    // This looks like garbage in place of the slop. Icky.
                    return new SimpleStringPhraseTerm(ctx.getText());
                }
                fuzziness = Float.parseFloat(ctx.fuzziness.getText());
            }
            return new FuzzyPhraseTerm(getText(ctx.TERM()), fuzziness);
        }

        private String getText(ParseTree ctx) {
            return ctx.getText().replace("\\\"", "\"");
        }
    }

    private static class PickLastAggregatingVisitor<T> extends QueryParserBaseVisitor<T> {
        /**
         * Control default aggregation of results from multi-child nodes in the
         * parse tree. The ANTLR default is to always take the result from the
         * last node but we switch to taking the the last <strong>non
         * null</string> result.
         */
        @Override
        protected T aggregateResult(T aggregate, T nextResult) {
            return nextResult == null ? aggregate : nextResult;
        }
    }

    private static List<FieldReference> fieldsFromContext(FieldsContext ctx) {
        List<FieldReference> fields = new ArrayList<>();
        for (FieldContext field : ctx.field()) {
            float boost = 1;
            if (field.boost != null) {
                boost = Float.parseFloat(field.boost.getText());
            }
            fields.add(new FieldReference(field.fieldName().getText(), boost));
        }
        return fields;
    }
}
