package org.wikimedia.search.querystring.query;

import static java.lang.Math.min;
import static org.elasticsearch.common.base.MoreObjects.firstNonNull;
import static org.elasticsearch.common.collect.Iterators.singletonIterator;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.index.query.support.QueryParsers;

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
        return termOrPhraseQuery(field.getStandard(), field.getStandardSearchAnalyzer(), singletonIterator(term),
                settings.getTermQueryPhraseSlop());
    }

    @Override
    public Query phraseQuery(List<String> terms, int slop, boolean useQuotedTerm) {
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
        // TODO prefix queries with an empty term are some match_all thing.
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

    private Query termOrPhraseQuery(String field, Analyzer analyzer, Iterator<String> term, int phraseSlop) {
        // TODO position increments!
        TokenStream ts = null;
        try {
            TermToBytesRefAttribute termAtt = null;
            BytesRef analyzedTerm = null;

            Term firstTerm = null;
            PhraseQuery phraseQuery = null;
            while (true) {
                if (ts == null) {
                    if (!term.hasNext()) {
                        if (phraseQuery != null) {
                            return phraseQuery;
                        }
                        if (firstTerm != null) {
                            return new TermQuery(firstTerm);
                        }
                        return null;
                    }
                    ts = analyzer.tokenStream(null, term.next());
                    termAtt = ts.addAttribute(TermToBytesRefAttribute.class);
                    analyzedTerm = termAtt.getBytesRef();
                    ts.reset();
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
                if (firstTerm == null) {
                    // If its the first term lets just store it.
                    firstTerm = new Term(field, BytesRef.deepCopyOf(analyzedTerm));
                    continue;
                }
                if (phraseQuery == null) {
                    // Its the second term - lets build a phrase query for it
                    phraseQuery = new PhraseQuery();
                    phraseQuery.setSlop(phraseSlop);
                    phraseQuery.add(firstTerm);
                    // Note we don't break here - we want to add the next term.
                }
                // We already have the phrase query so lets expand on it.
                phraseQuery.add(new Term(field, BytesRef.deepCopyOf(analyzedTerm)));
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
}
