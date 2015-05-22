package org.wikimedia.search.querystring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.elasticsearch.common.collect.ArrayListMultimap;
import org.elasticsearch.common.collect.ListMultimap;
import org.wikimedia.search.querystring.query.FieldDefinition;

/**
 * Helps QueryParserHelper resolve fields.
 */
public class FieldsHelper {
    /**
     * What to do with fields that are not authorized.
     */
    public static enum UnauthorizedAction {
        KEEP, REMOVE, WHITELIST;
    }

    private final ListMultimap<String, FieldDefinition> aliases = ArrayListMultimap.create();
    private final Set<String> blacklist = new HashSet<>();
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

    public void addAlias(String from, FieldDefinition to) {
        aliases.put(from, to);
    }

    public void removeAlias(String from, FieldDefinition to) {
        aliases.remove(from, to);
    }

    /**
     * Resolves aliases and whitelist/blacklists. Note that the blacklist is
     * applied before the whitelist.
     */
    public List<FieldDefinition> resolve(String fieldName, float boost, UnauthorizedAction unauthorized) {
        List<FieldDefinition> found = aliases.get(fieldName);
        if (found.isEmpty()) {
            switch (unauthorized) {
            case WHITELIST:
                whitelist(fieldName);
                // Intentional fallthrough
            case KEEP:
                return noAlias(fieldName, boost);
            case REMOVE:
                if (!allowed(fieldName)) {
                    return Collections.emptyList();
                }
                return noAlias(fieldName, boost);
            }
        }
        List<FieldDefinition> allowed = new ArrayList<>();
        for (FieldDefinition alias : found) {
            switch (unauthorized) {
            case WHITELIST:
                whitelist(alias.getField());
                whitelist(alias.getPhraseField());
                // Intentional fallthrough
            case KEEP:
                allowed.add(boosted(alias, boost));
                break;
            case REMOVE:
                if (allowed(alias.getField()) && allowed(alias.getPhraseField())) {
                    allowed.add(boosted(alias, boost));
                }
                break;
            }
        }
        return allowed;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s %s %s", aliases, whitelist, blacklist);
    }

    private boolean allowed(String field) {
        return !blacklist.contains(field) && (whitelist == null || whitelist.contains(field));
    }

    private List<FieldDefinition> noAlias(String fieldName, float boost) {
        return Collections.singletonList(new FieldDefinition(fieldName, fieldName, boost));
    }

    private FieldDefinition boosted(FieldDefinition original, float boost) {
        if (boost == 1) {
            return original;
        }
        return new FieldDefinition(original.getField(), original.getPhraseField(), original.getBoost() * boost);
    }
}
