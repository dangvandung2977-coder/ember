package net.emberhold.core.impl;

/**
 * Thrown by {@link ConfigBinder} when a config value cannot bind to a record
 * component. Message is "path + expected + got" for fail-fast diagnostics at load
 * (conventions §4) — never a silent fallback.
 */
public final class ConfigBindException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigBindException(String path, String expected, String got) {
        super(path.isEmpty() ? ("expected " + expected + " but got: " + got)
            : ("config path '" + path + "': expected " + expected + " but got: " + got));
    }
}
