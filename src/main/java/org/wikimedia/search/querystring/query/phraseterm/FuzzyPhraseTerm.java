package org.wikimedia.search.querystring.query.phraseterm;

import org.apache.lucene.search.Query;
import org.wikimedia.search.querystring.query.FieldQueryBuilder;

public class FuzzyPhraseTerm extends AbstractPhraseTerm {
    private final float fuzziness;

    public FuzzyPhraseTerm(String string, float fuzziness) {
        super(string);
        this.fuzziness = fuzziness;
    }

    @Override
    public Query query(FieldQueryBuilder b) {
        return b.fuzzyQuery(rawString(), fuzziness);
    }
}
