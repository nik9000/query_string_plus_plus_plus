package org.wikimedia.search.querystring.elasticsearch;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertHitCount;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSearchHits;

import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.PluginsService;
import org.elasticsearch.test.ElasticsearchIntegrationTest;
import org.junit.Test;
import org.wikimedia.search.querystring.query.FieldDefinition;

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
    public void whitelistDefault() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("foo", "bar"));
        QueryStringPlusPlusPlusBuilder builder = builder("foo", "foo:bar");
        assertHitCount(search(builder), 1);
        assertHitCount(search(builder.whitelistDefault(false)), 0);
        assertHitCount(search(builder.whitelistDefault(true)), 1);
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
        QueryStringPlusPlusPlusBuilder builder = builder("aa, bb", "foo").define("aa", new FieldDefinition("a", "qa"));
        SearchResponse response = client().prepareSearch("test").setQuery(builder).get();
        assertSearchHits(response, "1");
        builder = builder("aa, bb", "foo").define("bb", new FieldDefinition("b", "qb"));
        assertSearchHits(search(builder), "2");
        builder = builder("aa, bb", "foo").define("aa", new FieldDefinition("a", "qa")).define("bb", new FieldDefinition("b", "qb"));
        assertHitCount(search(builder), 2);
        builder = builder("aa, bb", "foo").define("a", new FieldDefinition("ua", "qa")).define("b", new FieldDefinition("ub", "qb"));
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
}
