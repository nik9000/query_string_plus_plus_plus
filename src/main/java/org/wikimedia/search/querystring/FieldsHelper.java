package org.wikimedia.search.querystring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.common.collect.ArrayListMultimap;
import org.elasticsearch.common.collect.ListMultimap;
import org.wikimedia.search.querystring.query.FieldDefinition;
import org.wikimedia.search.querystring.query.FieldReference;
import org.wikimedia.search.querystring.query.FieldUsage;

/**
 * Helps QueryParserHelper resolve fields. Note that this class is quite mutable
 * and not thread safe.
 */
public class FieldsHelper {
    /**
     * What to do with fields that are not authorized.
     */
    public static enum UnauthorizedAction {
        KEEP, REMOVE, WHITELIST;
    }

    private final Map<String, FieldDefinition> fields = new HashMap<>();
    private final ListMultimap<String, FieldReference> aliases = ArrayListMultimap.create();
    private final Set<String> blacklist = new HashSet<>();
    private Set<String> whitelist = new HashSet<>();

    /**
     * Defines a field for later use.
     */
    public void addField(String name, FieldDefinition definition) {
        fields.put(name, definition);
    }

    /**
     * Whitelist a field so it can be queried. If whitelistAll this is a noop.
     */
    public void whitelist(String field) {
        if (whitelist != null) {
            whitelist.add(field);
        }
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

    /**
     * Adds an alias from one field name to another with an optional boost.
     */
    public void addAlias(String from, FieldReference reference) {
        aliases.put(from, reference);
    }

    /**
     * Remove an alias.
     */
    public void removeAlias(String from, String to) {
        for (Iterator<FieldReference> usage = aliases.get(from).iterator(); usage.hasNext();) {
            if (usage.next().getName().equals(to)) {
                usage.remove();
            }
        }
    }

    /**
     * Resolves a and whitelist/blacklists. Note that the blacklist is applied
     * before the whitelist.
     */
    public List<FieldUsage> resolve(FieldReference reference, UnauthorizedAction unauthorized) {
        List<FieldUsage> results = new ArrayList<>();
        resolve(reference, unauthorized, results);
        return results;
    }

    /**
     * Just a list form of the resolve method.
     */
    public List<FieldUsage> resolve(Iterable<FieldReference> references, UnauthorizedAction unauthorized) {
        List<FieldUsage> results = new ArrayList<>();
        for (FieldReference reference : references) {
            resolve(reference, unauthorized, results);
        }
        return results;
    }

    private void resolve(FieldReference reference, UnauthorizedAction unauthorized, List<FieldUsage> results) {
        List<FieldReference> found = aliases.get(reference.getName());
        if (found.isEmpty()) {
            switch (unauthorized) {
            case WHITELIST:
                whitelist(reference.getName());
                // Intentional fallthrough
            case KEEP:
                results.add(buildNoAliasResult(reference));
                break;
            case REMOVE:
                if (!allowed(reference.getName())) {
                    return;
                }
                results.add(buildNoAliasResult(reference));
            }
        }
        for (FieldReference alias : found) {
            switch (unauthorized) {
            case WHITELIST:
                whitelist(alias.getName());
                // Intentional fallthrough
            case KEEP:
                results.add(buildUsage(alias, reference.getBoost()));
                break;
            case REMOVE:
                if (allowed(alias.getName())) {
                    results.add(buildUsage(alias, reference.getBoost()));
                }
                break;
            }
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s %s %s", aliases, whitelist, blacklist);
    }

    private boolean allowed(String field) {
        return !blacklist.contains(field) && (whitelist == null || whitelist.contains(field));
    }

    private FieldUsage buildNoAliasResult(FieldReference reference) {
        return new FieldUsage(definition(reference.getName()), reference.getBoost());
    }

    private FieldUsage buildUsage(FieldReference alias, float boost) {
        return new FieldUsage(definition(alias.getName()), alias.getBoost() * boost);
    }

    private FieldDefinition definition(String field) {
        FieldDefinition result = fields.get(field);
        return result == null ? new FieldDefinition(field, field) : result;
    }
}
