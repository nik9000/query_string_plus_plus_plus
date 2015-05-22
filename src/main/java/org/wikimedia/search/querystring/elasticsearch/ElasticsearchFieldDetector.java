package org.wikimedia.search.querystring.elasticsearch;

import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.QueryParseContext;
import org.wikimedia.search.querystring.FieldDetector;

public class ElasticsearchFieldDetector implements FieldDetector {
    private final QueryParseContext context;

    public ElasticsearchFieldDetector(QueryParseContext context) {
        this.context = context;
    }

    @Override
    public String resolveIndexName(String field) {
        if (field == null) {
            return null;
        }
        MapperService.SmartNameFieldMappers smart = context.smartFieldMappers(field);
        /*
         * Just finding the smart mapping is good enough to know that field was
         * defined in the mapping - meaning its ok to infer that there are
         * probably useful things in there.
         */
        if (smart != null && smart.hasMapper()) {
            // TODO when is this different from field?
            return smart.mapper().names().indexName();
        }
        return null;
    }
}
