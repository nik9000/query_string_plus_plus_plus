package org.wikimedia.search.querystring;

import static org.elasticsearch.common.collect.Maps.newHashMap;
import static org.junit.Assert.assertEquals;
import static org.wikimedia.search.querystring.FieldsParsingTest.BOOST_PATTERN;
import static org.wikimedia.search.querystring.FieldsParsingTest.fieldReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.elasticsearch.common.base.Splitter;
import org.elasticsearch.common.collect.ArrayListMultimap;
import org.elasticsearch.common.collect.ListMultimap;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.index.query.support.QueryParsers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.wikimedia.search.querystring.query.BasicQueryBuilder;
import org.wikimedia.search.querystring.query.DefaultingQueryBuilder;
import org.wikimedia.search.querystring.query.FieldDefinition;
import org.wikimedia.search.querystring.query.FieldQueryBuilder;
import org.wikimedia.search.querystring.query.FieldReference;
import org.wikimedia.search.querystring.query.FieldUsage;

import com.carrotsearch.ant.tasks.junit4.dependencies.com.google.common.collect.Iterables;

/**
 * Tests that the parser builds the right queries.
 */
@RunWith(Parameterized.class)
public class QueryParsingTest {
    @Parameters(name = "{0}")
    public static Collection<Object[]> params() {
        List<Object[]> params = new ArrayList<>();
        for (Object[] param : new Object[][] {
                { query("foo"), "foo" }, //
                { query("foo"), "foo   " }, //
                { Queries.newMatchAllQuery(), "" }, //
                { Queries.newMatchAllQuery(), "   " }, //
                { Queries.newMatchNoDocsQuery(), "", "empty=matchNone" }, //
                { Queries.newMatchNoDocsQuery(), "   ", "empty=matchNone" }, //
                { query("AND"), "AND" }, //
                { query("||"), "||" }, //
                { query(","), "," }, //
                { query("a,"), "a," }, //
                { query("~"), "~" }, //
                { query("+"), "+" }, //
                { query("-"), "-" }, //
                { and("foo", "bar", "baz"), "foo bar baz" }, //
                { or("foo", "bar"), "foo OR bar" }, //
                { and("foo", "or", "bar"), "foo or bar" }, //
                { and("foo", "bar"), "foo AND bar" }, //
                { and("foo", "and", "bar"), "foo and bar" }, //
                { or("foo", "bar"), "foo || bar" }, //
                { or("foo", "bar"), "foo||bar" }, //
                { and("foo", "bar"), "foo && bar" }, //
                { and("foo", "bar"), "foo&&bar" }, //
                { and("foo", "bar"), "foo bar" }, //
                { and("foo", "OR"), "foo OR" }, //
                { and("foo", "AND"), "foo AND" }, //
                { and("foo", "&&"), "foo &&" }, //
                { and("foo", "bar", "baz"), "foo AND bar AND baz" }, //
                { or(and("foo", "bar"), "baz"), "foo AND bar OR baz" }, //
                { and("foo", or("bar", "baz")), "foo bar OR baz" }, //
                { or("foo", or("bar", "baz")), "foo bar OR baz", "default=or" }, //
                { and("foo", clause("bar", Occur.MUST_NOT)), "foo -bar" }, //
                { and("foo", clause("bar", Occur.MUST_NOT)), "foo !bar" }, //
                { and("foo", clause("bar", Occur.MUST_NOT)), "foo - bar" }, //
                { and("foo", clause("bar", Occur.MUST_NOT)), "foo ! bar" }, //
                { and("foo", clause("bar", Occur.MUST_NOT)), "foo NOT bar" }, //
                { or("foo", clause("bar", Occur.MUST_NOT)), "foo -bar", "default=or" }, //
                { or("foo", clause("bar", Occur.MUST_NOT)), "foo !bar", "default=or" }, //
                { or("foo", clause("bar", Occur.MUST_NOT)), "foo NOT bar", "default=or" }, //
                { and("foo", "bar"), "foo +bar" }, //
                { or("foo", clause("bar", Occur.MUST)), "foo +bar", "default=or" }, //
                { and("foo", clause(or("bar", "baz"), Occur.MUST_NOT)), "foo -(bar OR baz)" }, //
                { and("foo", clause(or("bar", "baz"), Occur.MUST_NOT)), "foo -( bar OR baz )" }, //
                { or("foo", "bar", clause(and("baz", "bort"), Occur.MUST)), "foo bar +(baz AND bort)", "default=or" }, //
                { or("foo", "bar", and("baz", "bort")), "foo bar (baz AND bort)", "default=or" }, //
                { phrase("foo", "bar"), "\"foo bar\"" }, //
                { phrase("foo", "\"bar"), "\"foo \\\"bar\"" }, //
                { phrase("foo", "\"", "bar"), "\"foo \\\" bar\"" }, //
                { phrase("foo", "bar"), "\"foo bar" }, //
                { boost(phrase("foo", "bar"), 2), "\"foo bar\"^2" }, //
                { and(phrase("foo", "bar"), "precise_field:baz"), "\"foo bar\" \"baz\"" }, //
                { and(phrase("foo", "bar", "baz"), phrase("bort", "bop")), "\"foo bar baz\" \"bort bop\"" }, //
                { or(phrase("foo", "bar", "baz"), phrase("bort", "bop")), "\"foo bar baz\" OR \"bort bop\"" }, //
                { phrase(1, "foo", "bar"), "\"foo bar\"~1" }, //
                { phrase(20, "foo", "bar"), "\"foo bar\"~300" }, //
                { phrase("field:foo", "field:bar"), "\"foo bar\"~" }, //
                { phrase(1, "field:foo", "field:bar"), "\"foo bar\"~1~" }, //
                { and(clause("foo", Occur.MUST_NOT)), "-foo" }, //
                { and(clause(phrase("foo", "bar"), Occur.MUST_NOT)), "-\"foo bar\"" }, //
                { and("foo", clause(phrase("bar", "baz"), Occur.MUST_NOT)), "foo -\"bar baz\"" }, //
                { and("foo", clause("precise_field:bar", Occur.MUST_NOT)), "foo -\"bar\"" }, //
                { query("foo~1"), "foo~.4" }, //
                { query("foo~1"), "foo~1" }, //
                { query("foo~2"), "foo~2" }, //
                { and("foo~1", "bar"), "foo~ bar" }, //
                { query("foo~2"), "foo~3" }, //
                { query("foo"), "foo~.8" }, //
                { query("foo"), "foo~0" }, //
                { query("fo"), "fo~" }, //
                { query("foo~1"), "foo~" }, //
                { query("foooo~1"), "foooo~" }, //
                { query("fooooo~2"), "fooooo~" }, //
                { query("fooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo~2"), //
                        "fooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooooo~" }, //
                { query("pickl*"), "pickl*" }, //
                // TODO this is probably not quite right - * for a term should
                // be that it is defined at all
                { and("pickl", "*"), "pickl *" }, //
                { query("pickl?"), "pickl?" }, //
                { query("pic???"), "pic???" }, //
                { new TermQuery(new Term("field", "???")), "???" }, //
                { new TermQuery(new Term("field", "*oo")), "*oo" }, //
                { query("???"), "???", "allowLeadingWildcard=true" }, //
                { query("*oo"), "*oo", "allowLeadingWildcard=true" }, //
                { query("field_reverse:oo?"), "?oo", "reverseFields=field->field_reverse" }, //
                { new WildcardQuery(new Term("field_reverse", "oo*")), "*oo", "reverseFields=field->field_reverse" }, //
                { new TermQuery(new Term("field", "?o?")), "?o?", "reverseFields=field->field_reverse" }, //
                { new TermQuery(new Term("field", "*o*")), "*o*", "reverseFields=field->field_reverse" }, //
                { query("field_prefix:oo"), "oo*", "prefixFields=field->field_prefix" }, //
                { query("p?l"), "p?l" }, //
                { query("pi*kl?"), "pi*kl?" }, //
                { query("pi\\*kl?"), "pi\\*kl?" }, //
                { and("pick?e", "catap?lt"), "pick?e catap?lt" }, //
                // This next one is slightly different than Cirrus
                { query("precise_field:10.7227"), "\"10.7227\"yay\"" }, //
                { query("precise_field:10.1093/acprof:oso/9780195314250.003.0001"), "\"10.1093/acprof:oso/9780195314250.003.0001\"" }, //
                { and(phrase("two", "words"), "pickles", phrase("ffnonesenseword", "catapult")),
                        "\"two words\" pickles \"ffnonesenseword catapult" }, //
                { and(phrase("two", "words"), "pickles", phrase("ffnonesenseword", "catapult")),
                        "\"two words\" AND pickles AND \"ffnonesenseword catapult\"" }, //
                { or(phrase("two", "words"), "pickles", phrase("ffnonesenseword", "catapult")),
                        "\"two words\" OR pickles OR \"ffnonesenseword catapult\"" }, //
                // The next two are also different than Cirrus
                { phrase("field:foo", "field:bar"), "\"foo bar\"~garbage" }, //
                { and(phrase("field:foo", "field:bar"), "cat"), "\"foo bar\"~garbage cat" }, //
                { or("a:foo", "b:foo"), "foo", "fields=a|b" }, //
                { and(or("a:foo", "b:foo"), or("a:bar", "b:bar")), "foo bar", "fields=a|b" }, //
                { or(phrase("precise_a:foo", "precise_a:bar"), phrase("precise_b:foo", "precise_b:bar")), "\"foo bar\"", "fields=a|b" }, //
                { and(or("precise_a:foo", "precise_b:foo"), or("a:bar", "b:bar")), "\"foo\" bar", "fields=a|b" }, //
                { or("a:foo", "b:foo^5"), "foo", "fields=a|b^5" }, //
                { and("foo^5", "bar"), "foo^5 bar" }, //
                { and("foo^5.1", "bar"), "foo^5.1 bar" }, //
                { and("foo^cat", "bar"), "foo^cat bar" }, //
                { and("another_field:foo", "bar"), "another_field:foo bar" }, //
                { and("another.field:foo", "bar"), "another.field:foo bar" }, //
                { and("another:foo^2", "bar"), "another^2:foo bar" }, //
                { and(or("another:foo", "andAnother:foo"), "bar"), "another,andAnother:foo bar" }, //
                // The next one is totally valid but confusing looking
                { and(or("another:foo", "andAnother:foo"), "bar"), "another, andAnother:foo bar" }, //
                { and(or("A:foo", "C:foo", "B:foo"), "bar"), "a,b:foo bar", "aliases=a->A;C|b->B" }, //
                { and("another:foo", "bar"), "another,blacklisted:foo bar" }, //
                { and("field:blacklisted:foo", "bar"), "blacklisted:foo bar" }, //
                { phrase("title:foo", "title:bar"), "intitle:\"foo bar\"", "aliases=intitle->title, whitelist=title" }, //
                { or(phrase("title:foo", "title:bar"), phrase("category:foo", "category:bar")), "intitle,incategory:\"foo bar\"",
                        "aliases=intitle->title|incategory->category, whitelist=title|category" }, //
                { or(boost(phrase("title:foo", "title:bar"), 2), phrase("category:foo", "category:bar")), "tc:\"foo bar\"",
                        "aliases=tc->title^2;category, whitelist=title|category" }, //
                { or(boost(phrase("title:foo", "title:bar"), 4), boost(phrase("category:foo", "category:bar"), 2)), "tc^2:\"foo bar\"",
                        "aliases=tc->title^2;category, whitelist=title|category" }, //
        // TODO fields can't be integer or contain them!
        }) {
            Query expected = (Query) param[0];
            String toParse = param[1].toString();
            boolean defaultIsAnd = true;
            boolean emptyIsMatchAll = true;
            List<String> fields = Collections.singletonList("field");
            ListMultimap<String, String> aliases = ArrayListMultimap.create();
            Set<String> whitelist = new HashSet<>();
            whitelist.add("another_field");
            whitelist.add("field");
            whitelist.add("another");
            whitelist.add("andAnother");
            whitelist.add("another.field");
            whitelist.add("A");
            whitelist.add("B");
            whitelist.add("C");
            Set<String> blacklist = new HashSet<>();
            blacklist.add("blacklisted");
            boolean allowLeadingWildcard = false;
            Map<String, String> reverseFields = new HashMap<>();
            Map<String, String> prefixFields = new HashMap<>();
            String label;
            switch (param.length) {
            case 2:
                label = toParse;
                break;
            case 3:
                label = String.format(Locale.ROOT, "\"%s\" with %s", param[1], param[2]);
                Map<String, String> settings = newHashMap(Splitter.on(',').trimResults().withKeyValueSeparator('=')
                        .split(param[2].toString()));
                String newDefault = settings.remove("default");
                if (newDefault != null) {
                    if (newDefault.equals("or")) {
                        defaultIsAnd = false;
                    } else {
                        throw new RuntimeException("Invalid default operation:  " + newDefault);
                    }
                }
                String newEmpty = settings.remove("empty");
                if (newEmpty != null) {
                    if (newEmpty.equals("matchNone")) {
                        emptyIsMatchAll = false;
                    } else {
                        throw new RuntimeException("Invalid empty operation:  " + newEmpty);
                    }
                }
                String newFields = settings.remove("fields");
                if (newFields != null) {
                    fields = Splitter.on('|').splitToList(newFields);
                    if (fields.size() == 0) {
                        throw new RuntimeException("Fields cannot be empty");
                    }
                }
                String extraAliases = settings.remove("aliases");
                if (extraAliases != null) {
                    // What a mess...
                    for (Map.Entry<String, String> s : Splitter.on('|').withKeyValueSeparator("->").split(extraAliases).entrySet()) {
                        aliases.putAll(s.getKey(), Splitter.on(';').split(s.getValue()));
                    }
                }
                String extraWhitelist = settings.remove("whitelist");
                if (extraWhitelist != null) {
                    Iterables.addAll(whitelist, Splitter.on('|').split(extraWhitelist));
                }
                String extraBlacklist = settings.remove("blacklist");
                if (extraBlacklist != null) {
                    Iterables.addAll(blacklist, Splitter.on('|').split(extraBlacklist));
                }
                String newAllowLeadingWildcard = settings.remove("allowLeadingWildcard");
                if (newAllowLeadingWildcard != null) {
                    allowLeadingWildcard = Boolean.parseBoolean(newAllowLeadingWildcard);
                }
                parseToMap(reverseFields, settings, "reverseFields");
                parseToMap(prefixFields, settings, "prefixFields");
                if (!settings.isEmpty()) {
                    throw new RuntimeException("Invalid example settings: " + param[2]);
                }
                break;
            default:
                throw new RuntimeException("Invalid example:  " + Arrays.toString(param));
            }
            params.add(new Object[] { label, expected, toParse, defaultIsAnd, emptyIsMatchAll, fields, aliases, whitelist, blacklist,
                    allowLeadingWildcard, reverseFields, prefixFields });
        }
        return params;
    }

