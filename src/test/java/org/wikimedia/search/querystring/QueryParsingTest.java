package org.wikimedia.search.querystring;

import static org.elasticsearch.common.collect.Maps.newHashMap;
import static org.elasticsearch.common.lucene.search.Queries.newMatchAllQuery;
import static org.elasticsearch.common.lucene.search.Queries.newMatchNoDocsQuery;
import static org.junit.Assert.assertEquals;
import static org.wikimedia.search.querystring.FieldsParsingTest.BOOST_PATTERN;
import static org.wikimedia.search.querystring.FieldsParsingTest.fieldReference;

import java.io.IOException;
import java.io.Reader;
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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.spans.FieldMaskingSpanQuery;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper.TopTermsSpanBooleanQueryRewrite;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.util.CharsRef;
import org.elasticsearch.common.base.Splitter;
import org.elasticsearch.common.collect.ArrayListMultimap;
import org.elasticsearch.common.collect.Iterables;
import org.elasticsearch.common.collect.ListMultimap;
import org.elasticsearch.common.lucene.search.XFilteredQuery;
import org.elasticsearch.index.query.support.QueryParsers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.wikimedia.search.extra.regex.SourceRegexFilter;
import org.wikimedia.search.extra.util.FieldValues;
import org.wikimedia.search.querystring.query.BasicQueryBuilder;
import org.wikimedia.search.querystring.query.DefaultingQueryBuilder;
import org.wikimedia.search.querystring.query.FieldQueryBuilder;
import org.wikimedia.search.querystring.query.FieldReference;
import org.wikimedia.search.querystring.query.FieldUsage;
import org.wikimedia.search.querystring.query.RegexQueryBuilder;

/**
 * Tests that the parser builds the right queries.
 *
 * This class works by having a very basic query builder syntax which is uses to
 * build example queries to compare against the output of the parser. This works
 * because Lucene queries all implements equals.
 */
