package org.wikimedia.search.querystring.query;

import java.util.List;

import org.apache.lucene.search.Query;

public interface QueryBuilder extends FieldQueryBuilder {
    /**
     * Creates a copy of this builder that uses the passed in fields.
     */
    QueryBuilder forFields(List<FieldDefinition> fields);

    Query matchNone();

    Query matchAll();
}