    private static void parseToMap(Map<String, String> target, Map<String, String> settings, String name) {
        String value = settings.remove(name);
        if (value == null) {
            return;
        }
        target.putAll(Splitter.on('|').withKeyValueSeparator("->").split(value));
    }

    private static final Pattern FIELD_PATTERN = Pattern.compile("([^:]+):(.+)");
    private static final DefaultingQueryBuilder.Settings UNCHANCED_DEFAULT_SETTINGS = new DefaultingQueryBuilder.Settings();
    private static final FieldQueryBuilder.Settings UNCHANGED_SETTINGS = new FieldQueryBuilder.Settings();
    @Parameter(0)
    public String label;
    @Parameter(1)
    public Query expected;
    @Parameter(2)
    public String str;
    @Parameter(3)
    public boolean defaultIsAnd;
    @Parameter(4)
    public boolean emptyIsMatchAll;
    @Parameter(5)
    public List<String> fields;
    @Parameter(6)
    public ListMultimap<String, String> aliases;
    @Parameter(7)
    public Set<String> whitelist;
    @Parameter(8)
    public Set<String> blacklist;
    @Parameter(9)
    public boolean allowLeadingWildcard;
    @Parameter(10)
    public Map<String, String> reverseFields;
    @Parameter(11)
    public Map<String, String> prefixFields;

