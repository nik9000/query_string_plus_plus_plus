package org.wikimedia.search.querystring.query;

import org.apache.lucene.search.Query;

public interface QueryBuilder extends FieldQueryBuilder {
    Query matchNone();

    Query matchAll();
}