package org.wikimedia.search.querystring.query;

import java.util.List;

import org.apache.lucene.search.Query;

/**
 * Wraps a QueryBuilder adding some more methods who's parameters take default
 * values.
 */
public class DefaultingQueryBuilder implements QueryBuilder {
    public static class Settings {
        private int phraseSlop = 0;
        private float similaritySpec = .6f;

        public int getPhraseSlop() {
            return phraseSlop;
        }

        public void setPhraseSlop(int phraseSlop) {
            this.phraseSlop = phraseSlop;
        }

        public float getSimilaritySpec() {
            return similaritySpec;
        }

        public void setSimilaritySpec(float similaritySpec) {
            this.similaritySpec = similaritySpec;
        }
    }

    private final Settings settings;
    private final QueryBuilder delegate;

    public DefaultingQueryBuilder(Settings settings, QueryBuilder delegate) {
        this.settings = settings;
        this.delegate = delegate;
    }

    public Query fuzzyQuery(String term) {
        return fuzzyQuery(term, settings.similaritySpec);
    }

    public Query phraseQuery(List<String> terms, boolean useQuotedTerm) {
        return phraseQuery(terms, settings.phraseSlop, useQuotedTerm);
    }

    /**
     * Creates a copy of this builder that uses the passed in fields.
     */
    @Override
    public DefaultingQueryBuilder forFields(List<FieldUsage> fields) {
        return new DefaultingQueryBuilder(settings, delegate.forFields(fields));
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    // These are all auto-generated and just delegate to delegate
    @Override
    public Query termQuery(String term) {
        return delegate.termQuery(term);
    }

    @Override
    public Query phraseQuery(List<String> terms, int slop, boolean useQuotedTerm) {
        return delegate.phraseQuery(terms, slop, useQuotedTerm);
    }

    @Override
    public Query fuzzyQuery(String term, float similaritySpec) {
        return delegate.fuzzyQuery(term, similaritySpec);
    }

    @Override
    public Query prefixQuery(String term) {
        return delegate.prefixQuery(term);
    }

    @Override
    public Query wildcardQuery(String term) {
        return delegate.wildcardQuery(term);
    }

    @Override
    public Query matchNone() {
        return delegate.matchNone();
    }

    @Override
    public Query matchAll() {
        return delegate.matchAll();
    }
}
