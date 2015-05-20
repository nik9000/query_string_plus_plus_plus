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
}
