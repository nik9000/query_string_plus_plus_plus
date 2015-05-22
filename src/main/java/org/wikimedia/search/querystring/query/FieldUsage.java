package org.wikimedia.search.querystring.query;

/**
 * Enough information about a field to build any query. Build by calling
 * FieldsHelper.resolve so its safe to assume that aliases have been expanded
 * and the whitelist and blacklist have been checked.
 */
public class FieldUsage {
    private final FieldDefinition field;
    private final float boost;

    public FieldUsage(FieldDefinition field, float boost) {
        this.field = field;
        this.boost = boost;
    }

    public FieldDefinition getField() {
        return field;
    }

    public float getBoost() {
        return boost;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(field);
        if (boost != 1) {
            b.append('^').append(boost);
        }
        return b.toString();
    }
}
