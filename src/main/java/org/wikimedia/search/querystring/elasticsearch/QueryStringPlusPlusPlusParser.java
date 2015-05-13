package org.wikimedia.search.querystring.elasticsearch;

import java.io.IOException;

import org.apache.lucene.search.Query;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.index.query.QueryParsingException;

public class QueryStringPlusPlusPlusParser implements QueryParser {
    public static final String[] NAMES = new String[] { "query_string_plus_plus_plus", "queryStringPlusPlusPlus" };

    @Override
    public String[] names() {
        return NAMES;
    }

    @Override
    public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        // TODO Auto-generated method stub
        return null;
    }

}
