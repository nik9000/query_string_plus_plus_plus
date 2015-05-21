package org.wikimedia.search.querystring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.elasticsearch.common.collect.ArrayListMultimap;
import org.elasticsearch.common.collect.ListMultimap;

/**
 * Helps QueryParserHelper resolve fields.
 */
public class FieldsHelper {
    private final Set<String> blacklist = new HashSet<>();
    private final ListMultimap<String, String> synonyms = ArrayListMultimap.create();
    private Set<String> whitelist = new HashSet<>();

    /**
     * Whitelist a field so it can be queried. If whitelistAll has been called
     * this will undo its effects.
     */
    public void whitelist(String field) {
        if (whitelist == null) {
            whitelist = new HashSet<>();
        }
        whitelist.add(field);
    }

    /**
     * Whitelists all fields that are no explicitly blacklisted. This is
     * probably a bad idea.
     */
    public void whitelistAll() {
        this.whitelist = null;
    }

    /**
     * Blacklist a field so it'll never be queried.
     */
    public void blacklist(String field) {
        blacklist.add(field);
    }

    public void addSynonym(String from, String to) {
        synonyms.put(from, to);
    }

    public void removeSynonym(String from, String to) {
        synonyms.remove(from, to);
    }

    /**
     * Resolves synonyms and whitelist/blacklists. Note that the blacklist is
     * applied before the whitelist.
     */
    public List<String> resolve(String from) {
        List<String> found = synonyms.get(from);
        if (found.isEmpty()) {
            if (allowed(from)) {
                return Collections.singletonList(from);
            }
            return Collections.emptyList();
        }
        List<String> allowed = new ArrayList<String>();
        for (String s : found) {
            if (allowed(s)) {
                allowed.add(s);
            }
        }
        return allowed;
    }

    private boolean allowed(String field) {
        return !blacklist.contains(field) && (whitelist == null || whitelist.contains(field));
    }
}
