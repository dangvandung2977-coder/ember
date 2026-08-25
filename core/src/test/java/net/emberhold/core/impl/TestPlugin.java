package net.emberhold.core.impl;

import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;

/**
 * Test helper: builds a dynamic proxy for a Bukkit {@link Plugin} so DB-related
 * components can be constructed without a live server. Returns primitive defaults
 * for method return types; DB components only hold the reference, never call it.
 */
final class TestPlugin {

    static Plugin proxy() {
        return proxy(Plugin.class);
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
            TestPlugin.class.getClassLoader(),
            new Class<?>[]{type},
            (proxy, method, args) -> {
                Class<?> ret = method.getReturnType();
                if (ret == boolean.class) return false;
                if (ret == int.class) return 0;
                if (ret == long.class) return 0L;
                if (ret == double.class) return 0.0d;
                if (ret == float.class) return 0.0f;
                if (ret == short.class) return (short) 0;
                if (ret == byte.class) return (byte) 0;
                if (ret == char.class) return '\0';
                return null;
            });
    }
}
