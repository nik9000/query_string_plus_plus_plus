package org.wikimedia.search.querystring.elasticsearch;

import static org.elasticsearch.common.xcontent.XContentParser.Token.END_ARRAY;
import static org.elasticsearch.common.xcontent.XContentParser.Token.END_OBJECT;
import static org.elasticsearch.common.xcontent.XContentParser.Token.FIELD_NAME;
import static org.elasticsearch.common.xcontent.XContentParser.Token.START_ARRAY;
import static org.elasticsearch.common.xcontent.XContentParser.Token.START_OBJECT;
import static org.wikimedia.search.querystring.QueryParserHelper.parseFields;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.base.MoreObjects;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.index.query.QueryParsingException;
import org.wikimedia.search.querystring.FieldsHelper;
import org.wikimedia.search.querystring.FieldsHelper.UnauthorizedAction;
import org.wikimedia.search.querystring.QueryParserHelper;
import org.wikimedia.search.querystring.query.BasicQueryBuilder;
import org.wikimedia.search.querystring.query.DefaultingQueryBuilder;
import org.wikimedia.search.querystring.query.FieldDefinition;
import org.wikimedia.search.querystring.query.FieldQueryBuilder;
import org.wikimedia.search.querystring.query.FieldReference;
import org.wikimedia.search.querystring.query.FieldUsage;

/**
 * Parses QueryStringPlusPlusPlus.
 */
public class QueryStringPlusPlusPlusParser implements QueryParser {
    public static final String[] NAMES = new String[] { "qsppp", "query_string_plus_plus_plus", "queryStringPlusPlusPlus" };
    private static final ESLogger log = ESLoggerFactory.getLogger(QueryStringPlusPlusPlusParser.class.getPackage().getName());

    @Override
    public String[] names() {
        return NAMES;
    }

    @Override
    public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        DefaultingQueryBuilder.Settings defaultSettings = new DefaultingQueryBuilder.Settings();
        FieldQueryBuilder.Settings fieldSettings = new FieldQueryBuilder.Settings();
        FieldsHelper fieldsHelper = new FieldsHelper(new ElasticsearchFieldResolver(parseContext));
        boolean defaultIsAnd = true;
        boolean emptyIsMatchAll = true;
        UnauthorizedAction defaultFieldUnauthorizedAction = UnauthorizedAction.WHITELIST;
        Float boost = null;
        String fields = null;
        // TODO quoted fields.
        String query = null;

