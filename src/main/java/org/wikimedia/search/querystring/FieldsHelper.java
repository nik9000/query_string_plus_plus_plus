package org.wikimedia.search.querystring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.common.collect.ArrayListMultimap;
import org.elasticsearch.common.collect.ListMultimap;
import org.elasticsearch.common.collect.Tuple;
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

    /**
     * Registered fields.
     */
    private final Map<String, FieldDefinition> fields = new HashMap<>();
    /**
     * Fields with their analyzers resolved.
     */
    private final Map<String, FieldUsage> resolvedFields = new HashMap<>();
    private final ListMultimap<String, FieldReference> aliases = ArrayListMultimap.create();
    private final Set<String> blacklist = new HashSet<>();
    private final FieldResolver resolver;
    private Set<String> whitelist = new HashSet<>();

    public FieldsHelper(FieldResolver resolver) {
        this.resolver = resolver;
    }

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
        return lookupOrBuild(reference.getName(), reference.getBoost());
    }

    private FieldUsage buildUsage(FieldReference alias, float boost) {
        return lookupOrBuild(alias.getName(), alias.getBoost() * boost);
    }

    private FieldUsage lookupOrBuild(String field, float boost) {
        FieldUsage canonical = resolvedFields.get(field);
        if (canonical == null) {
            Tuple<String, Analyzer> standard;
            Tuple<String, Analyzer> precise;
            Tuple<String, Analyzer> reversePrecise;
            Tuple<String, Analyzer> prefixPrecise;

            FieldDefinition definition = fields.get(field);
            if (definition == null) {
                // There isn't a definition so we have to guess.
                /*
                 * If the standard field isn't mapped we just use the standard
                 * mapper. We probably won't find anything but that is what the
                 * user expect when they search for a field that doesn't exist.
                 */
                standard = resolve(field, field, resolver.defaultStandardSearchAnalyzer());
                /*
                 * For all the other fields we just don't use them if they
                 * aren't mapped. This is appropriate since we're just guessing
                 * at their names based on a pattern.
                 */
                precise = resolve(field + ".precise", null, null);
                reversePrecise = resolve(field + ".reverse_precise", null, null);
                prefixPrecise = resolve(field + ".prefix_precise", null, null);
            } else {
                // Found the definition so lets look up the fields.
                /*
                 * If the standard or precise fields aren't found we search on
                 * them anyway and get no results. We're doing our best to honor
                 * the user's request here.
                 */
                standard = resolve(definition.getStandard(), definition.getStandard(), resolver.defaultStandardSearchAnalyzer());
                precise = resolve(definition.getPrecise(), definition.getPrecise(), resolver.defaultPreciseSearchAnalyzer());
                /*
                 * If the reversePrecise or prefixPrecise fields aren't found we
                 * just don't use them for optimizations. We should warn the
                 * user somehow but we don't.
                 */
                reversePrecise = resolve(definition.getReversePrecise(), null, null);
                prefixPrecise = resolve(definition.getPrefixPrecise(), null, null);
            }
            canonical = new FieldUsage(standard.v1(), standard.v2(),
                    precise.v1(), precise.v2(),
                    reversePrecise.v1(), reversePrecise.v2(),
                    prefixPrecise.v1(), prefixPrecise.v2(),
                    1);
            resolvedFields.put(field, canonical);
        }
        if (boost == 1) {
            return canonical;
        }
        return new FieldUsage(canonical.getStandard(), canonical.getStandardSearchAnalyzer(),
                canonical.getPrecise(), canonical.getPreciseSearchAnalyzer(),
                canonical.getReversePrecise(), canonical.getReversePreciseSearchAnalyzer(),
                canonical.getPrefixPrecise(), canonical.getPrefixPreciseSearchAnalyzer(),
                canonical.getBoost() * boost);
    }

    private Tuple<String, Analyzer> resolve(String fieldName, String defaultFieldName, Analyzer defaultAnalyzer) {
        Tuple<String, Analyzer> result = resolver.resolve(fieldName);
        if (result != null) {
            return result;
        }
        return new Tuple<>(defaultFieldName, defaultAnalyzer);
    }
}
