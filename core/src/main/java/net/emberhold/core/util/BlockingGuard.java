package net.emberhold.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Server;

import java.util.function.BooleanSupplier;

/**
 * Dev-mode guard that catches blocking I/O on the Minecraft server (game) thread.
 * Backed by config {@code ember.dev.blocking-guard} (spec 00 §3, DoD 01 §10).
 *
 * The primary-thread check is injectable so it can be unit-tested without a server.
 */
public final class BlockingGuard {

    public static final String CONFIG_KEY = "ember.dev.blocking-guard";

    private static volatile boolean enabled;

    // Default: real Bukkit primary-thread check. Null-safe when no server is present.
    private static volatile BooleanSupplier primaryThreadCheck =
        () -> { Server s = Bukkit.getServer(); return s != null && s.isPrimaryThread(); };

    // Test hook only (package-private): override the primary-thread check.
    static void setPrimaryThreadCheck(BooleanSupplier supplier) {
        primaryThreadCheck = supplier;
    }

    private BlockingGuard() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean onGameThread() {
        return primaryThreadCheck.getAsBoolean();
    }

    /**
     * Returns {@code true} when it is safe to proceed (NOT on the game thread).
     * If enabled and called on the game thread, throws so the caller must wrap in
     * {@code EmberSchedulers.async(...)}.
     */
    public static boolean allowAsync() {
        if (enabled && onGameThread()) {
            throw new IllegalStateException(
                "Blocking I/O attempted on the Minecraft server thread. "
                    + "Wrap in EmberSchedulers.async(...) instead. (blocking-guard=" + enabled + ")");
        }
        return !onGameThread();
    }
}
