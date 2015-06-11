package org.wikimedia.search.querystring.query.phraseterm;

import org.apache.lucene.search.Query;
import org.wikimedia.search.querystring.query.FieldQueryBuilder;

public class PrefixPhraseTerm extends AbstractPhraseTerm {
    public PrefixPhraseTerm(String string) {
        super(string);
    }

    @Override
    public Query query(FieldQueryBuilder b) {
        return b.prefixQuery(rawString().substring(0, rawString().length() - 1));
    }
}
