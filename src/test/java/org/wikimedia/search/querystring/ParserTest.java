package org.wikimedia.search.querystring;

import static org.elasticsearch.common.collect.Maps.newHashMap;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import org.elasticsearch.common.collect.ImmutableList;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.index.query.support.QueryParsers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.wikimedia.search.querystring.query.BasicQueryBuilder;
import org.wikimedia.search.querystring.query.BoostingFieldQueryBuilder;
import org.wikimedia.search.querystring.query.DefaultingQueryBuilder;
import org.wikimedia.search.querystring.query.FieldQueryBuilder;
import org.wikimedia.search.querystring.query.MultiFieldQueryBuilder;
import org.wikimedia.search.querystring.query.SingleFieldQueryBuilder;

/**
 * Tests that the parser builds the right queries.
 */
@RunWith(Parameterized.class)
public class ParserTest {
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
                { and(phrase("foo", "bar"), "phrase_field:baz"), "\"foo bar\" \"baz\"" }, //
                { and(phrase("foo", "bar", "baz"), phrase("bort", "bop")), "\"foo bar baz\" \"bort bop\"" }, //
                { or(phrase("foo", "bar", "baz"), phrase("bort", "bop")), "\"foo bar baz\" OR \"bort bop\"" }, //
                { phrase(1, "foo", "bar"), "\"foo bar\"~1" }, //
                { phrase(20, "foo", "bar"), "\"foo bar\"~300" }, //
                { phrase("field:foo", "field:bar"), "\"foo bar\"~" }, //
                { phrase(1, "field:foo", "field:bar"), "\"foo bar\"~1~" }, //
                { and(clause("foo", Occur.MUST_NOT)), "-foo" }, //
                { and(clause(phrase("foo", "bar"), Occur.MUST_NOT)), "-\"foo bar\"" }, //
                { and("foo", clause(phrase("bar", "baz"), Occur.MUST_NOT)), "foo -\"bar baz\"" }, //
                { and("foo", clause("phrase_field:bar", Occur.MUST_NOT)), "foo -\"bar\"" }, //
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
                { query("???"), "???" }, //
                { query("p?l"), "p?l" }, //
                { query("pi*kl?"), "pi*kl?" }, //
                { query("pi\\*kl?"), "pi\\*kl?" }, //
                { and("pick?e", "catap?lt"), "pick?e catap?lt" }, //
                // This next one is slightly different than Cirrus
                { query("phrase_field:10.7227"), "\"10.7227\"yay\"" }, //
                { query("phrase_field:10.1093/acprof:oso/9780195314250.003.0001"), "\"10.1093/acprof:oso/9780195314250.003.0001\"" }, //
                { and(phrase("two", "words"), "pickles", phrase("ffnonesenseword", "catapult")),
                        "\"two words\" pickles \"ffnonesenseword catapult" }, //
                { and(phrase("two", "words"), "pickles", phrase("ffnonesenseword", "catapult")),
                        "\"two words\" AND pickles AND \"ffnonesenseword catapult\"" }, //
                { or(phrase("two", "words"), "pickles", phrase("ffnonesenseword", "catapult")),
                        "\"two words\" OR pickles OR \"ffnonesenseword catapult\"" }, //
                // The next one is also different than Cirrus
                { phrase("field:foo", "field:bar"), "\"foo bar\"~garbage" }, //
                { or("a:foo", "b:foo"), "foo", "fields=a|b" }, //
                { and(or("a:foo", "b:foo"), or("a:bar", "b:bar")), "foo bar", "fields=a|b" }, //
                { or(phrase("phrase_a:foo", "phrase_a:bar"), phrase("phrase_b:foo", "phrase_b:bar")), "\"foo bar\"", "fields=a|b" }, //
                { and(or("phrase_a:foo", "phrase_b:foo"), or("a:bar", "b:bar")), "\"foo\" bar", "fields=a|b" }, //
                { or("a:foo", "b:foo^5"), "foo", "fields=a|b^5" }, //
                { and("foo^5", "bar"), "foo^5 bar" }, //
                { and("foo^5.1", "bar"), "foo^5.1 bar" }, //
                { and("foo^cat", "bar"), "foo^cat bar" }, //
        }) {
            Query expected = (Query) param[0];
            String toParse = param[1].toString();
            boolean defaultIsAnd = true;
            boolean emptyIsMatchAll = true;
            List<String> fields = Collections.singletonList("field");
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
                    fields = ImmutableList.copyOf(Splitter.on('|').split(newFields));
                    if (fields.size() == 0) {
                        throw new RuntimeException("Fields cannot be empty");
                    }
                }
                if (!settings.isEmpty()) {
                    throw new RuntimeException("Invalid example settings: " + param[2]);
                }
                break;
            default:
                throw new RuntimeException("Invalid example:  " + Arrays.toString(param));
            }
            params.add(new Object[] { label, expected, toParse, defaultIsAnd, emptyIsMatchAll, fields });
        }
        return params;
    }

    private static final DefaultingQueryBuilder.Settings defaultSettings = new DefaultingQueryBuilder.Settings();
    private static final FieldQueryBuilder.Settings settings = new FieldQueryBuilder.Settings();
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

    @Test
    public void parse() {
        Query parsed = new QueryParserHelper(builder(), defaultIsAnd, emptyIsMatchAll).parse(str);
        assertEquals(expected, parsed);
    }

    private DefaultingQueryBuilder builder() {
        FieldQueryBuilder fieldBuilder;
        if (fields.size() == 1) {
            fieldBuilder = fieldQueryBuilder(fields.get(0));
        } else {
            List<FieldQueryBuilder> fieldBuilders = new ArrayList<>();
            for (String field : fields) {
                fieldBuilders.add(fieldQueryBuilder(field));
            }
            fieldBuilder = new MultiFieldQueryBuilder(fieldBuilders);
        }
        return new DefaultingQueryBuilder(defaultSettings, new BasicQueryBuilder(fieldBuilder));
    }

    private FieldQueryBuilder fieldQueryBuilder(String field) {
        Matcher m = BOOST_PATTERN.matcher(field);
        float boost = 1;
        if (m.matches()) {
            field = m.group(1);
            boost = Float.parseFloat(m.group(2));
        }
        FieldQueryBuilder b = new SingleFieldQueryBuilder(field, "phrase_" + field, settings);
        if (boost != 1) {
            b = new BoostingFieldQueryBuilder(b, boost);
        }
        return b;
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
                t = new Term("phrase_field", s);
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
        Matcher m = FIELD_PATTERN.matcher(s);
        if (m.matches()) {
            field = m.group(1);
            s = m.group(2);
        }
        m = Pattern.compile("(.+)~(\\d+)").matcher(s);
        if (m.matches()) {
            s = m.group(1);
            int edits = Integer.parseInt(m.group(2), 10);
            FuzzyQuery fq = new FuzzyQuery(new Term(field, s), edits, settings.getFuzzyPrefixLength(),
                    settings.getFuzzyMaxExpansions(), false);
            QueryParsers.setRewriteMethod(fq, settings.getRewriteMethod());
            return fq;
        }
        if (s.contains("?") || s.substring(0, s.length() - 1).contains("*")) {
            WildcardQuery wq = new WildcardQuery(new Term(field, s));
            QueryParsers.setRewriteMethod(wq, settings.getRewriteMethod());
            return wq;

        }
        if (s.length() > 1 && s.endsWith("*")) {
            PrefixQuery pq = new PrefixQuery(new Term(field, s.substring(0, s.length() - 1)));
            QueryParsers.setRewriteMethod(pq, settings.getRewriteMethod());
            return pq;
        }
        return new TermQuery(new Term(field, s));
    }

    private static final Pattern FIELD_PATTERN = Pattern.compile("([^:]+):(.+)");
    private static final Pattern BOOST_PATTERN = Pattern.compile("(.+)\\^([0-9]*\\.?[0-9]+)");
}
