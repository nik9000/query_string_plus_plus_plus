package org.wikimedia.search.querystring.query;

import static java.lang.Math.min;
import static org.elasticsearch.common.base.MoreObjects.firstNonNull;
import static org.elasticsearch.common.collect.Iterators.singletonIterator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.spans.FieldMaskingSpanQuery;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper.TopTermsSpanBooleanQueryRewrite;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.base.Joiner;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.index.mapper.internal.FieldNamesFieldMapper;
import org.elasticsearch.index.query.support.QueryParsers;
import org.wikimedia.search.querystring.query.phraseterm.SimpleStringPhraseTerm;

public class SingleFieldQueryBuilder implements FieldQueryBuilder {
    private static final ESLogger log = ESLoggerFactory.getLogger(SingleFieldQueryBuilder.class.getPackage().getName());

    private final FieldUsage field;
    private final Settings settings;

    public SingleFieldQueryBuilder(FieldUsage field, Settings settings) {
        this.field = field;
        this.settings = settings;
    }

    @Override
    public Query termQuery(String term) {
        return termOrPhraseQuery(field.getStandard(), field.getStandardSearchAnalyzer(),
                singletonIterator(new SimpleStringPhraseTerm(term)), settings.getTermQueryPhraseSlop());
    }

    @Override
    public Query phraseQuery(List<PhraseTerm> terms, int slop, boolean useQuotedTerm) {
        slop = min(slop, settings.getMaxPhraseSlop());
        String fieldName;
        Analyzer analyzer;
        if (useQuotedTerm && field.getPrecise() != null) {
            fieldName = field.getPrecise();
            analyzer = field.getPreciseSearchAnalyzer();
        } else {
            fieldName = field.getStandard();
            analyzer = field.getStandardSearchAnalyzer();
        }
        return termOrPhraseQuery(fieldName, analyzer, terms.iterator(), slop);
    }

    @Override
    public Query fuzzyQuery(String term, float similaritySpec) {
        // TODO it should totally be possible to rewrite some fuzzy
        if (similaritySpec == Float.NEGATIVE_INFINITY) {
            similaritySpec = settings.getDefaultFuzzySimilaritySpec();
        }
        @SuppressWarnings("deprecation")
        int numEdits = FuzzyQuery.floatToEdits(similaritySpec, term.codePointCount(0, term.length()));
        if (numEdits == 0) {
            return termQuery(term);
        }
        // TODO the analyzer?
        FuzzyQuery query = new FuzzyQuery(preciseTerm(term), numEdits, settings.getFuzzyPrefixLength(), settings.getFuzzyMaxExpansions(),
                false);
        QueryParsers.setRewriteMethod(query, settings.getRewriteMethod());
        return query;
    }

    @Override
    public Query prefixQuery(String term) {
        if (field.getPrefixPrecise() != null) {
            // TODO the analyzer?
            return new TermQuery(new Term(field.getPrefixPrecise(), term));
        }
        if (!settings.getAllowPrefix()) {
            return termQuery(term + "*");
        }
        // TODO analyzer?
        PrefixQuery query = new PrefixQuery(preciseTerm(term));
        QueryParsers.setRewriteMethod(query, settings.getRewriteMethod());
        return query;
    }

    @Override
    public Query wildcardQuery(String term) {
        boolean hasLeadingWildcard = hasLeadingWildcard(term);
        if (hasLeadingWildcard && field.getReversePrecise() != null) {
            term = new StringBuilder(term).reverse().toString();
            hasLeadingWildcard = hasLeadingWildcard(term);
            if (!settings.getAllowLeadingWildcard() && hasLeadingWildcard) {
                /*
                 * Still has a leading wildcards aren't allowed so fall back to
                 * a term query.
                 */
                return termQuery(term);
            }
            // TODO the analyzer?
            Term reversed = new Term(field.getReversePrecise(), term);
            WildcardQuery query = new WildcardQuery(reversed);
            QueryParsers.setRewriteMethod(query, settings.getRewriteMethod());
            return query;
        }
        if (!settings.getAllowLeadingWildcard() && hasLeadingWildcard) {
            // Leading wildcards aren't allowed so fall back to a term query.
            return termQuery(term);
        }
        // TODO the analyzer?
        WildcardQuery query = new WildcardQuery(preciseTerm(term));
        QueryParsers.setRewriteMethod(query, settings.getRewriteMethod());
        return query;
    }

