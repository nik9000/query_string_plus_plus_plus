package org.wikimedia.search.querystring.query;

/**
 * A reference to a field as it can be parsed from the query. Enhanced by
 * FieldsHelper into one of more FieldsUsages to use in building queries.
 */
public class FieldReference {
    private final String name;
    private final float boost;

    public FieldReference(String name, float boost) {
        this.name = name;
        this.boost = boost;
    }

    public String getName() {
        return name;
    }

    public float getBoost() {
        return boost;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Float.floatToIntBits(boost);
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        FieldReference other = (FieldReference) obj;
        if (Float.floatToIntBits(boost) != Float.floatToIntBits(other.boost))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }
}
