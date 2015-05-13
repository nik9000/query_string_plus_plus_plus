package org.wikimedia.search.querystring;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ParserTest {
    @Parameters(name = "\"{1}\" {2}")
    public static Collection<Object[]> params() {
        List<Object[]> params = new ArrayList<>();
        for (Object[] param : new Object[][] { { query("foo"), "foo" }, //
                { query("AND"), "AND" }, //
                { query("||"), "||" }, //
                { and("foo", "bar", "baz"), "foo bar baz" }, //
                { or("foo", "bar"), "foo OR bar" }, //
                { and("foo", "bar"), "foo AND bar" }, //
                { or("foo", "bar"), "foo || bar" }, //
                { and("foo", "bar"), "foo && bar" }, //
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
                { or("foo", "bar", clause(and("baz", "bort"), Occur.MUST)), "foo bar +(baz AND bort)", false }, //
                { or("foo", "bar", and("baz", "bort")), "foo bar (baz AND bort)", false }, //
                { phrase("foo", "bar"), "\"foo bar\"" }, //
                { phrase("foo", "\"bar"), "\"foo \\\"bar\"" }, //
                { phrase("foo", "\"", "bar"), "\"foo \\\" bar\"" }, //
                { phrase("foo", "bar"), "\"foo bar" }, // We add the extra " if its missing
                { and(phrase("foo", "bar"), "phrase_field:baz"), "\"foo bar\" \"baz\"" }, //
                { and(phrase("foo", "bar", "baz"), phrase("bort", "bop")), "\"foo bar baz\" \"bort bop\"" }, //
        }) {
            if (param.length == 2) {
                param = new Object[] { param[0], param[1], true };
            }
            params.add(param);
        }
        return params;
    }

    @Parameter(0)
    public Query expected;
    @Parameter(1)
    public String str;
    @Parameter(2)
    public boolean defaultIsAnd;

    @Test
    public void parse() {
        QueryBuilder builder = new QueryBuilder("field", "phrase_field");
        Query parsed = new QueryParserHelper(builder, defaultIsAnd).parse(str);
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

    private static PhraseQuery phrase(String... terms) {
        PhraseQuery pq = new PhraseQuery();
        for (int i = 0; i < terms.length; i++) {
            pq.add(new Term("phrase_field", terms[i]));
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
            Matcher m = Pattern.compile("(.+):(.+)").matcher(s);
            if (m.matches()) {
                return new TermQuery(new Term(m.group(1), m.group(2)));
            }
            return new TermQuery(new Term("field", s));
        }
        throw new IllegalArgumentException("No idea what to do with:  " + o);
    }
}
