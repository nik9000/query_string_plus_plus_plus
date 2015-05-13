package org.wikimedia.search.querystring;

import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

public class QueryBuilder {
    private final String field;
    private final String quotedField;
    private final int defaultPhraseSlop;

    public QueryBuilder(String field, String quotedField, int defaultPhraseSlop) {
        this.field = field;
        this.quotedField = quotedField;
        this.defaultPhraseSlop = defaultPhraseSlop;
    }

    public Query termQuery(String term) {
        // TODO multi-term handling
        // TODO multi-field handling
        return new TermQuery(term(term));
    }

    public Query phraseQuery(List<String> terms) {
        return phraseQuery(terms, defaultPhraseSlop);
    }

    public Query phraseQuery(List<String> terms, int slop) {
        if (terms.size() == 1) {
            return new TermQuery(quotedTerm(terms.get(0)));
        }
        // TODO multi-field handling
        PhraseQuery pq = new PhraseQuery();
        pq.setSlop(slop);
        // TODO multi-term queries inside of phrase queries
        for (String term : terms) {
            // TODO the right field
            pq.add(quotedTerm(term));
        }
        return pq;
    }

    public Term term(String term) {
        return new Term(field, term);
    }

    public Term quotedTerm(String term) {
        return new Term(quotedField, term);
    }
}
