package org.wikimedia.search.querystring.query.phraseterm;

import org.apache.lucene.search.Query;
import org.wikimedia.search.querystring.query.FieldQueryBuilder;

public class SimpleStringPhraseTerm extends AbstractPhraseTerm {
    public SimpleStringPhraseTerm(String string) {
        super(string);
    }

    @Override
    public Query query(FieldQueryBuilder b) {
        return null;
    }
}
