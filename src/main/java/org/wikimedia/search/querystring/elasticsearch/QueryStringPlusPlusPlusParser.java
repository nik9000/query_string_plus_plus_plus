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

/**
 * Parses QueryStringPlusPlusPlus.
 */
public class QueryStringPlusPlusPlusParser implements QueryParser {
    public static final String[] NAMES = new String[] { "qsppp", "query_string_plus_plus_plus", "queryStringPlusPlusPlus" };

    @Override
    public String[] names() {
        return NAMES;
    }

    @Override
    public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        DefaultingQueryBuilder.Settings defaultSettings = new DefaultingQueryBuilder.Settings();
        FieldQueryBuilder.Settings fieldSettings = new FieldQueryBuilder.Settings();
        FieldsHelper fieldsHelper = new FieldsHelper();
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
                default:
                    throw new QueryParsingException(parseContext.index(), "[query_string] query does not support [" + currentFieldName
                            + "]");
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
                                if (!parser.booleanValue()) {
                                    defaultFieldUnauthorizedAction = UnauthorizedAction.KEEP;
                                }
                            case "whitelist_all":
                                fieldsHelper.whitelistAll();
                                break;
                            default:
                                throw new QueryParsingException(parseContext.index(), "[query_string] query does not support [fields."
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
                                throw new QueryParsingException(parseContext.index(), "[query_string] query does not support [fields."
                                        + currentFieldName + "]");
                            }
                        } else if (token == START_OBJECT) {
                            switch (currentFieldName) {
                            case "aliases":
                                while ((token = parser.nextToken()) != END_OBJECT) {
                                    if (token == FIELD_NAME) {
                                        currentFieldName = parser.currentName();
                                    } else if (token.isValue()) {
                                        // Whitelist all alias targets, why
                                        // would they be targets otherwise?
                                        for (FieldDefinition target : parseFields(fieldsHelper, parser.text(), UnauthorizedAction.WHITELIST)) {
                                            fieldsHelper.addAlias(currentFieldName, target);
                                        }
                                    }
                                }
                            default:
                                throw new QueryParsingException(parseContext.index(), "[query_string] query does not support [fields."
                                        + currentFieldName + "]");
                            }
                        }
                    }
                    break;
                default:
                    throw new QueryParsingException(parseContext.index(), "[query_string] query does not support [" + currentFieldName
                            + "]");
                }
            }
        }

        if (query == null) {
            throw new QueryParsingException(parseContext.index(), "query_string_plus_plus_plus must be provided with a [query]");
        }
        if (fields == null) {
            throw new QueryParsingException(parseContext.index(),
                    "query_string_plus_plus_plus must be provided with a [fields] or a [field] or a [fields.default]");
        }

        List<FieldDefinition> defaultFields = parseFields(fieldsHelper, fields, defaultFieldUnauthorizedAction);
        BasicQueryBuilder basicQueryBuilder = new BasicQueryBuilder(fieldSettings, defaultFields);
        DefaultingQueryBuilder queryBuilder = new DefaultingQueryBuilder(defaultSettings, basicQueryBuilder);
        Query parsed = new QueryParserHelper(fieldsHelper, queryBuilder, defaultIsAnd, emptyIsMatchAll).parse(query);
        if (boost != null) {
            parsed.setBoost(boost);
        }
        return parsed;
    }
}
