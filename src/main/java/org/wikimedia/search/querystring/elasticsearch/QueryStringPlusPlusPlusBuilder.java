package org.wikimedia.search.querystring.elasticsearch;

import java.io.IOException;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BaseQueryBuilder;
import org.elasticsearch.index.query.BoostableQueryBuilder;

public class QueryStringPlusPlusPlusBuilder extends BaseQueryBuilder implements BoostableQueryBuilder<QueryStringPlusPlusPlusBuilder> {
    private final String fields;
    private final String query;

    private Float boost;

    public QueryStringPlusPlusPlusBuilder(String fields, String query) {
        this.fields = fields;
        this.query = query;
    }

    @Override
    public QueryStringPlusPlusPlusBuilder boost(float boost) {
        this.boost = boost;
        return this;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(QueryStringPlusPlusPlusParser.NAMES[0]);

        builder.field("query", query);
        builder.field("fields", fields);
        if (boost != null) {
            builder.field("boost", boost);
        }
        builder.endObject();
    }
}
