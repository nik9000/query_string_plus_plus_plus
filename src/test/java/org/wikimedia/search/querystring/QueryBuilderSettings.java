package org.wikimedia.search.querystring;

import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MultiTermQuery.RewriteMethod;

public class QueryBuilderSettings {
    private final String field;
    private final String quotedField;
    private int defaultPhraseSlop = 0;
    private int maxPhraseSlop = 20;
    private int fuzzyPrefixLength = FuzzyQuery.defaultPrefixLength;
    private RewriteMethod fuzzyRewriteMethod;
    private int fuzzyMaxExpansions = FuzzyQuery.defaultMaxExpansions;

    public QueryBuilderSettings(String field, String quotedField) {
        this.field = field;
        this.quotedField = quotedField;
    }

    public int getDefaultPhraseSlop() {
        return defaultPhraseSlop;
    }

    public void setDefaultPhraseSlop(int defaultPhraseSlop) {
        this.defaultPhraseSlop = defaultPhraseSlop;
    }

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

    public RewriteMethod getFuzzyRewriteMethod() {
        return fuzzyRewriteMethod;
    }

    public void setFuzzyRewriteMethod(RewriteMethod fuzzyRewriteMethod) {
        this.fuzzyRewriteMethod = fuzzyRewriteMethod;
    }

    public String getField() {
        return field;
    }

    public String getQuotedField() {
        return quotedField;
    }

    public int getFuzzyMaxExpansions() {
        return fuzzyMaxExpansions;
    }

    public void setFuzzyMaxExpansions(int fuzzyMaxExpansions) {
        this.fuzzyMaxExpansions = fuzzyMaxExpansions;
    }
}