@RunWith(Parameterized.class)
public class QueryParsingTest {
    @Parameters(name = "{0}")
    public static Collection<Object[]> params() {
        List<Object[]> params = new ArrayList<>();
        for (Object[] param : new Object[][] {
                { query("foo"), "foo" }, //
                { query("foo"), "foo   " }, //
                { newMatchAllQuery(), "" }, //
                { newMatchAllQuery(), "   " }, //
                { newMatchNoDocsQuery(), "", "empty=matchNone" }, //
                { newMatchNoDocsQuery(), "   ", "empty=matchNone" }, //
                { query("and"), "AND", "standardAnalyzer=standard" }, //
                { query("or"), "OR", "standardAnalyzer=standard" }, //
                { query("a"), "a,", "standardAnalyzer=standard" }, //
                { query("AND"), "AND", "standardAnalyzer=keyword" }, //
                { query("OR"), "OR", "standardAnalyzer=keyword" }, //
                { query("||"), "||", "standardAnalyzer=keyword" }, //
                { query(","), ",", "standardAnalyzer=keyword" }, //
                { query("a,"), "a,", "standardAnalyzer=keyword" }, //
                { query("~"), "~", "standardAnalyzer=keyword" }, //
                { query("+"), "+", "standardAnalyzer=keyword" }, //
                { query("-"), "-", "standardAnalyzer=keyword" }, //
                { newMatchAllQuery(), "AND" }, //
                { newMatchAllQuery(), "OR" }, //
                { newMatchAllQuery(), "a," }, //
                { newMatchAllQuery(), "||" }, //
                { newMatchAllQuery(), "," }, //
                { newMatchAllQuery(), "a," }, //
                { newMatchAllQuery(), "~" }, //
                { newMatchAllQuery(), "+" }, //
                { newMatchAllQuery(), "-" }, //
                { and("foo", "bar", "baz"), "foo bar baz" }, //
                { or("foo", "bar"), "foo OR bar" }, //
                { and("foo", "or", "bar"), "foo or bar", "standardAnalyzer=keyword" }, //
                /* The operator is thrown out because its an English stopword. */
                { and("foo", "bar"), "foo or bar" }, //
                { and("foo", "bar"), "foo AND bar" }, //
                { and("foo", "bar"), "foo and bar" }, //
                { or("foo", "bar"), "foo || bar" }, //
                { or("foo", "bar"), "foo||bar" }, //
                { and("foo", "bar"), "foo && bar" }, //
                { and("foo", "bar"), "foo&&bar" }, //
                { and("foo", "bar"), "foo bar" }, //
                { and("foo", "OR"), "foo OR", "standardAnalyzer=keyword" }, //
                { and("foo", "AND"), "foo AND", "standardAnalyzer=keyword" }, //
                { and("foo", "&&"), "foo &&", "standardAnalyzer=keyword" }, //
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
                { phrase("foo", "\"bar"), "\"foo \\\"bar\"", "preciseAnalyzer=keyword" }, //
                { phrase("foo", "bar"), "\"foo \\\"bar\"" }, //
                { phrase("foo", "\"", "bar"), "\"foo \\\" bar\"", "preciseAnalyzer=keyword" }, //
                { phrase("foo", "bar"), "\"foo \\\" bar\"" }, //
                { phrase("foo", "bar"), "\"foo bar" }, //
                { boost(phrase("foo", "bar"), 2), "\"foo bar\"^2" }, //
                { and(phrase("foo", "bar"), "precise_field:baz"), "\"foo bar\" \"baz\"" }, //
                { and(phrase("foo", "bar", "baz"), phrase("bort", "bop")), "\"foo bar baz\" \"bort bop\"" }, //
                { or(phrase("foo", "bar", "baz"), phrase("bort", "bop")), "\"foo bar baz\" OR \"bort bop\"" }, //
                { phrase(1, "foo", "bar"), "\"foo bar\"~1" }, //
                { phrase(20, "foo", "bar"), "\"foo bar\"~300" }, //
                { phrase("field:foo", "field:bar"), "\"foo bar\"~" }, //
                { phrase(1, "field:foo", "field:bar"), "\"foo bar\"~1~" }, //
                { span("field", "foo*", "precise_field:bar"), "\"foo* bar\"" }, //
                { span("field", "precise_field:foo", "bar*"), "\"foo bar*\"" }, //
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
                { and("pickl", "*"), "pickl *" }, //
                { query("pickl?"), "pickl?" }, //
                { query("pic???"), "pic???" }, //
                { new TermQuery(new Term("field", "???")), "???", "standardAnalyzer=keyword" }, //
                { new TermQuery(new Term("field", "*oo")), "*oo", "standardAnalyzer=keyword" }, //
                { query("???"), "???", "allowLeadingWildcard=true" }, //
                { query("*oo"), "*oo", "allowLeadingWildcard=true" }, //
                { query("field_reverse:oo?"), "?oo", "reverseFields=field->field_reverse" }, //
                { new WildcardQuery(new Term("field_reverse", "oo*")), "*oo", "reverseFields=field->field_reverse" }, //
                { new TermQuery(new Term("field", "?o?")), "?o?", "reverseFields=field->field_reverse,standardAnalyzer=keyword" }, //
                { new TermQuery(new Term("field", "*o*")), "*o*", "reverseFields=field->field_reverse,standardAnalyzer=keyword" }, //
                { query("field_prefix:oo"), "oo*", "prefixFields=field->field_prefix" }, //
                { query("p?l"), "p?l" }, //
                { query("pi*kl?"), "pi*kl?" }, //
                { query("pi\\*kl?"), "pi\\*kl?" }, //
                { and("pick?e", "catap?lt"), "pick?e catap?lt" }, //
                // This next two are slightly different than Cirrus
                { query("precise_field:10.7227"), "\"10.7227\"yay\"" }, //
                { query("precise_field:7227"), "\"7227\"yay\"" }, //
                { phrase("precise_field:10.1093", "precise_field:acprof:oso", "precise_field:9780195314250.003.0001"),
                        "\"10.1093/acprof:oso/9780195314250.003.0001\"" }, //
                { phrase("field:10.1093", "field:acprof:oso", "field:9780195314250.003.0001"), "10.1093/acprof:oso/9780195314250.003.0001" }, //
                { phrase("field:10.1093", "field:acprof:oso"), "10.1093/acprof:oso" }, //
                { and(phrase("two", "words"), "pickl", phrase("ffnonesenseword", "catapult")),
                        "\"two words\" pickles \"ffnonesenseword catapult" }, //
                { and(phrase("two", "words"), "pickl", phrase("ffnonesenseword", "catapult")),
                        "\"two words\" AND pickles AND \"ffnonesenseword catapult\"" }, //
                { or(phrase("two", "words"), "pickl", phrase("ffnonesenseword", "catapult")),
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
                { and("foo^cat", "bar"), "foo^cat bar", "standardAnalyzer=keyword" }, //
                { and(phrase("field:foo", "field:cat"), "bar"), "foo^cat bar" }, //
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
                // Terms are analyzed
                { query("cat"), "cats" }, //
                /*
                 * Stopwords are excluded _but_ we're not that careful with the
                 * wrapping query.
                 */
                { and(query("cat")), "and cats" }, //
                /*
                 * Terms are analyzed and when they are multiple tokens they are
                 * phrase queries.
                 */
                { phrase("field:日", "field:本", "field:語"), "日本語" }, //
                { phrase("日", "本", "語"), "\"日本語\"" }, //
                { and(phrase("field:日", "field:本", "field:語"), "more", "word", phrase("field:共", "field:通", "field:語")),
                        "日本語 more words 共通語" }, //
                // Synonyms
                { or("foo", "bar"), "foo", "standardAnalyzer=synonym" }, //
                { phrase(new Object[] { "foo", "bar" }, "baz"), "\"foo baz\"", "preciseAnalyzer=synonym" }, //
                { span("field", new Object[] { "precise_field:foo", "precise_field:bar" }, "baz*"), "\"foo baz*\"", "preciseAnalyzer=synonym" }, //
                { span("field", "baz*", new Object[] { "precise_field:foo", "precise_field:bar" }), "\"baz* foo\"", "preciseAnalyzer=synonym" }, //
                { span("field", "foo*", "precise_field:baz"), "\"foo* baz\"", "preciseAnalyzer=synonym" }, //
                // Regexes are just terms when disallowed
                { query("foo"), "/foo./", "allowRegex=false" },//
                { new TermQuery(new Term("field", "/foo./")), "/foo./", "allowRegex=false, standardAnalyzer=keyword" },//
                // Allowed regexes are regexes
                { query("/foo./[3]"), "/foo./" },//
                { query("/f\\oo./[3]"), "/f\\oo./" },//
                { query("/cat|dog|crumpet/[3]"), "/cat|dog|crumpet/" },//
                // Regexes are fine even without the ngram field
                { query("another:/foo./"), "another:/foo./" },//
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
            Map<String, String> ngramFields = new HashMap<>();
            ngramFields.put("field", "trigram_field");
            Analyzer standardAnalyzer = parseAnalyzer("english");
            Analyzer preciseAnalyzer = parseAnalyzer("standard");
            boolean allowRegex = true;
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
                String newStandardAnalyzer = settings.remove("standardAnalyzer");
                if (newStandardAnalyzer != null) {
                    standardAnalyzer = parseAnalyzer(newStandardAnalyzer);
                }
                String newPreciseAnalyzer = settings.remove("preciseAnalyzer");
                if (newPreciseAnalyzer != null) {
                    preciseAnalyzer = parseAnalyzer(newPreciseAnalyzer);
                }
                String newAllowRegex = settings.remove("allowRegex");
                if (newAllowRegex != null) {
                    allowRegex = Boolean.parseBoolean(newAllowRegex);
                }
                if (!settings.isEmpty()) {
                    throw new RuntimeException("Invalid example settings: " + param[2]);
                }
                break;
            default:
                throw new RuntimeException("Invalid example:  " + Arrays.toString(param));
            }
            params.add(new Object[] { label, expected, toParse, defaultIsAnd, emptyIsMatchAll, fields, aliases, whitelist, blacklist,
                    allowLeadingWildcard, reverseFields, prefixFields, ngramFields, standardAnalyzer, preciseAnalyzer, allowRegex });
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
    @Parameter(12)
    public Map<String, String> ngramFields;
    @Parameter(13)
    public Analyzer standardAnalyzer;
    @Parameter(14)
    public Analyzer preciseAnalyzer;
    @Parameter(15)
    public boolean allowRegex;

    @Test
    public void parse() {
        Query parsed = new QueryParserHelper(fieldsHelper(), builder(), defaultIsAnd, emptyIsMatchAll).parse(str);
        assertEquals(expected, parsed);
    }

    private FieldsHelper fieldsHelper() {
        FieldsHelper fieldsHelper = new FieldsHelper(new FieldResolver.NeverFinds(standardAnalyzer, preciseAnalyzer));
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
        Analyzer reversePreciseAnalyzer = preciseAnalyzer;
        Analyzer prefixPreciseAnalyzer = preciseAnalyzer;
        List<FieldUsage> usages = new ArrayList<>();
        for (String field : fields) {
            FieldReference reference = fieldReference(field);
            FieldUsage usage = new FieldUsage(reference.getName(), standardAnalyzer, "precise_" + reference.getName(), preciseAnalyzer,
                    reverseFields.get(reference.getName()), reversePreciseAnalyzer, prefixFields.get(reference.getName()),
                    prefixPreciseAnalyzer, ngramFields.get(reference.getName()), 3, reference.getBoost());
            usages.add(usage);
        }
        FieldQueryBuilder.Settings settings = new FieldQueryBuilder.Settings();
        settings.setAllowLeadingWildcard(allowLeadingWildcard);
        if (allowRegex) {
            settings.setRegexQueryBuilder(new RegexQueryBuilder.WikimediaExtraRegexQueryBuilder());
        }
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

    private static Query phrase(Object... terms) {
        PhraseQuery pq = new PhraseQuery();
        int i = 0;
        if (terms[i] instanceof Number) {
            pq.setSlop(((Number) terms[i++]).intValue());
        } else {
            pq.setSlop(0);
        }
        for (; i < terms.length; i++) {
            Object t = terms[i];
            if (t instanceof Object[] || t instanceof String[]) {
                return multiphrase(terms);
            }
            pq.add(phraseTerm(terms[i].toString()));
        }
        return pq;
    }

    private static Query multiphrase(Object... terms) {
        MultiPhraseQuery pq = new MultiPhraseQuery();
        int i = 0;
        if (terms[i] instanceof Number) {
            pq.setSlop(((Number) terms[i++]).intValue());
        } else {
            pq.setSlop(0);
        }
        for (; i < terms.length; i++) {
            Object t = terms[i];
            if (t instanceof Object[]) {
                Object[] objects = (Object[]) t;
                String[] strings = new String[objects.length];
                for (int j = 0; j < objects.length; j++) {
                    strings[j] = objects[j].toString();
                }
                t = strings;
            }
            if (t instanceof String[]) {
                String[] strings = (String[]) t;
                Term[] termsAtPosition = new Term[strings.length];
                for (int j = 0; j < strings.length; j++) {
                    termsAtPosition[j] = phraseTerm(strings[j]);
                }
                pq.add(termsAtPosition);
                continue;
            }
            pq.add(phraseTerm(terms[i].toString()));
        }
        return pq;
    }

    private static Term phraseTerm(String s) {
        Matcher m = FIELD_PATTERN.matcher(s);
        if (m.matches()) {
            return new Term(m.group(1), m.group(2));
        }
        return new Term("precise_field", s);
    }

    private static Query span(String field, Object... terms) {
        List<SpanQuery> spanNear = new ArrayList<>();
        int slop = 0;
        int i = 0;
        if (terms[i] instanceof Number) {
            slop = ((Number) terms[i++]).intValue();
        }
        for (; i < terms.length; i++) {
            Object t = terms[i];
            if (t instanceof String[]) {
                String[] strings = (String[]) t;
                Object[] o = new Object[strings.length];
                for (int j = 0; j < strings.length; j++) {
                    o[j] = strings[j];
                }
                t = o;
            }
            if (t instanceof Object[]) {
                List<SpanQuery> clauses = new ArrayList<>();
                for (Object o : (Object[]) t) {
                    clauses.add(spanify(field, query(o)));
                }
                spanNear.add(new SpanOrQuery(clauses.toArray(new SpanQuery[clauses.size()])));
                continue;
            }
            spanNear.add(spanify(field, query(t)));
        }
        try {
            return new SpanNearQuery(spanNear.toArray(new SpanQuery[spanNear.size()]), slop, true, false);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Can't build the span query for " + Arrays.deepToString(terms), e);
        }
    }

    private static SpanQuery spanify(String field, Query q) {
        SpanQuery span = spanify(q);
        if (span.getField().equals(field)) {
            return span;
        }
        return new FieldMaskingSpanQuery(span, field);
    }

    private static SpanQuery spanify(Query q) {
        if (q instanceof TermQuery) {
            return new SpanTermQuery(((TermQuery) q).getTerm());
        }
        if (q instanceof MultiTermQuery) {
            MultiTermQuery mq = (MultiTermQuery) q;
            mq.setRewriteMethod(new TopTermsSpanBooleanQueryRewrite(UNCHANGED_SETTINGS.getFuzzyMaxExpansions()));
            return new SpanMultiTermQueryWrapper<>(mq);
        }
        throw new RuntimeException("No idea how to spanify:  " + q);
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
            field = !specifiedField ? "precise_" + field : field;
            s = m.group(1);
            int edits = Integer.parseInt(m.group(2), 10);
            FuzzyQuery fq = new FuzzyQuery(new Term(field, s), edits, UNCHANGED_SETTINGS.getFuzzyPrefixLength(),
                    UNCHANGED_SETTINGS.getFuzzyMaxExpansions(), false);
            QueryParsers.setRewriteMethod(fq, UNCHANGED_SETTINGS.getRewriteMethod());
            return fq;
        }
        m = Pattern.compile("/((?:[^/]|\\/)+)/(?:\\[(\\d+)\\])?").matcher(s);
        if (m.matches()) {
            String gramSizeString = m.group(2);
            String ngramField;
            int gramSize;
            if (gramSizeString == null) {
                gramSize = 3;
                ngramField = null;
            } else {
                gramSize = Integer.parseInt(gramSizeString, 10);
                switch (gramSize) {
                case 3:
                    ngramField = "trigram_" + field;
                    break;
                default:
                    throw new RuntimeException("no idea how to make a gram size of " + gramSize + "into a field name");
                }
            }
            SourceRegexFilter filter = new SourceRegexFilter(field, ngramField, m.group(1), FieldValues.loadFromSource(),
                    new SourceRegexFilter.Settings(), gramSize);
            return new XFilteredQuery(newMatchAllQuery(), filter);
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

    private static Analyzer parseAnalyzer(String name) {
        switch (name) {
        case "english":
            return new EnglishAnalyzer();
        case "standard":
            return new StandardAnalyzer(CharArraySet.EMPTY_SET);
        case "keyword":
            return new KeywordAnalyzer();
        case "synonym":
            return new Analyzer() {
                @Override
                @SuppressWarnings("resource")
                protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
                    SynonymMap.Builder syn = new SynonymMap.Builder(false);
                    syn.add(new CharsRef("foo"), new CharsRef("bar"), true);

                    StandardTokenizer src = new StandardTokenizer(reader);
                    TokenStream tok = new StandardFilter(src);
                    tok = new LowerCaseFilter(src);
                    try {
                        tok = new SynonymFilter(src, syn.build(), false);
                    } catch (IOException e) {
                        throw new RuntimeException("What happened?", e);
                    }
                    return new TokenStreamComponents(src, tok);
                }
            };
        default:
            throw new RuntimeException("Unexpected analyzer:  " + name);
        }
    }
}
