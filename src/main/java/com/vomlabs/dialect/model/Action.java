package com.vomlabs.dialect.model;

public enum Action {
    ALLOW,
    DENY,
    WARN,
    TRANSLATE,
    SUGGEST_CORRECTION,
    LOG_ONLY;

    public boolean isBlocking() {
        return this == DENY || this == WARN;
    }

    public boolean allowsMessage() {
        return this == ALLOW || this == TRANSLATE || this == SUGGEST_CORRECTION || this == LOG_ONLY;
    }

    public boolean modifiesContent() {
        return this == TRANSLATE || this == SUGGEST_CORRECTION;
    }
}
