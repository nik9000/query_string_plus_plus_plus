package org.wikimedia.search.querystring.query;

import org.elasticsearch.common.Nullable;

/**
 * Information about how a field should be used when creating queries.
 */
public class FieldDefinition {
    private final String standard;
    private final String precise;
    private final String reversePrecise;

    public FieldDefinition(String standard, @Nullable String precise, @Nullable String reversePrecise) {
        this.standard = standard;
        this.precise = precise;
        this.reversePrecise = reversePrecise;
    }

    public FieldDefinition(String standard, @Nullable String precise) {
        this(standard, precise, null);
    }

    public FieldDefinition(String standard) {
        this(standard, null, null);
    }

    /**
     * The field searched for unquoted, non-wildcard terms. Usually users expect
     * this to be stemmed.
     */
    public String getStandard() {
        return standard;
    }

    /**
     * The field searched for quoted and wildcard terms or null if the unquoted
     * field is the right one to use. Usually users expect quoted terms to be
     * more precise/less stemmed that the field searched for unquoted terms.
     */
    public String getPrecise() {
        return precise;
    }

    /**
     * The field to be searched for wildcard terms that contain leading
     * wildcards or null if there is no such field.
     */
    public String getReversePrecise() {
        return reversePrecise;
    }

    @Override
    public String toString() {
        if (precise == null) {
            return standard;
        }
        StringBuilder b = new StringBuilder(standard.length() + precise.length() + 2);
        b.append(standard);
        b.append('(').append(precise);
        if (reversePrecise != null) {
            b.append('~').append(reversePrecise);
        }
        b.append(')');
        return b.toString();
    }
}
