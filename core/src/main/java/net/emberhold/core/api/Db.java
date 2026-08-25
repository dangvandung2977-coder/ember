package net.emberhold.core.api;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Async-only DB facade (spec 01 §6). Only Core opens connections; modules use this.
 * All methods are non-blocking and must be called off the game thread (async path).
 */
public interface Db {

    /**
     * Runs {@code fn} on the async pool and returns its result. Throws
     * {@link IllegalStateException} if invoked on the game thread (see BlockingGuard).
     */
    <T> CompletableFuture<T> withConnection(Function<Connection, T> fn);

    /** Same as {@link #withConnection} but wraps in a transaction (commit/rollback). */
    <T> CompletableFuture<T> inTransaction(Function<Connection, T> fn);

    /** True when currently executing on the Minecraft server thread. */
    boolean isGameThread();
}