    @Test
    public void parse() {
        Query parsed = new QueryParserHelper(fieldsHelper(), builder(), defaultIsAnd, emptyIsMatchAll).parse(str);
        assertEquals(expected, parsed);
    }

    private FieldsHelper fieldsHelper() {
        FieldsHelper fieldsHelper = new FieldsHelper(new FieldDetector.Noop());
        for (Map.Entry<String, String> alias : aliases.entries()) {
            fieldsHelper.addAlias(alias.getKey(), fieldReference(alias.getValue()));
        }
        if (whitelist == null) {
            fieldsHelper.whitelistAll();
        } else {
            for (String field : whitelist) {
                fieldsHelper.whitelist(field);
            }
        }
        for (String field : blacklist) {
            fieldsHelper.blacklist(field);
        }
        return fieldsHelper;
    }

    private DefaultingQueryBuilder builder() {
        List<FieldUsage> usages = new ArrayList<>();
        for (String field : fields) {
            FieldReference reference = fieldReference(field);
            FieldDefinition definition = new FieldDefinition(reference.getName(), "precise_" + reference.getName(),
                    reverseFields.get(reference.getName()), prefixFields.get(reference.getName()));
            usages.add(new FieldUsage(definition, reference.getBoost()));
        }
        FieldQueryBuilder.Settings settings = new FieldQueryBuilder.Settings();
        settings.setAllowLeadingWildcard(allowLeadingWildcard);
        return new DefaultingQueryBuilder(UNCHANCED_DEFAULT_SETTINGS, new BasicQueryBuilder(settings, usages));
    }

