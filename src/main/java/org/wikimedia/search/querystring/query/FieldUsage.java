package org.wikimedia.search.querystring.query;

import org.apache.lucene.analysis.Analyzer;

/**
 * Enough information about a field to build any query. Build by calling
 * FieldsHelper.resolve so its safe to assume that aliases have been expanded
 * and the whitelist and blacklist have been checked.
 */
public class FieldUsage extends FieldDefinition {
    private final Analyzer standardSearchAnalyzer;
    private final Analyzer preciseSearchAnalyzer;
    private final Analyzer reversePreciseSearchAnalyzer;
    private final Analyzer prefixPreciseSearchAnalyzer;
    private final float boost;

    public FieldUsage(String standard, Analyzer standardSearchAnalyzer, String precise, Analyzer preciseSearchAnalyzer,
            String reversePrecise, Analyzer reversePreciseSearchAnalyzer, String prefixPrecise, Analyzer prefixPreciseSearchAnalyzer,
            String ngramField, int ngramFieldGramSize, float boost) {
        super(standard, precise, reversePrecise, prefixPrecise, ngramField, ngramFieldGramSize);
        this.standardSearchAnalyzer = standardSearchAnalyzer;
        this.preciseSearchAnalyzer = preciseSearchAnalyzer;
        this.reversePreciseSearchAnalyzer = reversePreciseSearchAnalyzer;
        this.prefixPreciseSearchAnalyzer = prefixPreciseSearchAnalyzer;
        this.boost = boost;
    }

    public Analyzer getStandardSearchAnalyzer() {
        return standardSearchAnalyzer;
    }

    public Analyzer getPreciseSearchAnalyzer() {
        return preciseSearchAnalyzer;
    }

    public Analyzer getReversePreciseSearchAnalyzer() {
        return reversePreciseSearchAnalyzer;
    }

    public Analyzer getPrefixPreciseSearchAnalyzer() {
        return prefixPreciseSearchAnalyzer;
    }

    public float getBoost() {
        return boost;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(super.toString());
        if (boost != 1) {
            b.append('^').append(boost);
        }
        return b.toString();
    }
}
