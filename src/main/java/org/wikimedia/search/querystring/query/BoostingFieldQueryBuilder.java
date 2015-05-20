package org.wikimedia.search.querystring.query;

import java.util.List;

import org.apache.lucene.search.Query;

/**
 * Applies boosts queries made by a delegate builder.
 */
public class BoostingFieldQueryBuilder implements FieldQueryBuilder {
    private final FieldQueryBuilder delegate;
    private final float boost;

    public BoostingFieldQueryBuilder(FieldQueryBuilder delegate, float boost) {
        this.delegate = delegate;
        this.boost = boost;
    }

    @Override
    public Query termQuery(String term) {
        return boost(delegate.termQuery(term));
    }

    @Override
    public Query phraseQuery(List<String> terms, int slop, boolean useQuotedTerm) {
        return boost(delegate.phraseQuery(terms, slop, useQuotedTerm));
    }

    @Override
    public Query fuzzyQuery(String term, float similaritySpec) {
        return boost(delegate.fuzzyQuery(term, similaritySpec));
    }

    @Override
    public Query prefixQuery(String term) {
        return boost(delegate.prefixQuery(term));
    }

    @Override
    public Query wildcardQuery(String term) {
        return boost(delegate.wildcardQuery(term));
    }

    private Query boost(Query q) {
        q.setBoost(boost);
        return q;
    }
}
