package org.wikimedia.search.querystring.query;

/**
 * Information about how a field should be used when creating queries.
 */
public class FieldDefinition {
    private final String unquoted;
    private final String quoted;

    public FieldDefinition(String unquoted, String quoted) {
        this.unquoted = unquoted;
        this.quoted = quoted;
    }

    /**
     * The field searched for unquoted terms. Usually users expect this to be
     * stemmed to some degree.
     */
    public String getUnquoted() {
        return unquoted;
    }

    /**
     * The field searched for quoted terms. Usually users expect quoted terms to
     * be more precise/less stemmed that the field searched for unquoted terms.
     */
    public String getQuoted() {
        return quoted;
    }

    @Override
    public String toString() {
        if (quoted.equals(unquoted)) {
            return unquoted;
        }
        return unquoted + "(\"" + quoted + "\")";
    }
}
