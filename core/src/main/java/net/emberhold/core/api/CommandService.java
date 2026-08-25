package net.emberhold.core.api;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Declarative command framework (spec 01 §7). Register objects whose public methods
 * carry {@link Cmd}; the service binds them into Bukkit, resolves typed arguments,
 * auto-completes tab, enforces permissions and applies the denied-log rule.
 */
public interface CommandService {

    /** Register a handler object; every {@link Cmd}-annotated method becomes a command. */
    void register(Object handler);

    /** Look up command usage for a permission-denied debug log. */
    List<String> usage(String commandName);
}
