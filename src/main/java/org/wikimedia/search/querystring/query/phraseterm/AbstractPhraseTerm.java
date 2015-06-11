package org.wikimedia.search.querystring.query.phraseterm;

import org.wikimedia.search.querystring.query.PhraseTerm;

public abstract class AbstractPhraseTerm implements PhraseTerm {
    private final String string;

    public AbstractPhraseTerm(String string) {
        this.string = string;
    }

    @Override
    public String rawString() {
        return string;
    }

    @Override
    public String toString() {
        return rawString();
    }
}