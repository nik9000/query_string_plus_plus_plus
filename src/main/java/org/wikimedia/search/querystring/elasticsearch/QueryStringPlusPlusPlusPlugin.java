package org.wikimedia.search.querystring.elasticsearch;

import org.elasticsearch.indices.query.IndicesQueriesModule;
import org.elasticsearch.plugins.AbstractPlugin;

public class QueryStringPlusPlusPlusPlugin extends AbstractPlugin {

    @Override
    public String description() {
        return "Elasticsearch Highlighter designed for easy tinkering.";
    }

    @Override
    public String name() {
        return "experimental highlighter";
    }

    /**
     * Register our parser.
     */
    public void onModule(IndicesQueriesModule module) {
        module.addQuery(new QueryStringPlusPlusPlusParser());
    }
}
