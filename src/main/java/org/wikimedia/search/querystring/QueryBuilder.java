package org.wikimedia.search.querystring;

import static java.lang.Math.min;

import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.index.query.support.QueryParsers;

public class QueryBuilder {
    private final QueryBuilderSettings settings;

    public QueryBuilder(QueryBuilderSettings settings) {
        this.settings = settings;
    }

    public Query termQuery(String term) {
        // TODO multi-term handling
        // TODO multi-field handling
        return new TermQuery(term(term));
    }

    public Query phraseQuery(List<String> terms, boolean useQuotedTerm) {
        return phraseQuery(terms, settings.getDefaultPhraseSlop(), useQuotedTerm);
    }

    public Query phraseQuery(List<String> terms, int slop, boolean useQuotedTerm) {
        slop = min(slop, settings.getMaxPhraseSlop());
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

    public Query fuzzyQuery(String term) {
        // TODO .4? configure it.
        return fuzzyQuery(term, .6F);
    }

    public Query fuzzyQuery(String term, float similaritySpec) {
        @SuppressWarnings("deprecation")
        int numEdits = FuzzyQuery.floatToEdits(similaritySpec, term.codePointCount(0, term.length()));
        if (numEdits == 0) {
            return termQuery(term);
        }
        FuzzyQuery query = new FuzzyQuery(new Term(settings.getField(), term), numEdits, settings.getFuzzyPrefixLength(),
                settings.getFuzzyMaxExpansions(), false);
        QueryParsers.setRewriteMethod(query, settings.getRewriteMethod());
        return query;
    }

    public Query prefixQuery(String term) {
        PrefixQuery query = new PrefixQuery(term(term));
        QueryParsers.setRewriteMethod(query, settings.getRewriteMethod());
        return query;
    }

    public Query wildcardQuery(String term) {
        WildcardQuery query = new WildcardQuery(term(term));
        QueryParsers.setRewriteMethod(query, settings.getRewriteMethod());
        return query;
    }

    public Query matchNone() {
        return Queries.newMatchNoDocsQuery();
    }

    public Query matchAll() {
        return Queries.newMatchAllQuery();
    }

    public Term term(String term) {
        return new Term(settings.getField(), term);
    }

    public Term quotedTerm(String term) {
        return new Term(settings.getQuotedField(), term);
    }
}
