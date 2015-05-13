package org.wikimedia.search.querystring;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
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
    @Parameters(name = "\"{1}\" {2}")
    public static Collection<Object[]> params() {
        List<Object[]> params = new ArrayList<>();
        for (Object[] param : new Object[][] { { query("foo"), "foo" }, //
                { query("AND"), "AND" }, //
                { query("||"), "||" }, //
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
        }) {
            if (param.length == 2) {
                param = new Object[] { param[0], param[1], true };
            }
            params.add(param);
        }
        return params;
    }

    @Parameter(0)
    public Query query;
    @Parameter(1)
    public String str;
    @Parameter(2)
    public boolean defaultIsAnd;

    @Test
    public void parse() {
        assertEquals(query, new QueryParserHelper(defaultIsAnd).parse(str));
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
            return new TermQuery(new Term("term", o.toString()));
        }
        throw new IllegalArgumentException("No idea what to do with:  " + o);
    }
}