    private static BooleanQuery or(Object... clauses) {
        BooleanQuery bq = new BooleanQuery();
        bq.setMinimumNumberShouldMatch(1);
        for (int i = 0; i < clauses.length; i++) {
            bq.add(clause(clauses[i], Occur.SHOULD));
        }
        return bq;
    }

    private static BooleanQuery and(Object... clauses) {
        BooleanQuery bq = new BooleanQuery();
        for (int i = 0; i < clauses.length; i++) {
            bq.add(clause(clauses[i], Occur.MUST));
        }
        return bq;
    }

    private static PhraseQuery phrase(Object... terms) {
        PhraseQuery pq = new PhraseQuery();
        int i = 0;
        if (terms[i] instanceof Number) {
            pq.setSlop(((Number) terms[i++]).intValue());
        } else {
            pq.setSlop(0);
        }
        for (; i < terms.length; i++) {
            String s = terms[i].toString();
            Term t;
            Matcher m = FIELD_PATTERN.matcher(s);
            if (m.matches()) {
                t = new Term(m.group(1), m.group(2));
            } else {
                t = new Term("precise_field", s);
            }
            pq.add(t);
        }
        return pq;
    }

    private static BooleanClause clause(Object clause, Occur defaultOccur) {
        if (clause instanceof BooleanClause) {
            return (BooleanClause) clause;
        }
        return new BooleanClause(query(clause), defaultOccur);
    }

