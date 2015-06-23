package org.wikimedia.search.querystring;

/**
 * Thrown when there is a parse error on the query string. These are always
 * bugs.
 */
public class ParseErrorException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ParseErrorException(String message, Throwable cause) {
        super(message, cause);
    }

    public ParseErrorException(String message) {
        super(message);
    }
}
