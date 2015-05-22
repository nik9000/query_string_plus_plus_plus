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

public class IntegrationTest extends ElasticsearchIntegrationTest {
    @Test
    public void basic() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("foo", "bar"));
        SearchResponse response = client().prepareSearch("test").setQuery(builder("foo", "bar")).get();
        assertSearchHits(response, "1");
        response = client().prepareSearch("test").setQuery(builder("foo", "bort")).get();
        assertHitCount(response, 0);
        response = client().prepareSearch("test").setQuery(builder("bort", "bar")).get();
        assertHitCount(response, 0);
    }

    @Test
    public void fields() throws InterruptedException, ExecutionException {
        indexRandom(true, client().prepareIndex("test", "test", "1").setSource("a", "foo"),
                client().prepareIndex("test", "test", "2").setSource("b", "foo"));
        SearchResponse response = client().prepareSearch("test").setQuery(builder("a, b", "foo")).get();
        assertHitCount(response, 2);
        response = client().prepareSearch("test").setQuery(builder("a, b^2", "foo")).get();
        assertSearchHits(response, "2", "1");
        response = client().prepareSearch("test").setQuery(builder("a^2, b", "foo")).get();
        assertSearchHits(response, "1", "2");
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

}
