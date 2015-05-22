package org.wikimedia.search.querystring.query;

public class FieldDefinition {
    private final String field;
    private final String phraseField;
    private final float boost;

    public FieldDefinition(String field, String phraseField, float boost) {
        this.field = field;
        this.phraseField = phraseField;
        this.boost = boost;
    }

    public String getField() {
        return field;
    }

    public String getPhraseField() {
        return phraseField;
    }

    public float getBoost() {
        return boost;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(field);
        if (!phraseField.equals(field)) {
            b.append('(').append(phraseField).append(')');
        }
        if (boost != 1) {
            b.append('^').append(boost);
        }
        return b.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Float.floatToIntBits(boost);
        result = prime * result + ((field == null) ? 0 : field.hashCode());
        result = prime * result + ((phraseField == null) ? 0 : phraseField.hashCode());
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
        FieldDefinition other = (FieldDefinition) obj;
        if (Float.floatToIntBits(boost) != Float.floatToIntBits(other.boost))
            return false;
        if (field == null) {
            if (other.field != null)
                return false;
        } else if (!field.equals(other.field))
            return false;
        if (phraseField == null) {
            if (other.phraseField != null)
                return false;
        } else if (!phraseField.equals(other.phraseField))
            return false;
        return true;
    }

}
