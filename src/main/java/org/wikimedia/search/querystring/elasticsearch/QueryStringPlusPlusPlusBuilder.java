package org.wikimedia.search.querystring.elasticsearch;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BaseQueryBuilder;
import org.elasticsearch.index.query.BoostableQueryBuilder;
import org.wikimedia.search.querystring.query.FieldDefinition;

public class QueryStringPlusPlusPlusBuilder extends BaseQueryBuilder implements BoostableQueryBuilder<QueryStringPlusPlusPlusBuilder> {
    private final String fields;
    private final String query;
    private final Map<String, String> aliases = new HashMap<>();
    private final Map<String, FieldDefinition> fieldDefinitions = new HashMap<>();
    private final Set<String> whitelist = new HashSet<>();
    private final Set<String> blacklist = new HashSet<>();
    private Boolean defaultIsAnd;
    private Boolean emptyIsMatchAll;
    private Boolean whitelistDefault;
    private Boolean whitelistAll;
    private Boolean allowLeadingWildcard;

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
     * for pointing "quoted terms" to a more precisely analyzed field or to
     * enable the reverse field optimization for wildcard matching.
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
     * Should the fields that are searched by default be whitelisted so users
     * can search them explicitly.
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

    /**
     * Blacklist a field so users will never be able to explicitly search for it
     * even if it is whitelisted.
     */
    public QueryStringPlusPlusPlusBuilder blacklist(String field) {
        blacklist.add(field);
        return this;
    }

    /**
     * Whitelist a field so users will be able to explicitly search for it if it
     * hasn't been blacklisted. Note that if whitelistDefault is true (and it is
     * by default) then all the fields that are searched by default are
     * automatically whitelisted.
     */
    public QueryStringPlusPlusPlusBuilder whitelist(String field) {
        whitelist.add(field);
        return this;
    }

    /**
     * Should queries allow a leading wildcard?
     */
    public QueryStringPlusPlusPlusBuilder allowLeadingWildcard(boolean allowLeadingWildcard) {
        this.allowLeadingWildcard = allowLeadingWildcard;
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
        if (aliases.isEmpty() && fieldDefinitions.isEmpty() && whitelistDefault == null && whitelistAll == null && whitelist.isEmpty()
                && blacklist.isEmpty()) {
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
                    if (!name.equals(definition.getStandard())) {
                        builder.field("standard", definition.getStandard());
                    }
                    if (!name.equals(definition.getPrecise())) {
                        builder.field("precise", definition.getPrecise());
                    }
                    if (!name.equals(definition.getReversePrecise())) {
                        builder.field("reverse_precise", definition.getReversePrecise());
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
            if (!blacklist.isEmpty()) {
                builder.field("blacklist", blacklist);
            }
            if (!whitelist.isEmpty()) {
                builder.field("whitelist", whitelist);
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
        if (allowLeadingWildcard != null) {
            builder.field("allow_leading_wildcard", allowLeadingWildcard);
        }
        builder.endObject();
    }
}
