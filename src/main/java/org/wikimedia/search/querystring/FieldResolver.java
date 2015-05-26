package org.wikimedia.search.querystring;

import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.common.collect.Tuple;

/**
 * Resolves field names.
 */
public interface FieldResolver {
    /**
     * Returns this field's index name and search analyzer or null if the field
     * isn't mapped.
     */
    Tuple<String, Analyzer> resolve(String field);

    Analyzer defaultStandardSearchAnalyzer();

    Analyzer defaultPreciseSearchAnalyzer();

    /**
     * Resolver that never looks for the field, just returns some predefined
     * analyzers.
     */
    public static class NeverFinds implements FieldResolver {
        private final Analyzer standardAnalyzer;
        private final Analyzer preciseAnalyzer;

        public NeverFinds(Analyzer standardAnalyzer, Analyzer preciseAnalyzer) {
            this.standardAnalyzer = standardAnalyzer;
            this.preciseAnalyzer = preciseAnalyzer;
        }

        @Override
        public Tuple<String, Analyzer> resolve(String field) {
            return null;
        }

        @Override
        public Analyzer defaultStandardSearchAnalyzer() {
            return standardAnalyzer;
        }

        @Override
        public Analyzer defaultPreciseSearchAnalyzer() {
            return preciseAnalyzer;
        }
    }
}
