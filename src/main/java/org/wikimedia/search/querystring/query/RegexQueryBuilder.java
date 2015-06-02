package org.wikimedia.search.querystring.query;

import static org.elasticsearch.common.lucene.search.Queries.newMatchAllQuery;

import java.io.IOException;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.lucene.search.XFilteredQuery;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryParsingException;
import org.wikimedia.search.extra.regex.SourceRegexFilter;
import org.wikimedia.search.extra.regex.SourceRegexFilterParser;
import org.wikimedia.search.extra.util.FieldValues;

public interface RegexQueryBuilder {
    /**
     * RegexQueryBuilder that never builds any queries.
     */
    public static final RegexQueryBuilder NONE = new None();

    boolean parseSetting(String name, XContentParser parser) throws IOException, QueryParsingException;

    /**
     * Build a regex query against field.
     *
     * @return the query or null if the query isn't supported
     */
    Query regexQuery(FieldUsage field, String regex);

    /**
     * Builder that never returns any queries.
     */
    public static class None implements RegexQueryBuilder {
        @Override
        public boolean parseSetting(String name, XContentParser parser) throws IOException, QueryParsingException {
            return false;
        }

        @Override
        public Query regexQuery(FieldUsage field, String regex) {
            return null;
        }
    }

    public static class WikimediaExtraRegexQueryBuilder implements RegexQueryBuilder {
        private final SourceRegexFilter.Settings settings = new SourceRegexFilter.Settings();

        @Override
        public boolean parseSetting(String name, XContentParser parser) throws IOException, QueryParsingException {
            return SourceRegexFilterParser.parseInto(settings, name, parser);
        }

        @Override
        public Query regexQuery(FieldUsage field, String regex) {
            // TODO look this up properly
            FieldValues.Loader loader = FieldValues.loadFromSource();
            // TODO is it always right to load the standard field?
            SourceRegexFilter filter = new SourceRegexFilter(field.getStandard(), field.getNgramField(), regex, loader, settings,
                    field.getNgramFieldGramSize());
            return new XFilteredQuery(newMatchAllQuery(), filter);
        }
    }
}
