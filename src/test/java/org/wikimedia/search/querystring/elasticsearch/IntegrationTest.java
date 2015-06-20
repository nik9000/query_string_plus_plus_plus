package org.wikimedia.search.querystring.elasticsearch;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFailures;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchHits;
import static org.hamcrest.Matchers.containsString;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.plugins.PluginsService;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;
import org.wikimedia.search.extra.regex.SourceRegexFilterBuilder;
import org.wikimedia.search.querystring.query.FieldDefinition;

/**
 * Tests the queries over the Elasticsearch api.
 */
public class IntegrationTest extends ElasticsearchIntegrationTest {
    @Test
    public void basic() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("foo", "bar"));
        assertSearchHits(search(builder("foo", "bar")), "1");
        assertHitCount(search(builder("foo", "bort")), 0);
        assertHitCount(search(builder("bort", "bar")), 0);
    }

    @Test
    public void defaultOperator() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("foo", "bar"));
        QueryStringPlusPlusPlusBuilder builder = builder("foo", "bar baz");
        assertHitCount(search(builder), 0);
        assertHitCount(search(builder.defaultIsOr()), 1);
        assertHitCount(search(builder.defaultIsAnd()), 0);
    }

    @Test
    public void emptyQuery() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("foo", "bar"));
        QueryStringPlusPlusPlusBuilder builder = builder("foo", "");
        assertHitCount(search(builder), 1);
        assertHitCount(search(builder.emptyIsMatchNone()), 0);
        assertHitCount(search(builder.emptyIsMatchAll()), 1);
    }

    @Test
    public void allowLeadingWildcard() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("foo", "bar"));
        QueryStringPlusPlusPlusBuilder builder = builder("foo", "*r");
        assertHitCount(search(builder), 0);
        assertHitCount(search(builder.allowLeadingWildcard(false)), 0);
        assertHitCount(search(builder.allowLeadingWildcard(true)), 1);
        builder = builder("foo", "?ar");
        assertHitCount(search(builder), 0);
        assertHitCount(search(builder.allowLeadingWildcard(false)), 0);
        assertHitCount(search(builder.allowLeadingWildcard(true)), 1);
    }

    @Test
    public void whitelistDefault() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("foo", "bar"));
        QueryStringPlusPlusPlusBuilder builder = builder("foo", "foo:bar");
        assertHitCount(search(builder), 1);
        assertHitCount(search(builder.whitelistDefault(false)), 0);
        assertHitCount(search(builder.whitelistDefault(true)), 1);
    }

    @Test
    public void detectedFieldsArentWhitelisted() throws InterruptedException, ExecutionException, IOException {
        buildNiceMapping();
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("auto", "stemming"));
        assertHitCount(search(builder("auto", "stem")), 1);
        assertHitCount(search(builder("auto", "auto.precise:stemming")), 0);
        assertHitCount(search(builder("auto", "auto.precise:stemming").whitelist("auto.precise")), 1);
        assertHitCount(search(builder("auto", "auto.precise:stem").whitelist("auto.precise")), 0);
    }

    @Test
    public void whitelistAll() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("foo", "bar", "other", "bar"));
        QueryStringPlusPlusPlusBuilder builder = builder("foo", "other:bar");
        assertHitCount(search(builder), 0);
        assertHitCount(search(builder.whitelistAll(false)), 0);
        assertHitCount(search(builder.whitelistAll(true)), 1);
        assertHitCount(search(builder.blacklist("other")), 0);
        builder = builder("foo", "foo:bar").whitelistDefault(false);
        assertHitCount(search(builder), 0);
        assertHitCount(search(builder.whitelistAll(true)), 1);
        assertHitCount(search(builder.blacklist("foo")), 0);
    }

    @Test
    public void blacklist() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("foo", "bar"));
        QueryStringPlusPlusPlusBuilder builder = builder("foo", "foo:bar");
        assertHitCount(search(builder), 1);
        assertHitCount(search(builder.blacklist("foo")), 0);
        builder = builder("foo", "bar");
        assertHitCount(search(builder), 1);
        // Default fields aren't effected by blacklist
        assertHitCount(search(builder.blacklist("foo")), 1);
    }

    @Test
    public void whitelist() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("foo", "bar", "other", "bar"));
        QueryStringPlusPlusPlusBuilder builder = builder("foo", "other:bar");
        assertHitCount(search(builder), 0);
        assertHitCount(search(builder.whitelist("other")), 1);
        builder = builder("foo", "foo:bar");
        // By default you don't have to whitelist a default field
        assertHitCount(search(builder), 1);
        // But you do if you turn off whitelistDefault
        assertHitCount(search(builder.whitelistDefault(false)), 0);
        assertHitCount(search(builder.whitelist("foo")), 1);
    }

    @Test
    public void fields() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("a", "foo"), //
                client().prepareIndex("test", "test", "2").setSource("b", "foo"));
        assertHitCount(search(builder("a, b", "foo")), 2);
        assertSearchHits(search(builder("a, b^2", "foo")), "2", "1");
        assertSearchHits(search(builder("a^2, b", "foo")), "1", "2");
    }

    @Test
    public void aliases() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("a", "foo"), //
                client().prepareIndex("test", "test", "2").setSource("b", "foo"));
        QueryStringPlusPlusPlusBuilder builder = builder("aa, bb", "foo").alias("aa", "a");
        SearchResponse response = client().prepareSearch("test").setQuery(builder).get();
        assertSearchHits(response, "1");
        builder = builder("aa, bb", "foo").alias("bb", "b");
        assertSearchHits(search(builder), "2");
        builder = builder("aa, bb^2", "foo").alias("aa", "a").alias("bb", "b");
        assertSearchHits(search(builder), "2", "1");
        builder = builder("aa, bb", "foo").alias("aa", "a").alias("bb", "b^2");
        assertSearchHits(search(builder), "2", "1");
        builder = builder("aa, bb^2", "foo").alias("aa", "a^4").alias("bb", "b");
        assertSearchHits(search(builder), "1", "2");
        // Aliases aren't whitelisted by default
        builder = builder("junk", "aa:foo").alias("aa", "a");
        assertHitCount(search(builder), 0);
        // But you _can_ whitelist their targets
        assertHitCount(search(builder.whitelist("a")), 1);
    }

    @Test
    public void fieldDefinitions() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("a", "foo"), //
                client().prepareIndex("test", "test", "2").setSource("b", "foo"));
        QueryStringPlusPlusPlusBuilder builder = builder("aa, bb", "foo").define("aa", new FieldDefinition("a"));
        SearchResponse response = client().prepareSearch("test").setQuery(builder).get();
        assertSearchHits(response, "1");
        builder = builder("aa, bb", "foo").define("bb", new FieldDefinition("b"));
        assertSearchHits(search(builder), "2");
        builder = builder("aa, bb", "foo").define("aa", new FieldDefinition("a")).define("bb", new FieldDefinition("b"));
        assertHitCount(search(builder), 2);
        builder = builder("aa, bb", "foo").define("a", new FieldDefinition("ua")).define("b", new FieldDefinition("ub"));
        assertHitCount(search(builder), 0);
        builder = builder("aa, bb", "\"foo\"").define("aa", new FieldDefinition("ua", "a")).define("bb", new FieldDefinition("ub", "b"));
        assertHitCount(search(builder), 2);
        builder = builder("aaa, bbb", "foo").alias("aaa", "aa").alias("bbb", "bb").define("aa", new FieldDefinition("a", "qa"))
                .define("bb", new FieldDefinition("b", "qb"));
        assertHitCount(search(builder), 2);
        // Defined fields aren't whitelisted by default
        builder = builder("junk", "aaa:foo").alias("aaa", "aa").define("aa", new FieldDefinition("a", "qa"));
        assertHitCount(search(builder), 0);
        // And whitelisting any of the unquoted or quoted fields does nothing
        assertHitCount(search(builder.whitelist("a")), 0);
        // But whitelisting the field's name will whitelist it
        assertHitCount(search(builder.whitelist("aa")), 1);
    }

    @Test
    public void multiFields() throws InterruptedException, ExecutionException, IOException {
        buildNiceMapping();
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("explicit", "foo", "auto", "foo"));
        assertHitCount(search(builder("explicit", "foo")), 1);
    }

    @Test
    public void rewrites() throws InterruptedException, ExecutionException, IOException {
        buildNiceMapping();
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("explicit", "foo", "auto", "foo"));
        FieldDefinition explicitField = new FieldDefinition("explicit", "explicit.break_auto_precise",
                "explicit.break_auto_reverse_precise", "explicit.break_auto_prefix_precise", "explicit.trigram", 3);
        QueryStringPlusPlusPlusBuilder builder = builder("explicit", "*oo");
        assertHitCount(search(builder), 0);
        assertHitCount(search(builder.define("explicit", explicitField)), 1);
        builder = builder("explicit", "fo*").allowPrefix(false);
        assertHitCount(search(builder), 0);
        assertHitCount(search(builder.define("explicit", explicitField)), 1);
        /*
         * No need to define the auto field - its subfields follow a pattern the
         * query automatically detects.
         */
        assertHitCount(search(builder("auto", "*oo")), 1);
        assertHitCount(search(builder("auto", "fo*").allowPrefix(false)), 1);
    }

    @Test
    public void     span() throws InterruptedException, ExecutionException, IOException {
        buildNiceMapping();
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("foo", "foo bar boooom", "auto", "foo bar boooom"));
        for (String field: new String[] {"foo", "auto"}) {
            assertHitCount(search(builder(field, "\"foo bar\"")), 1);
            assertHitCount(search(builder(field, "\"foo ba*\"")), 1);
            assertHitCount(search(builder(field, "\"ba* foo\"")), 0);
            assertHitCount(search(builder(field, "\"fo? bar\"")), 1);
            assertHitCount(search(builder(field, "\"fo? ba*\"")), 1);
            assertHitCount(search(builder(field, "\"foo bo*\"~1")), 1);
            assertHitCount(search(builder(field, "\"foo bo*\"")), 0);
            assertHitCount(search(builder(field, "\"foo b*\"")), 1);
            assertHitCount(search(builder(field, "\"foo b*\"~1")), 1);
            assertHitCount(search(builder(field, "\"foo bar boooomm~\"")), 1);
            assertHitCount(search(builder(field, "\"foo bar boooomm~2\"")), 1);
            assertHitCount(search(builder(field, "\"*oo bar\"").allowLeadingWildcard(true)), 1);
            // Auto doesn't need a leading wildcard because its uses a reverse field
            assertHitCount(search(builder(field, "\"*oo bar\"").allowLeadingWildcard(false)), field.equals("auto") ? 1 : 0);
        }
    }

    /**
     * This tests using Elasticsearch's _field_name optimization for field
     * exists.
     */
    @Test
    public void exists() throws InterruptedException, ExecutionException, IOException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("a", "foo"), //
                client().prepareIndex("test", "test", "2").setSource("b", "foo"), //
                client().prepareIndex("test", "test", "3").setSource("a", "foo", "b", "foo"));
        assertHitCount(search(builder("a", "*")), 2);
        assertHitCount(search(builder("b", "*")), 2);
        assertHitCount(search(builder("does_not_exist", "*")), 0);
        assertHitCount(search(builder("a,b", "a:* OR b:*")), 3);
        assertHitCount(search(builder("a,b", "a:* AND b:*")), 1);
        assertHitCount(search(builder("a,b", "(a:* OR b:*) AND NOT (a:* AND b:*)")), 2);
        assertSearchHits(search(builder("a,b", "a:* NOT b:*")), "1");
    }

    @Test
    public void regex() throws InterruptedException, ExecutionException, IOException {
        buildNiceMapping();
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("explicit", "foo bar", "auto", "foo bar"));
        FieldDefinition explicitField = new FieldDefinition("explicit", "explicit.break_auto_precise",
                "explicit.break_auto_reverse_precise", "explicit.break_auto_prefix_precise", "explicit.break_auto_trigram", 3);
        QueryStringPlusPlusPlusBuilder builder = builder("explicit", "/oo b/");
        SourceRegexFilterBuilder.Settings settings = new SourceRegexFilterBuilder.Settings();
        assertHitCount(search(builder), 0);
        assertHitCount(search(builder.regexSettings(settings)), 1);
        assertHitCount(search(builder.regexSettings(null).define("explicit", explicitField)), 0);
        assertHitCount(search(builder.regexSettings(settings)), 1);
        /*
         * No need to define the auto field - its subfields follow a pattern the
         * query automatically detects.
         */
        builder = builder("auto", "/oo b/");
        assertHitCount(search(builder.regexSettings(settings)), 1);
        builder = builder("explicit", "/oo b/");
        settings.rejectUnaccelerated(true);
        assertFailures(client().prepareSearch("test").setQuery(builder.regexSettings(settings)), RestStatus.INTERNAL_SERVER_ERROR,
                containsString("Unable to accelerate \"oo b\""));
        indexRandom(true, client().prepareIndex("test", "test", "2").setSource("auto", "cat"));
        builder = builder("auto", "/oo b/ OR cat");
        assertHitCount(search(builder.regexSettings(settings)), 2);
    }

    private static QueryStringPlusPlusPlusBuilder builder(String fields, String query) {
        return new QueryStringPlusPlusPlusBuilder(fields, query);
    }

    /**
     * Enable plugin loading.
     */
    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return ImmutableSettings.builder().put(super.nodeSettings(nodeOrdinal))
                .put("plugins." + PluginsService.LOAD_PLUGIN_FROM_CLASSPATH, true).build();
    }

    private SearchResponse search(QueryStringPlusPlusPlusBuilder builder) {
        return client().prepareSearch("test").setQuery(builder).get();
    }

    private void buildNiceMapping() throws IOException {
        XContentBuilder mapping = jsonBuilder().startObject();
        mapping.startObject("test").startObject("properties");
        fieldWithSubFields(mapping, "explicit", true);
        fieldWithSubFields(mapping, "auto", false);
        mapping.endObject().endObject().endObject();

        XContentBuilder settings = jsonBuilder().startObject().startObject("index");
        settings.startObject("analysis");
        {
            settings.startObject("analyzer");
            {
                settings.startObject("reverse_standard");
                {
                    settings.field("tokenizer", "standard");
                    settings.field("filter", "standard", "lowercase", "reverse");
                }
                settings.endObject();
                settings.startObject("prefix_standard");
                {
                    settings.field("tokenizer", "standard");
                    settings.field("filter", "standard", "lowercase", "prefix");
                }
                settings.endObject();
                settings.startObject("trigram");
                {
                    settings.field("tokenizer", "trigram");
                    settings.field("filter", "lowercase");
                }
                settings.endObject();
            }
            settings.endObject();
            settings.startObject("filter");
            {
                settings.startObject("prefix");
                {
                    settings.field("type", "edgeNGram");
                    settings.field("max_grap", 255);
                }
                settings.endObject();
            }
            settings.endObject();
            settings.startObject("tokenizer");
            {
                settings.startObject("trigram");
                {
                    settings.field("type", "ngram");
                    settings.field("min_gram", 3);
                    settings.field("max_gram", 3);
                }
                settings.endObject();
            }
            settings.endObject();
        }
        settings.endObject();
        assertAcked(prepareCreate("test").setSettings(settings).addMapping("test", mapping));
        ensureYellow();
    }

    private void fieldWithSubFields(XContentBuilder mapping, String name, boolean breakAutoDetection) throws IOException {
        mapping.startObject(name);
        mapping.field("type", "string");
        mapping.field("analyzer", "english");
        {
            mapping.startObject("fields");
            String namePrefix = breakAutoDetection ? "break_auto_" : "";
            field(mapping, namePrefix + "precise", "standard");
            field(mapping, namePrefix + "reverse_precise", "reverse_standard");
            field(mapping, namePrefix + "prefix_precise", "prefix_standard");
            field(mapping, namePrefix + "trigram", "trigram");
            mapping.endObject();
        }
        mapping.endObject();
    }

    private void field(XContentBuilder mapping, String name, String analyzer) throws IOException {
        mapping.startObject(name);
        mapping.field("type", "string");
        mapping.field("analyzer", analyzer);
        mapping.endObject();
    }
}
