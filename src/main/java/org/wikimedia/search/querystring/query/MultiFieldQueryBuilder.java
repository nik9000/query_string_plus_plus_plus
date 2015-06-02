package org.wikimedia.search.querystring.query;

import java.util.List;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;

public class MultiFieldQueryBuilder implements FieldQueryBuilder {
    /**
     * One query build per field.
     */
    private final List<FieldQueryBuilder> fieldDelegates;

    public MultiFieldQueryBuilder(List<FieldQueryBuilder> fieldDelegates) {
        this.fieldDelegates = fieldDelegates;
    }

    @Override
    public Query termQuery(String term) {
        BooleanQuery bq = or();
        for (FieldQueryBuilder fieldDelegate : fieldDelegates) {
            bq.add(fieldDelegate.termQuery(term), Occur.SHOULD);
        }
        return bq;
    }

    @Override
    public Query phraseQuery(List<String> terms, int slop, boolean useQuotedTerm) {
        BooleanQuery bq = or();
        for (FieldQueryBuilder fieldDelegate : fieldDelegates) {
            bq.add(fieldDelegate.phraseQuery(terms, slop, useQuotedTerm), Occur.SHOULD);
        }
        return bq;
    }

    @Override
    public Query fuzzyQuery(String term, float similaritySpec) {
        BooleanQuery bq = or();
        for (FieldQueryBuilder fieldDelegate : fieldDelegates) {
            bq.add(fieldDelegate.fuzzyQuery(term, similaritySpec), Occur.SHOULD);
        }
        return bq;
    }

    @Override
    public Query prefixQuery(String term) {
        BooleanQuery bq = or();
        for (FieldQueryBuilder fieldDelegate : fieldDelegates) {
            bq.add(fieldDelegate.prefixQuery(term), Occur.SHOULD);
        }
        return bq;
    }

    @Override
    public Query wildcardQuery(String term) {
        BooleanQuery bq = or();
        for (FieldQueryBuilder fieldDelegate : fieldDelegates) {
            bq.add(fieldDelegate.wildcardQuery(term), Occur.SHOULD);
        }
        return bq;
    }

    @Override
    public Query regexQuery(String regex) {
        BooleanQuery bq = or();
        for (FieldQueryBuilder fieldDelegate : fieldDelegates) {
            bq.add(fieldDelegate.regexQuery(regex), Occur.SHOULD);
        }
        return bq;
    }

    @Override
    public Query fieldExists() {
        BooleanQuery bq = or();
        for (FieldQueryBuilder fieldDelegate : fieldDelegates) {
            bq.add(fieldDelegate.fieldExists(), Occur.SHOULD);
        }
        return bq;
    }

    @Override
    public String toString() {
        return fieldDelegates.toString();
    }

    private BooleanQuery or() {
        BooleanQuery bq = new BooleanQuery();
        bq.setMinimumNumberShouldMatch(1);
        return bq;
    }
}
