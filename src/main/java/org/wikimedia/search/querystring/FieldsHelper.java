package org.wikimedia.search.querystring;

import java.util.Collections;
import java.util.List;

import org.elasticsearch.common.collect.ArrayListMultimap;
import org.elasticsearch.common.collect.ListMultimap;

/**
 * Helps QueryParserHelper resolve fields.
 */
public class FieldsHelper {
    private final ListMultimap<String, String> synonyms = ArrayListMultimap.create();

    public void addSynonym(String from, String to) {
        synonyms.put(from, to);
    }

    public void removeSynonym(String from, String to) {
        synonyms.remove(from, to);
    }

    /**
     * Looks up synonyms and returns them if there are any otherwise returns
     * from wrapped in a list. This is because if a field doesn't have any
     * synonyms then it must itself be the field you are looking for.
     */
    public List<String> resolveSynonyms(String from) {
        List<String> result = synonyms.get(from);
        if (!result.isEmpty()) {
            return result;
        }
        return Collections.singletonList(from);
    }
}
