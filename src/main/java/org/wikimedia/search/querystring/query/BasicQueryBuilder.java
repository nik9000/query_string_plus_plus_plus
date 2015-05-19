package org.wikimedia.search.querystring.query;

import java.util.List;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.lucene.search.Queries;

/**
 * Builds queries, mostly by delegating to a FieldQueryBuilder.
 */
public class BasicQueryBuilder implements QueryBuilder {
    private final FieldQueryBuilder fieldQueryBuilder;

    public BasicQueryBuilder(FieldQueryBuilder fieldQueryBuilder) {
        this.fieldQueryBuilder = fieldQueryBuilder;
    }

    @Override
    public Query matchNone() {
        return Queries.newMatchNoDocsQuery();
    }

    @Override
    public Query matchAll() {
        return Queries.newMatchAllQuery();
    }

    @Override
    public Query termQuery(String term) {
        return fieldQueryBuilder.termQuery(term);
    }

    @Override
    public Query phraseQuery(List<String> terms, int slop, boolean useQuotedTerm) {
        return fieldQueryBuilder.phraseQuery(terms, slop, useQuotedTerm);
    }

    @Override
    public Query fuzzyQuery(String term, float similaritySpec) {
        return fieldQueryBuilder.fuzzyQuery(term, similaritySpec);
    }

    @Override
    public Query prefixQuery(String term) {
        return fieldQueryBuilder.prefixQuery(term);
    }

    @Override
    public Query wildcardQuery(String term) {
        return fieldQueryBuilder.wildcardQuery(term);
    }
}
