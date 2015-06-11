package org.wikimedia.search.querystring.query.phraseterm;

import org.apache.lucene.search.Query;
import org.wikimedia.search.querystring.query.FieldQueryBuilder;

public class WildcardPhraseTerm extends AbstractPhraseTerm {
    public WildcardPhraseTerm(String string) {
        super(string);
    }

    @Override
    public Query query(FieldQueryBuilder b) {
        return b.wildcardQuery(rawString());
    }
}
