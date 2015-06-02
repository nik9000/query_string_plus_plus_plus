package org.wikimedia.search.querystring.query;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.lucene.search.Queries;

/**
 * Builds queries, mostly by delegating to a FieldQueryBuilder.
 */
public class BasicQueryBuilder implements QueryBuilder {
    private final FieldQueryBuilder fieldQueryBuilder;
    private final FieldQueryBuilder.Settings fieldQuerySettings;

    public BasicQueryBuilder(FieldQueryBuilder.Settings fieldQuerySettings, List<FieldUsage> fields) {
        this.fieldQuerySettings = fieldQuerySettings;
        if (fields.size() == 1) {
            fieldQueryBuilder = buildFieldQueryBuilder(fields.get(0));
        } else {
            List<FieldQueryBuilder> fieldBuilders = new ArrayList<>();
            for (FieldUsage field : fields) {
                fieldBuilders.add(buildFieldQueryBuilder(field));
            }
            fieldQueryBuilder = new MultiFieldQueryBuilder(fieldBuilders);
        }
    }

    @Override
    public QueryBuilder forFields(List<FieldUsage> fields) {
        return new BasicQueryBuilder(fieldQuerySettings, fields);
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

    @Override
    public Query regexQuery(String regex) {
        return fieldQueryBuilder.regexQuery(regex);
    }

    @Override
    public Query fieldExists() {
        return fieldQueryBuilder.fieldExists();
    }

    @Override
    public String toString() {
        return fieldQueryBuilder.toString();
    }

    /**
     * Builds the field queries based on field definitions.
     */
    private FieldQueryBuilder buildFieldQueryBuilder(FieldUsage field) {
        FieldQueryBuilder b = new SingleFieldQueryBuilder(field, fieldQuerySettings);
        if (field.getBoost() != 1) {
            b = new BoostingFieldQueryBuilder(b, field.getBoost());
        }
        return b;
    }
}