        XContentParser parser = parseContext.parser();
        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                switch (currentFieldName) {
                case "query":
                    query = parser.text();
                    break;
                case "fields":
                case "field":
                    fields = parser.text();
                    break;
                case "default_operator":
                case "defaultOperator":
                    defaultIsAnd = "and".equals(parser.text().toLowerCase(Locale.ROOT));
                    break;
                case "empty":
                    emptyIsMatchAll = "match_all".equals(parser.text().toLowerCase(Locale.ROOT));
                    break;
                case "boost":
                    boost = parser.floatValue();
                    break;
                case "allow_leading_wildcard":
                case "allowLeadingWildcard":
                    fieldSettings.setAllowLeadingWildcard(parser.booleanValue());
                    break;
                case "allow_prefix":
                case "allowPrefix":
                    fieldSettings.setAllowPrefix(parser.booleanValue());
                    break;
                default:
                    throw new QueryParsingException(parseContext.index(), "[qsppp] query does not support [" + currentFieldName + "]");
                }
            } else if (token == START_OBJECT) {
                switch (currentFieldName) {
                case "fields":
                    while ((token = parser.nextToken()) != END_OBJECT) {
                        if (token == FIELD_NAME) {
                            currentFieldName = parser.currentName();
                        } else if (token.isValue()) {
                            switch (currentFieldName) {
                            case "default":
                                fields = parser.text();
                                break;
                            case "whitelist_default":
                            case "whitelistDefault":
                                if (!parser.booleanValue()) {
                                    defaultFieldUnauthorizedAction = UnauthorizedAction.KEEP;
                                }
                                break;
                            case "whitelist_all":
                            case "whitelistAll":
                                if (parser.booleanValue()) {
                                    fieldsHelper.whitelistAll();
                                }
                                break;
                            default:
                                throw new QueryParsingException(parseContext.index(), "[qsppp] query does not support [fields."
                                        + currentFieldName + "]");
                            }
                        } else if (token == START_ARRAY) {
                            switch (currentFieldName) {
                            case "whitelist":
                                while ((token = parser.nextToken()) != END_ARRAY) {
                                    fieldsHelper.whitelist(parser.text());
                                }
                                break;
                            case "blacklist":
                                while ((token = parser.nextToken()) != END_ARRAY) {
                                    fieldsHelper.blacklist(parser.text());
                                }
                                break;
                            default:
                                throw new QueryParsingException(parseContext.index(), "[qsppp] query does not support [fields."
                                        + currentFieldName + "]");
                            }
                        } else if (token == START_OBJECT) {
                            switch (currentFieldName) {
                            case "aliases":
                                while ((token = parser.nextToken()) != END_OBJECT) {
                                    if (token == FIELD_NAME) {
                                        currentFieldName = parser.currentName();
                                    } else if (token.isValue()) {
                                        for (FieldReference target : parseFields(parser.text())) {
                                            fieldsHelper.addAlias(currentFieldName, target);
                                        }
                                    }
                                }
                                break;
                            case "definitions":
                                parseDefinitions(parseContext, parser, fieldsHelper);
                                break;
                            default:
                                throw new QueryParsingException(parseContext.index(), "[qsppp] query does not support [fields."
                                        + currentFieldName + "]");
                            }
                        }
                    }
                    break;
                default:
                    throw new QueryParsingException(parseContext.index(), "[qsppp] query does not support [" + currentFieldName + "]");
                }
            }
        }

        if (query == null) {
            throw new QueryParsingException(parseContext.index(), "qsppp must be provided with a [query]");
        }
        if (fields == null) {
            throw new QueryParsingException(parseContext.index(),
                    "qsppp must be provided with a [fields] or a [field] or a [fields.default]");
        }

        List<FieldUsage> defaultFields = fieldsHelper.resolve(parseFields(fields), defaultFieldUnauthorizedAction);
        BasicQueryBuilder basicQueryBuilder = new BasicQueryBuilder(fieldSettings, defaultFields);
        DefaultingQueryBuilder queryBuilder = new DefaultingQueryBuilder(defaultSettings, basicQueryBuilder);
        try {
            Query parsed = new QueryParserHelper(fieldsHelper, queryBuilder, defaultIsAnd, emptyIsMatchAll).parse(query);
            if (boost != null) {
                parsed.setBoost(boost);
            }
            return parsed;
        } catch (Exception e) {
            /*
             * Elasticsearch doesn't log the stack trace for these errors so we
             * log them ourselves.
             */
            log.warn("Error parsing query", e);
            throw e;
        }
    }

    private void parseDefinitions(QueryParseContext parseContext, XContentParser parser, FieldsHelper fieldsHelper) throws IOException {
        String name = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != END_OBJECT) {
            if (token == FIELD_NAME) {
                name = parser.currentName();
            } else if (token == START_OBJECT) {
                String currentFieldName = null;
                String standard = null;
                String precise = null;
                String reversePrecise = null;
                String prefixPrecise = null;
                while ((token = parser.nextToken()) != END_OBJECT) {
                    if (token == FIELD_NAME) {
                        currentFieldName = parser.currentName();
                    } else if (token.isValue()) {
                        switch (currentFieldName) {
                        case "standard":
                            standard = parser.text();
                            break;
                        case "precise":
                            precise = parser.text();
                            break;
                        case "reverse_precise":
                        case "reversePrecise":
                            reversePrecise = parser.text();
                            break;
                        case "prefix_precise":
                        case "prefixPrecise":
                            prefixPrecise = parser.text();
                            break;
                        default:
                            throw new QueryParsingException(parseContext.index(), "[qsppp] query does not support [fields.definitions."
                                    + currentFieldName + "]");
                        }
                    }
                }
                standard = MoreObjects.firstNonNull(standard, name);
                fieldsHelper.addField(name, new FieldDefinition(standard, precise, reversePrecise, prefixPrecise));
            }
        }
    }
}
