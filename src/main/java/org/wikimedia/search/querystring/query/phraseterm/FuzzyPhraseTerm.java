package org.wikimedia.search.querystring.query.phraseterm;

import org.apache.lucene.search.Query;
import org.wikimedia.search.querystring.query.FieldQueryBuilder;

public class FuzzyPhraseTerm extends AbstractPhraseTerm {
    public FuzzyPhraseTerm(String string) {
        super(string);
    }

    @Override
    public Query query(FieldQueryBuilder b) {
        return b.fuzzyQuery(rawString(), 0);
    }
}
