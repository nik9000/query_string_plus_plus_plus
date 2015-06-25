package org.wikimedia.search.querystring;

import static org.wikimedia.search.querystring.FieldsParsingTest.fieldReference;
import static org.wikimedia.search.querystring.QueryParsingTest.parseAnalyzer;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.TestUtil;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wikimedia.search.querystring.query.BasicQueryBuilder;
import org.wikimedia.search.querystring.query.DefaultingQueryBuilder;
import org.wikimedia.search.querystring.query.FieldReference;
import org.wikimedia.search.querystring.query.FieldUsage;
import org.wikimedia.search.querystring.query.RegexQueryBuilder.WikimediaExtraRegexQueryBuilder;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Repeat;

/**
 * Parses random queries and verifies that they don't blow up.
 */
@RunWith(RandomizedRunner.class)
//@Seed("BD9334D4E41F3B7A:FBB7C267FBFD54A5")
public class RandomQueryParsingTest extends RandomizedTest {
    private static final ESLogger log = ESLoggerFactory.getLogger(RandomQueryParsingTest.class.getPackage().getName());
    @Test
    @Repeat(iterations=1000)
    public void parseRandomQuery() {
        Analyzer standardAnalyzer = parseAnalyzer("english");
        Analyzer preciseAnalyzer = parseAnalyzer("standard");
        FieldsHelper fieldsHelper = new FieldsHelper(new FieldResolver.NeverFinds(standardAnalyzer, preciseAnalyzer));
        List<FieldUsage> usages = new ArrayList<>();
        String field = "foo";
        FieldReference reference = fieldReference(field);
        String preciseName = frequently() ? "precise_" + reference.getName() : null;
        String reversePreciseName = rarely() ? "reverse_precise_" + reference.getName() : null;
        String prefixPreciseName = rarely() ? "reverse_precise_" + reference.getName() : null;
        String ngramName = rarely() ? "ngram_" + reference.getName() : null;
        FieldUsage usage = new FieldUsage(reference.getName(), standardAnalyzer, preciseName, preciseAnalyzer, reversePreciseName,
                preciseAnalyzer, prefixPreciseName, preciseAnalyzer, ngramName, 3, 1);
        usages.add(usage);
        BasicQueryBuilder.Settings settings = new BasicQueryBuilder.Settings();
        settings.setAllowLeadingWildcard(rarely());
        settings.setAllowPrefix(frequently());
        settings.setMaxPhraseSlop(between(0, 10));
        settings.setRegexQueryBuilder(ngramName == null ? null : new WikimediaExtraRegexQueryBuilder());
        DefaultingQueryBuilder builder = new DefaultingQueryBuilder(new DefaultingQueryBuilder.Settings(), new BasicQueryBuilder(
                settings, usages));
        String str = TestUtil.randomRealisticUnicodeString(getRandom(), 1000);
        log.info("Parsing \"{}\"", str);
        Query parsed = new QueryParserHelper(fieldsHelper, builder, true, true).parse(str);
        assertNotNull(parsed);
    }
}
