package org.wikimedia.search.querystring.query;

import org.apache.lucene.search.Query;

/**
 * Phrase terms are generally just strings but sometimes they have special
 * meaning and can construct a query directly. In that case the query changes
 * from a regular old phrase query to a span query.
 */
public interface PhraseTerm {
    /**
     * The string representation of the phrase term. Never returns null because
     * there is always a string that the phrase was parsed from but if query
     * returns a query then you should use that instead.
     */
    public String rawString();

    /**
     * The query that this phrase term represents or null if its raw string
     * should just be analyzed.
     */
    public Query query(FieldQueryBuilder b);
}
