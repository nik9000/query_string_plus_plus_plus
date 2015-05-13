package org.wikimedia.search.querystring;

import static java.lang.Math.min;

import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

public class QueryBuilder {
    public static Builder builder() {
        return new Builder();
    }
    public static class Builder {
        private int defaultPhraseSlop = 0;
        private int maxPhraseSlop = 20;

        public QueryBuilder build(String field, String quotedField) {
            return new QueryBuilder(field, quotedField, this);
        }
        public void setDefaultPhraseSlop(int defaultPhraseSlop) {
            this.defaultPhraseSlop = defaultPhraseSlop;
        }
        public void setMaxPhraseSlop(int maxPhraseSlop) {
            this.maxPhraseSlop = maxPhraseSlop;
        }
    }
    private final String field;
    private final String quotedField;
    private final int defaultPhraseSlop;
    private final int maxPhraseSlop;

    private QueryBuilder(String field, String quotedField, Builder b) {
        this.field = field;
        this.quotedField = quotedField;
        defaultPhraseSlop = b.defaultPhraseSlop;
        maxPhraseSlop = b.maxPhraseSlop;
    }

    public Query termQuery(String term) {
        // TODO multi-term handling
        // TODO multi-field handling
        return new TermQuery(term(term));
    }

    public Query phraseQuery(List<String> terms, boolean useQuotedTerm) {
        return phraseQuery(terms, defaultPhraseSlop, useQuotedTerm);
    }

    public Query phraseQuery(List<String> terms, int slop, boolean useQuotedTerm) {
        slop = min(slop, maxPhraseSlop);
        if (terms.size() == 1) {
            return new TermQuery(quotedTerm(terms.get(0)));
        }
        // TODO multi-field handling
        PhraseQuery pq = new PhraseQuery();
        pq.setSlop(slop);
        // TODO multi-term queries inside of phrase queries
        for (String term : terms) {
            pq.add(useQuotedTerm ? quotedTerm(term) : term(term));
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