    @Override
    public Query regexQuery(String regex) {
        Query q = settings.getRegexQueryBuilder().regexQuery(field, regex);
        if (q != null) {
            return q;
        }
        return termQuery("/" + regex + "/");
    }

    @Override
    public Query fieldExists() {
        if (settings.getShouldUseFieldNamesFieldForExists()) {
            return new TermQuery(new Term(FieldNamesFieldMapper.NAME, field.getStandard()));
        }
        return prefixQuery("");
    }

    public Term preciseTerm(String term) {
        return new Term(firstNonNull(field.getPrecise(), field.getStandard()), term);
    }

    @Override
    public String toString() {
        return field.toString();
    }

    private boolean hasLeadingWildcard(String term) {
        return term.charAt(0) == WildcardQuery.WILDCARD_STRING || term.charAt(0) == WildcardQuery.WILDCARD_CHAR;
    }

    private Query termOrPhraseQuery(String field, Analyzer analyzer, Iterator<? extends PhraseTerm> terms, int phraseSlop) {
        // TODO position increments!l

        /*
         * This is kinda complicated, unfortunately. This method's body is
         * devoted to iterating PhraseTerm and either sending the queries that
         * it makes directly to builder or analyzing the terms and shipping them
         * position by position to the builder. This is difficult because the
         * terms sometimes overlap one another.
         *
         * The builder's job is to build the right query for the things its
         * shipped. This is difficult because overlapping terms should be
         * treated differently from special phrase terms from simple
         * non-overlapping terms. And when the terms iterator just contains a
         * single SimpleStringPhraseTerm that analyzes to a single term that
         * term should become a term query instead of a single node phrase query
         * or anything similarly complicated.
         */
        TermOrPhraseOrSpanQueryBuilder builder = new TermOrPhraseOrSpanQueryBuilder(field, phraseSlop);
        TokenStream ts = null;
        try {
            TermToBytesRefAttribute termAtt = null;
            PositionIncrementAttribute posIncAtt = null;
            boolean justStartedANewStream = true;
            BytesRef analyzedTerm = null;

            List<Term> termsAtCurrentPosition = new ArrayList<>();
            while (true) {
                if (ts == null) {
                    if (!terms.hasNext()) {
                        /*
                         * We've run out of terms! Now we have to put a bow on
                         * our query and return it.
                         */
                        return builder.lastPosition(termsAtCurrentPosition);
                    }
                    PhraseTerm term = terms.next();
                    Query queryForTerm = term.query(this);
                    if (queryForTerm != null) {
                        /*
                         * Note that we have to flush the current position or
                         * stuff gets out of order.
                         */
                        builder.position(termsAtCurrentPosition);
                        termsAtCurrentPosition.clear();
                        builder.query(queryForTerm);
                        continue;
                    }
                    ts = analyzer.tokenStream(field, term.rawString());
                    termAtt = ts.addAttribute(TermToBytesRefAttribute.class);
                    posIncAtt = ts.addAttribute(PositionIncrementAttribute.class);
                    analyzedTerm = termAtt.getBytesRef();
                    justStartedANewStream = true;
                    ts.reset();
                } else {
                    justStartedANewStream = false;
                }
                if (!ts.incrementToken()) {
                    ts.end();
                    ts.close();
                    ts = null;
                    continue;
                }
                termAtt.fillBytesRef();
                if (log.isTraceEnabled()) {
                    log.trace("Term:  {}", analyzedTerm.utf8ToString());
                }
                if (analyzedTerm.length == 0) {
                    /*
                     * Token is empty so we can't really search for it - try for
                     * the next one.
                     */
                    continue;
                }
                /*
                 * Ok - analyzedTerm now contains an actual, non empty term.
                 * Time to do something with it.
                 */
                Term currentTerm = new Term(field, BytesRef.deepCopyOf(analyzedTerm));
                if (justStartedANewStream || posIncAtt.getPositionIncrement() != 0) {
                    builder.position(termsAtCurrentPosition);
                    termsAtCurrentPosition.clear();
                }
                termsAtCurrentPosition.add(currentTerm);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unexpected IOException from Lucene when they shouldn't be possible.", e);
        } finally {
            if (ts != null) {
                try {
                    ts.end();
                    ts.close();
                } catch (IOException e) {
                    throw new RuntimeException("Unexpected IOException from Lucene when they shouldn't be possible.", e);
                }
            }
        }
    }

    private class TermOrPhraseOrSpanQueryBuilder {
        private final String fieldName;
        private final int phraseSlop;
        private PhraseQuery phraseQuery;
        private MultiPhraseQuery multiPhraseQuery;
        private List<SpanQuery> spanNear;

        public TermOrPhraseOrSpanQueryBuilder(String fieldName, int phraseSlop) {
            this.fieldName = fieldName;
            this.phraseSlop = phraseSlop;
        }

        /**
         * The next position can contain any of the matching terms.
         */
        private void position(List<Term> terms) {
            switch (terms.size()) {
            case 0:
                return;
            case 1:
                if (spanNear != null) {
                    spanNear.add(fixField(new SpanTermQuery(terms.get(0))));
                    return;
                }
                if (multiPhraseQuery != null) {
                    multiPhraseQuery.add(terms.get(0));
                    return;
                }
                if (phraseQuery == null) {
                    phraseQuery = new PhraseQuery();
                    phraseQuery.setSlop(phraseSlop);
                }
                phraseQuery.add(terms.get(0));
                return;
            default:
                if (spanNear != null) {
                    addTermsToSpanNear(terms);
                    return;
                }
                if (multiPhraseQuery == null) {
                    multiPhraseQuery = new MultiPhraseQuery();
                    multiPhraseQuery.setSlop(phraseSlop);
                    if (phraseQuery != null) {
                        // TODO copy the phrase query into the multi
                        // phrase query
                        phraseQuery = null;
                    }
                }
                multiPhraseQuery.add(terms.toArray(new Term[terms.size()]));
                return;
            }
        }

        /**
         * The next position should match this query.
         */
        public void query(Query query) {
            // TODO be careful with fields and rewrites
            if (spanNear == null) {
                spanNear = new ArrayList<>();
                if (multiPhraseQuery != null) {
                    List<Term[]> terms = multiPhraseQuery.getTermArrays();
                    for (int i = 0; i < terms.size(); i++) {
                        Term[] termsAtPosition = terms.get(i);
                        SpanQuery[] clauses = new SpanQuery[termsAtPosition.length];
                        for (int j = 0; j < termsAtPosition.length; j++) {
                            clauses[j] = fixField(new SpanTermQuery(termsAtPosition[j]));
                        }
                        spanNear.add(new SpanOrQuery(clauses));
                    }
                    multiPhraseQuery = null;
                } else if (phraseQuery != null) {
                    Term[] terms = phraseQuery.getTerms();
                    for (int i = 0; i < terms.length; i++) {
                        // TODO position increment differences
                        spanNear.add(fixField(new SpanTermQuery(terms[i])));
                    }
                    phraseQuery = null;
                }
            }
            spanNear.add(fixField(spanify(query)));
        }

        private SpanQuery spanify(Query query) {
            if (query instanceof SpanQuery) {
                return (SpanQuery) query;
            }
            if (query instanceof MultiTermQuery) {
                MultiTermQuery mquery = (MultiTermQuery) query;
                mquery.setRewriteMethod(new TopTermsSpanBooleanQueryRewrite(settings.getFuzzyMaxExpansions()));
                return new SpanMultiTermQueryWrapper<>(mquery);
            }
            if (query instanceof TermQuery) {
                return new SpanTermQuery(((TermQuery) query).getTerm());
            }
            throw new UnsupportedOperationException("Don't know how to convert this query into a span query of type " + query.getClass()
                    + ":  " + query);
        }

        /**
         * The last position can contain any of the matching terms. We're done
         * with this builder - return the query that we built.
         */
        public Query lastPosition(List<Term> terms) {
            /*
             * There are tons of cases here making this all kinds of complicated
             * but the general goal is to return the least complicated query we
             * can. This is more "fun" because in the case this this is a single
             * position.
             */
            switch (terms.size()) {
            case 0:
                /*
                 * Our last position wasn't interesting so lets just return what
                 * we have - and if we have nothing we'll just return null. That
                 * happens when you send a stopword or punctuation only through
                 * the analyzer.
                 */
                if (spanNear != null) {
                    return new SpanNearQuery(spanNear.toArray(new SpanQuery[spanNear.size()]), phraseSlop, true, false);
                }
                return multiPhraseQuery != null ? multiPhraseQuery : phraseQuery;
            case 1:
                if (spanNear != null) {
                    spanNear.add(fixField(new SpanTermQuery(terms.get(0))));
                    try {
                        return new SpanNearQuery(spanNear.toArray(new SpanQuery[spanNear.size()]), phraseSlop, true, false);
                    } catch (IllegalArgumentException e) {
                        if (!e.getMessage().equals("Clauses must have same field.")) {
                            throw e;
                        }
                        /*
                         * The error message Lucene throws here isn't great so
                         * we try to enrich it.
                         */
                        throw new IllegalArgumentException("Bad span query. Clauses:  " + Joiner.on(' ').join(spanNear), e);
                    }
                }
                if (multiPhraseQuery != null) {
                    multiPhraseQuery.add(terms.get(0));
                    return multiPhraseQuery;
                }
                if (phraseQuery != null) {
                    phraseQuery.add(terms.get(0));
                    return phraseQuery;
                }
                return new TermQuery(terms.get(0));
            default:
                if (spanNear != null) {
                    addTermsToSpanNear(terms);
                    return new SpanNearQuery(spanNear.toArray(new SpanQuery[spanNear.size()]), phraseSlop, true, false);
                }
                if (multiPhraseQuery != null) {
                    multiPhraseQuery.add(terms.toArray(new Term[terms.size()]));
                    return multiPhraseQuery;
                }
                if (phraseQuery != null) {
                    multiPhraseQuery = new MultiPhraseQuery();
                    multiPhraseQuery.setSlop(phraseSlop);
                    if (phraseQuery != null) {
                        // TODO copy the phrase query into the multi
                        // phrase query
                    }
                    return multiPhraseQuery;
                }
                BooleanQuery bq = new BooleanQuery();
                bq.setMinimumNumberShouldMatch(1);
                for (Term termAtCurrentPosition : terms) {
                    bq.add(new TermQuery(termAtCurrentPosition), Occur.SHOULD);
                }
                return bq;
            }
        }

        private void addTermsToSpanNear(List<Term> terms) {
            List<SpanQuery> clauses = new ArrayList<>();
            for (Term term : terms) {
                clauses.add(fixField(new SpanTermQuery(term)));
            }
            spanNear.add(new SpanOrQuery(clauses.toArray(new SpanQuery[clauses.size()])));
        }

        /**
         * Span queries must work around the same field - but we are pretty free
         * with substituting precise and prefix, etc.
         */
        private SpanQuery fixField(SpanQuery query) {
            // TODO what if positions don't line up
            if (query.getField().equals(fieldName)) {
                return query;
            }
            return new FieldMaskingSpanQuery(query, fieldName);
        }
    }
}
