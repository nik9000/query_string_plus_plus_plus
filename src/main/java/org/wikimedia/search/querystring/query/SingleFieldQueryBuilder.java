package org.wikimedia.search.querystring.query;

import static java.lang.Math.min;

import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.elasticsearch.index.query.support.QueryParsers;

public class SingleFieldQueryBuilder implements FieldQueryBuilder {
    private final FieldUsage field;
    private final Settings settings;

    public SingleFieldQueryBuilder(FieldUsage field, Settings settings) {
        this.field = field;
        this.settings = settings;
    }

    @Override
    public Query termQuery(String term) {
        // TODO its really wrong to make a _term_ query here
        /*
         * We should analyze the term and handle things like multiple tokens
         * coming out of that term - similar to what query_string does now.
         */
        return new TermQuery(term(term));
    }

    @Override
    public Query phraseQuery(List<String> terms, int slop, boolean useQuotedTerm) {
        slop = min(slop, settings.getMaxPhraseSlop());
        if (terms.size() == 1) {
            return new TermQuery(quotedTerm(terms.get(0)));
        }
        PhraseQuery pq = new PhraseQuery();
        pq.setSlop(slop);
        // TODO multi-term queries inside of phrase queries
        for (String term : terms) {
            pq.add(useQuotedTerm ? quotedTerm(term) : term(term));
        }
        return pq;
    }

    @Override
    public Query fuzzyQuery(String term, float similaritySpec) {
        @SuppressWarnings("deprecation")
        int numEdits = FuzzyQuery.floatToEdits(similaritySpec, term.codePointCount(0, term.length()));
        if (numEdits == 0) {
            return termQuery(term);
        }
        FuzzyQuery query = new FuzzyQuery(term(term), numEdits, settings.getFuzzyPrefixLength(),
                settings.getFuzzyMaxExpansions(), false);
        QueryParsers.setRewriteMethod(query, settings.getRewriteMethod());
        return query;
    }

    @Override
    public Query prefixQuery(String term) {
        PrefixQuery query = new PrefixQuery(term(term));
        QueryParsers.setRewriteMethod(query, settings.getRewriteMethod());
        return query;
    }

    @Override
    public Query wildcardQuery(String term) {
        WildcardQuery query = new WildcardQuery(term(term));
        QueryParsers.setRewriteMethod(query, settings.getRewriteMethod());
        return query;
    }

    public Term term(String term) {
        return new Term(field.getField().getUnquoted(), term);
    }

    public Term quotedTerm(String term) {
        return new Term(field.getField().getQuoted(), term);
    }
}
