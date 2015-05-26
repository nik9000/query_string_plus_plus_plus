package org.wikimedia.search.querystring.elasticsearch;

import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.query.QueryParseContext;
import org.wikimedia.search.querystring.FieldResolver;

public class ElasticsearchFieldResolver implements FieldResolver {
    private final QueryParseContext context;

    public ElasticsearchFieldResolver(QueryParseContext context) {
        this.context = context;
    }

    @Override
    public Tuple<String, Analyzer> resolve(String field) {
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
            String name = smart.mapper().names().indexName();
            return new Tuple<>(name, smart.searchAnalyzer());
        }
        return null;
    }

    @Override
    public Analyzer defaultStandardSearchAnalyzer() {
        return context.mapperService().searchAnalyzer();
    }

    @Override
    public Analyzer defaultPreciseSearchAnalyzer() {
        return context.mapperService().searchQuoteAnalyzer();
    }
}
