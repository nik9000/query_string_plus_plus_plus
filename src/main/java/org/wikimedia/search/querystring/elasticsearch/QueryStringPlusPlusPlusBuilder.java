package org.wikimedia.search.querystring.elasticsearch;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BaseQueryBuilder;
import org.elasticsearch.index.query.BoostableQueryBuilder;
import org.wikimedia.search.querystring.query.FieldDefinition;

public class QueryStringPlusPlusPlusBuilder extends BaseQueryBuilder implements BoostableQueryBuilder<QueryStringPlusPlusPlusBuilder> {
    private final String fields;
    private final String query;
    private final Map<String, String> aliases = new HashMap<>();
    private final Map<String, FieldDefinition> fieldDefinitions = new HashMap<>();
    private Boolean defaultIsAnd;
    private Boolean emptyIsMatchAll;
    private Boolean whitelistDefault;
    private Boolean whitelistAll;

    private Float boost;

    public QueryStringPlusPlusPlusBuilder(String fields, String query) {
        this.fields = fields;
        this.query = query;
    }

    /**
     * Add a field alias. Aliases are resolved before definitions.
     *
     * @param to Alias list in the form
     *            <code>name(^boost)?(, ?name(^boost)?)*</code>
     */
    public QueryStringPlusPlusPlusBuilder alias(String from, String to) {
        aliases.put(from, to);
        return this;
    }

    /**
     * Define how a field name is resolved to actual fields for query. Useful
     * for pointing "quoted terms" to a less-stemmed field.
     */
    public QueryStringPlusPlusPlusBuilder define(String field, FieldDefinition definition) {
        fieldDefinitions.put(field, definition);
        return this;
    }

    /**
     * When two terms are next to eachother without any explicit operator they
     * should both be required.
     */
    public QueryStringPlusPlusPlusBuilder defaultIsAnd() {
        defaultIsAnd = true;
        return this;
    }

    /**
     * When two terms are next to eachother without any explicit operator then
     * either one is required.
     */
    public QueryStringPlusPlusPlusBuilder defaultIsOr() {
        defaultIsAnd = false;
        return this;
    }

    /**
     * When sent an empty query return all documents.
     */
    public QueryStringPlusPlusPlusBuilder emptyIsMatchAll() {
        emptyIsMatchAll = true;
        return this;
    }

    /**
     * When sent an empty query return no results.
     */
    public QueryStringPlusPlusPlusBuilder emptyIsMatchNone() {
        emptyIsMatchAll = false;
        return this;
    }

    /**
     * Should the fields that are searched by default be whitelisted so users can search them explicitly.
     */
    public QueryStringPlusPlusPlusBuilder whitelistDefault(boolean whitelistDefault) {
        this.whitelistDefault = whitelistDefault;
        return this;
    }

    /**
     * Should all not explicitly blacklisted fields be whitelisted? Note that
     * setting this to true for user written queries can be dangerous. Think
     * this through. Are you sure?
     */
    public QueryStringPlusPlusPlusBuilder whitelistAll(boolean whitelistAll) {
        this.whitelistAll = whitelistAll;
        return this;
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
        if (aliases.isEmpty() && fieldDefinitions.isEmpty() && whitelistDefault == null && whitelistAll == null) {
            builder.field("fields", fields);
        } else {
            builder.startObject("fields");
            builder.field("default", fields);
            if (!aliases.isEmpty()) {
                builder.field("aliases", aliases);
            }
            if (!fieldDefinitions.isEmpty()) {
                builder.startObject("definitions");
                for (Map.Entry<String, FieldDefinition> entry : fieldDefinitions.entrySet()) {
                    String name = entry.getKey();
                    FieldDefinition definition = entry.getValue();
                    builder.startObject(name);
                    if (!name.equals(definition.getUnquoted())) {
                        builder.field("unquoted", definition.getUnquoted());
                    }
                    if (!name.equals(definition.getQuoted())) {
                        builder.field("quoted", definition.getQuoted());
                    }
                    builder.endObject();
                }
                builder.endObject();
            }
            if (whitelistDefault != null) {
                builder.field("whitelist_default", whitelistDefault);
            }
            if (whitelistAll != null) {
                builder.field("whitelist_all", whitelistAll);
            }
            builder.endObject();
        }
        if (boost != null) {
            builder.field("boost", boost);
        }
        if (defaultIsAnd != null) {
            builder.field("default_operator", defaultIsAnd ? "and" : "or");
        }
        if (emptyIsMatchAll != null) {
            builder.field("empty", emptyIsMatchAll ? "match_all" : "match_none");
        }
        builder.endObject();
    }
}
