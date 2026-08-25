package net.emberhold.core;

/**
 * Static metadata for the EmberHold plugin. T0 skeletononly — no gameplay logic.
 */
public final class EmberCore {

    public static final String MODULE_ID = "core";
    public static final String NAME = "EmberHold Core";
    public static final String VERSION = "0.1.0";

    private EmberCore() {
    }

    public static String moduleId() {
        return MODULE_ID;
    }
}
