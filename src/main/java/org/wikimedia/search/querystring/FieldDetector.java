package org.wikimedia.search.querystring;

/**
 * Resolves field names.
 */
public interface FieldDetector {
    /**
     * Finds this field's index name or null if we can't be sure the field exists.
     */
    String resolveIndexName(String field);

    public static class Noop implements FieldDetector {
        @Override
        public String resolveIndexName(String field) {
            return null;
        }
    }
}
