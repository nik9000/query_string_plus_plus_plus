package org.wikimedia.search.querystring.elasticsearch;

import org.elasticsearch.indices.query.IndicesQueriesModule;
import org.elasticsearch.plugins.AbstractPlugin;

public class QueryStringPlusPlusPlusPlugin extends AbstractPlugin {

    @Override
    public String description() {
        return "Super query_string!";
    }

    @Override
    public String name() {
        return "query string plus plus plus";
    }

    /**
     * Register our parser.
     */
    public void onModule(IndicesQueriesModule module) {
        module.addQuery(new QueryStringPlusPlusPlusParser());
    }
}
