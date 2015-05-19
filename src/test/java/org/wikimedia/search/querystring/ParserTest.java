package org.wikimedia.search.querystring;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
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
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.index.query.support.QueryParsers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

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
                { Queries.newMatchNoDocsQuery(), "", true, false }, //
                { Queries.newMatchNoDocsQuery(), "   ", true, false }, //
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
                { or("foo", or("bar", "baz")), "foo bar OR baz", false }, //
                { and("foo", clause("bar", Occur.MUST_NOT)), "foo -bar" }, //
                { and("foo", clause("bar", Occur.MUST_NOT)), "foo !bar" }, //
                { and("foo", clause("bar", Occur.MUST_NOT)), "foo - bar" }, //
                { and("foo", clause("bar", Occur.MUST_NOT)), "foo ! bar" }, //
                { and("foo", clause("bar", Occur.MUST_NOT)), "foo NOT bar" }, //
                { or("foo", clause("bar", Occur.MUST_NOT)), "foo -bar", false }, //
                { or("foo", clause("bar", Occur.MUST_NOT)), "foo !bar", false }, //
                { or("foo", clause("bar", Occur.MUST_NOT)), "foo NOT bar", false }, //
                { and("foo", "bar"), "foo +bar" }, //
                { or("foo", clause("bar", Occur.MUST)), "foo +bar", false }, //
                { and("foo", clause(or("bar", "baz"), Occur.MUST_NOT)), "foo -(bar OR baz)" }, //
                { and("foo", clause(or("bar", "baz"), Occur.MUST_NOT)), "foo -( bar OR baz )" }, //
                { or("foo", "bar", clause(and("baz", "bort"), Occur.MUST)), "foo bar +(baz AND bort)", false }, //
                { or("foo", "bar", and("baz", "bort")), "foo bar (baz AND bort)", false }, //
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
        }) {
            String label;
            switch (param.length) {
            case 2:
                label = param[1].toString();
                param = new Object[] { null, param[0], param[1], true, true };
                break;
            case 3:
                label = String.format(Locale.ROOT, "\"%s\" %s", param[1], param[2]);
                param = new Object[] { null, param[0], param[1], param[2], true };
                break;
            case 4:
                label = String.format(Locale.ROOT, "\"%s\" %s %s", param[1], param[2], param[3]);
                param = new Object[] { null, param[0], param[1], param[2], param[3] };
                break;
            default:
                throw new RuntimeException("Invalid example:  " + Arrays.toString(param));
            }
            param[0] = label;
            params.add(param);
        }
        return params;
    }

    private static final QueryBuilderSettings settings = new QueryBuilderSettings("field", "phrase_field");
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

    @Test
    public void parse() {
        QueryBuilder builder = new QueryBuilder(settings);
        Query parsed = new QueryParserHelper(builder, defaultIsAnd, emptyIsMatchAll).parse(str);
        assertEquals(expected, parsed);
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
        throw new IllegalArgumentException("No idea what to do with:  " + o);
    }

    private static final Pattern FIELD_PATTERN = Pattern.compile("([^:]+):(.+)");
}