    private static Query query(Object o) {
        if (o instanceof Query) {
            return (Query) o;
        }
        if (o instanceof String) {
            String s = o.toString();
            Matcher m = BOOST_PATTERN.matcher(s);
            float boost = 1;
            if (m.matches()) {
                s = m.group(1);
                boost = Float.parseFloat(m.group(2));
            }
            Query q = unboostedQueryFromString(s);
            q.setBoost(boost);
            return q;
        }
        throw new IllegalArgumentException("No idea what to do with:  " + o);
    }

    private static Query unboostedQueryFromString(String s) {
        String field = "field";
        boolean specifiedField = false;
        Matcher m = FIELD_PATTERN.matcher(s);
        if (m.matches()) {
            field = m.group(1);
            s = m.group(2);
            specifiedField = true;
        }
        m = Pattern.compile("(.+)~(\\d+)").matcher(s);
        if (m.matches()) {
            s = m.group(1);
            int edits = Integer.parseInt(m.group(2), 10);
            FuzzyQuery fq = new FuzzyQuery(new Term(field, s), edits, UNCHANGED_SETTINGS.getFuzzyPrefixLength(),
                    UNCHANGED_SETTINGS.getFuzzyMaxExpansions(), false);
            QueryParsers.setRewriteMethod(fq, UNCHANGED_SETTINGS.getRewriteMethod());
            return fq;
        }
        if (s.contains("?") || s.substring(0, s.length() - 1).contains("*")) {
            field = !specifiedField ? "precise_" + field : field;
            WildcardQuery wq = new WildcardQuery(new Term(field, s));
            QueryParsers.setRewriteMethod(wq, UNCHANGED_SETTINGS.getRewriteMethod());
            return wq;
        }
        if (s.endsWith("*")) {
            field = !specifiedField ? "precise_" + field : field;
            PrefixQuery pq = new PrefixQuery(new Term(field, s.substring(0, s.length() - 1)));
            QueryParsers.setRewriteMethod(pq, UNCHANGED_SETTINGS.getRewriteMethod());
            return pq;
        }
        return new TermQuery(new Term(field, s));
    }

    private static Query boost(Query query, float boost) {
        query.setBoost(boost);
        return query;
    }
}
