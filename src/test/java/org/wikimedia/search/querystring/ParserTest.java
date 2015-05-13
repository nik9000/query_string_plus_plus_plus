package org.wikimedia.search.querystring;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ParserTest {
    @Parameters(name="{1}")
    public static Collection<Object[]> params() {
        return Arrays.asList(new Object[][] { { query("foo"), "foo" }, //
                { query("AND"), "AND" }, //
                { or("foo", "bar"), "foo OR bar" }, //
                { and("foo", "bar"), "foo AND bar" }, //
                { and("foo", "bar"), "foo bar" }, //
                { and("foo", "OR"), "foo OR" }, //
                { and("foo", "AND"), "foo AND" }, //
                { and("foo", "bar", "baz"), "foo AND bar AND baz" }, //
                { or(and("foo", "bar"), "baz"), "foo AND bar OR baz" }, //
                { and("foo", or("bar", "baz")), "foo bar OR baz" }, //
                });
    }

    @Parameter(0)
    public Query query;
    @Parameter(1)
    public String str;

    @Test
    public void parse() {
        assertEquals(query, new QueryParserHelper().parse(str));
    }

    private static BooleanQuery or(Object... clauses) {
        BooleanQuery bq = new BooleanQuery();
        bq.setMinimumNumberShouldMatch(1);
        for (int i = 0; i < clauses.length; i++) {
            bq.add(query(clauses[i]), Occur.SHOULD);
        }
        return bq;
    }

    private static BooleanQuery and(Object... clauses) {
        BooleanQuery bq = new BooleanQuery();
        for (int i = 0; i < clauses.length; i++) {
            bq.add(query(clauses[i]), Occur.MUST);
        }
        return bq;
    }

    private static Query query(Object o) {
        if (o instanceof Query) {
            return (Query) o;
        }
        if (o instanceof String) {
            return new TermQuery(new Term("term", o.toString()));
        }
        throw new IllegalArgumentException("No idea what to do with:  " + o);
    }
}
