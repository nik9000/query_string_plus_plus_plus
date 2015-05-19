package org.wikimedia.search.querystring.query;

import java.util.List;

import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MultiTermQuery.RewriteMethod;
import org.apache.lucene.search.Query;

/**
 * Builds queries that reference one or more fields.
 */
public interface FieldQueryBuilder {
    Query termQuery(String term);

    Query phraseQuery(List<String> terms, int slop, boolean useQuotedTerm);

    Query fuzzyQuery(String term, float similaritySpec);

    Query prefixQuery(String term);

    Query wildcardQuery(String term);

    /**
     * Settings used by FieldQueryBuilders.
     */
    public static class Settings {
        private int maxPhraseSlop = 20;
        private int fuzzyPrefixLength = FuzzyQuery.defaultPrefixLength;
        private RewriteMethod rewriteMethod;
        private int fuzzyMaxExpansions = FuzzyQuery.defaultMaxExpansions;

        public int getMaxPhraseSlop() {
            return maxPhraseSlop;
        }

        public void setMaxPhraseSlop(int maxPhraseSlop) {
            this.maxPhraseSlop = maxPhraseSlop;
        }

        public int getFuzzyPrefixLength() {
            return fuzzyPrefixLength;
        }

        public void setFuzzyPrefixLength(int fuzzyPrefixLength) {
            this.fuzzyPrefixLength = fuzzyPrefixLength;
        }

        public RewriteMethod getRewriteMethod() {
            return rewriteMethod;
        }

        public void setRewriteMethod(RewriteMethod rewriteMethod) {
            this.rewriteMethod = rewriteMethod;
        }

        public int getFuzzyMaxExpansions() {
            return fuzzyMaxExpansions;
        }

        public void setFuzzyMaxExpansions(int fuzzyMaxExpansions) {
            this.fuzzyMaxExpansions = fuzzyMaxExpansions;
        }
    }
}