package net.emberhold.core.impl;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.emberhold.core.api.Cmd;
import net.emberhold.core.api.CommandService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Declarative command framework (spec 01 §7), Paper-native. Registers via
 * {@link JavaPlugin#registerCommand(String, BasicCommand)} — Paper plugins do NOT
 * use YAML command declarations. The first dot segment of {@code @Cmd#name()} is
 * the registered root command; the remainder is the subcommand selector. Typed args
 * are resolved per parameter type, tab-completion is generated from online
 * players/worlds/booleans, permissions are enforced and the denied-log rule (≥3
 * perm-denied / 60s per player) logs the usage at INFO.
 */
public final class SimpleCommandService implements CommandService {

    private record Binding(String root, String selector, String perm, boolean playerOnly,
                           Object handler, Method method) {
    }

    private final Plugin plugin;
    private final Map<String, Binding> bindings = new ConcurrentHashMap<>(); // full name -> binding
    private final Map<String, List<Binding>> byRoot = new ConcurrentHashMap<>();
    private final Map<String, String[]> usage = new ConcurrentHashMap<>();
    private final Map<String, long[]> denials = new ConcurrentHashMap<>();

    public SimpleCommandService(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register(Object handler) {
        for (Method m : handler.getClass().getDeclaredMethods()) {
            Cmd cmd = m.getAnnotation(Cmd.class);
            if (cmd == null) {
                continue;
            }
            String full = cmd.name().toLowerCase(Locale.ROOT);
            int dot = full.indexOf('.');
            String root = dot < 0 ? full : full.substring(0, dot);
            String selector = dot < 0 ? "" : full.substring(dot + 1);
            Binding b = new Binding(root, selector, cmd.perm(), cmd.playerOnly(), handler, m);
            bindings.put(full, b);
            byRoot.computeIfAbsent(root, k -> new ArrayList<>()).add(b);
            usage.put(full, describe(cmd.name(), m));
        }
        for (String root : byRoot.keySet()) {
            registerRoot(root);
        }
    }

    private void registerRoot(String root) {
        if (!(plugin instanceof JavaPlugin jp)) {
            return;
        }
        jp.registerCommand(root, new RootCommand(root));
    }

    /** Paper {@link BasicCommand} adapter for a root command, routing to subcommands. */
    private final class RootCommand implements BasicCommand {
        private final String root;

        RootCommand(String root) {
            this.root = root;
        }

        @Override
        public void execute(CommandSourceStack stack, String[] args) {
            CommandSender sender = stack.getSender();
            String selector = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
            String full = rightNow(root, selector);
            Binding b = bindings.get(full);
            if (b == null) {
                sender.sendMessage("Unknown subcommand '" + selector + "'. Try /" + root + " help.");
                return;
            }
            if (b.playerOnly && !(sender instanceof Player)) {
                sender.sendMessage("This command can only be run by a player.");
                return;
            }
            if (!b.perm.isEmpty() && !sender.hasPermission(b.perm)) {
                recordDenial(sender, full);
                sender.sendMessage("You do not have permission: " + b.perm);
                return;
            }
            String[] methodArgs = args.length > 0 ? tail(args) : args;
            try {
                Object result = b.method.invoke(b.handler, resolveArgs(b, methodArgs, sender));
                handleResult(sender, result);
            } catch (ReflectiveOperationException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                plugin.getLogger().severe("[Cmd] " + full + " failed: " + cause.getMessage());
                sender.sendMessage("Command failed (see server log).");
            }
        }

        @Override
        public Collection<String> suggest(CommandSourceStack stack, String[] args) {
            CommandSender sender = stack.getSender();
            if (args.length == 1) {
                List<String> subs = new ArrayList<>();
                for (Binding b : byRoot.getOrDefault(root, List.of())) {
                    if (!b.selector.isEmpty()) {
                        subs.add(b.selector);
                    }
                }
                return filter(subs, args[0]);
            }
            String selector = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
            Binding b = bindings.get(root + "." + selector);
            if (b == null) {
                return List.of();
            }
            Parameter[] params = b.method.getParameters();
            int idx = args.length - 2; // skip the selector
            if (idx < 0 || idx >= params.length) {
                return List.of();
            }
            return SimpleCommandService.this.suggest(params[idx].getType(), args[args.length - 1]);
        }

        @Override
        public String permission() {
            return null; // per-subcommand perm check in execute()
        }
    }

    private static String rightNow(String root, String selector) {
        return selector.isEmpty() ? root : root + "." + selector;
    }

    private static String[] tail(String[] args) {
        String[] out = new String[args.length - 1];
        System.arraycopy(args, 1, out, 0, out.length);
        return out;
    }

    private void handleResult(CommandSender sender, Object result) {
        if (result instanceof String s) {
            sender.sendMessage(s);
        } else if (result instanceof String[] arr) {
            sender.sendMessage(arr);
        } else if (result instanceof List<?> list) {
            list.forEach(line -> sender.sendMessage(String.valueOf(line)));
        }
    }

    private Object[] resolveArgs(Binding b, String[] args, CommandSender sender) {
        Parameter[] params = b.method.getParameters();
        Object[] out = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            if (type == CommandSender.class) {
                out[i] = sender;
                continue;
            }
            try {
                out[i] = resolve(type, i < args.length ? args[i] : null);
            } catch (RuntimeException e) {
                out[i] = null;
            }
        }
        return out;
    }

    private Object resolve(Class<?> type, String raw) {
        if (raw == null) {
            return null;
        }
        if (type == String.class) {
            return raw;
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(raw);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(raw);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(raw);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(raw);
        }
        if (type == Player.class) {
            return plugin.getServer().getPlayerExact(raw);
        }
        if (type == org.bukkit.World.class) {
            return plugin.getServer().getWorld(raw);
        }
        return null;
    }

    private void recordDenial(CommandSender sender, String command) {
        String key = sender.getName().toLowerCase(Locale.ROOT) + "|" + command;
        long now = System.currentTimeMillis();
        long[] window = denials.compute(key, (k, v) -> denialWindow(v, now));
        if (window[0] == 3) {
            plugin.getLogger().info("[Cmd] " + sender.getName() + " repeatedly denied for '" + command
                + "'. Usage: " + String.join(" ", usage.getOrDefault(command, new String[]{})));
        }
    }

    /**
     * Sliding-window denial counter (spec 01 §7: log usage when a player is perm-denied
     * ≥3 times within 60s). Package-visible for unit testing. Returns [count, windowStart].
     * Resets when the window (60s) has passed or on first denial.
     */
    static long[] denialWindow(long[] prev, long nowMillis) {
        if (prev == null || nowMillis - prev[1] > 60_000) {
            return new long[]{1, nowMillis};
        }
        return new long[]{prev[0] + 1, prev[1]};
    }

    private List<String> suggest(Class<?> type, String prefix) {
        String base = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        if (type == Player.class) {
            return filter(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), base);
        }
        if (type == org.bukkit.World.class) {
            return filter(plugin.getServer().getWorlds().stream().map(org.bukkit.World::getName).toList(), base);
        }
        if (type == boolean.class || type == Boolean.class) {
            return filter(List.of("true", "false"), base);
        }
        return List.of();
    }

    private static List<String> filter(List<String> all, String prefix) {
        String base = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : all) {
            if (s.toLowerCase(Locale.ROOT).startsWith(base)) {
                out.add(s);
            }
        }
        return out;
    }

    private static String[] describe(String name, Method m) {
        List<String> parts = new ArrayList<>();
        parts.add("/" + name);
        for (Parameter p : m.getParameters()) {
            if (p.getType() == CommandSender.class) {
                continue;
            }
            parts.add("<" + p.getType().getSimpleName().toLowerCase(Locale.ROOT) + ">");
        }
        return parts.toArray(String[]::new);
    }

    @Override
    public List<String> usage(String commandName) {
        return List.of(usage.getOrDefault(commandName.toLowerCase(Locale.ROOT), new String[]{}));
    }
}
