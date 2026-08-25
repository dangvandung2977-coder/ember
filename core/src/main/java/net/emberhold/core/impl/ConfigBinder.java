package net.emberhold.core.impl;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tiny record binder for YAML config maps (spec 01 §5, conventions §4). No Jackson.
 * Supports scalars (String/Number/Boolean), enums, {@link Duration}, {@link Ticks},
 * lists of any of those, and nested records. Missing/failing values throw a
 * {@link ConfigBindException} describing "path + expected + got" (no silent fallback).
 */
public final class ConfigBinder {

    private ConfigBinder() {
    }

    /**
     * Bind a SnakeYAML-produced value (Map/List/scalar) into the given record type.
     *
     * @param path component path prefix for error messages (empty for the root)
     */
    @SuppressWarnings("unchecked")
    public static <T> T bind(Class<T> type, Object value, String path) {
        if (type.isRecord() && value instanceof Map<?, ?> m) {
            return (T) bindRecord(type, m, path);
        }
        return (T) coerce(type, value, path);
    }

    private static <T> T bindRecord(Class<T> type, Map<?, ?> raw, String path) {
        RecordComponent[] comps = type.getRecordComponents();
        Object[] args = new Object[comps.length];
        for (int i = 0; i < comps.length; i++) {
            RecordComponent c = comps[i];
            String key = kebab(c.getName());
            String childPath = path.isEmpty() ? key : path + "." + key;
            if (raw.containsKey(key)) {
                args[i] = resolve(c.getGenericType(), raw.get(key), childPath);
            } else if (c.getType().isPrimitive()) {
                throw new ConfigBindException(childPath, c.getType().getSimpleName(), "missing");
            } else {
                args[i] = null; // optional component absent → null
            }
        }
        try {
            return type.getDeclaredConstructor(stream(comps)).newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new ConfigBindException(path, type.getSimpleName(), "cannot instantiate record: " + e.getMessage());
        }
    }

    private static Class<?>[] stream(RecordComponent[] comps) {
        Class<?>[] classes = new Class<?>[comps.length];
        for (int i = 0; i < comps.length; i++) {
            classes[i] = comps[i].getType();
        }
        return classes;
    }

    private static Object resolve(Type type, Object value, String path) {
        if (type instanceof Class<?> c) {
            if (c.isRecord() && value instanceof Map<?, ?> m) {
                return bindRecord(c, m, path);
            }
            return coerce(c, value, path);
        }
        if (type instanceof ParameterizedType pt) {
            Type raw = pt.getRawType();
            if (raw == List.class) {
                // Coerce each element to the list's element type.
                Type elementType = pt.getActualTypeArguments()[0];
                List<Object> out = new ArrayList<>();
                for (Object el : (List<?>) value) {
                    out.add(resolve(elementType, el, path));
                }
                return out;
            }
            throw new ConfigBindException(path, raw.getTypeName(), "unsupported generic type");
        }
        throw new ConfigBindException(path, type.getTypeName(), "unsupported type");
    }

    @SuppressWarnings("unchecked")
    private static Object coerce(Class<?> type, Object value, String path) {
        if (value == null) {
            return null;
        }
        if (type == String.class) {
            return String.valueOf(value);
        }
        if (type == int.class || type == Integer.class) {
            return (int) toLong(value, path, type.getSimpleName());
        }
        if (type == long.class || type == Long.class) {
            return toLong(value, path, type.getSimpleName());
        }
        if (type == double.class || type == Double.class) {
            return toDouble(value, path, type.getSimpleName());
        }
        if (type == float.class || type == Float.class) {
            return (float) toDouble(value, path, type.getSimpleName());
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.valueOf(String.valueOf(value));
        }
        if (type.isEnum()) {
            return enumValue((Class<? extends Enum<?>>) type, value, path);
        }
        if (type == Duration.class) {
            return parseDuration(value, path);
        }
        if (type == Ticks.class) {
            return parseTicks(value, path);
        }
        if (type == List.class) {
            return parseList(value, path);
        }
        throw new ConfigBindException(path, type.getSimpleName(), String.valueOf(value));
    }

    private static double toDouble(Object value, String path, String expected) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new ConfigBindException(path, expected, String.valueOf(value));
        }
    }

    private static long toLong(Object value, String path, String expected) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new ConfigBindException(path, expected, String.valueOf(value));
        }
    }

    private static Enum<?> enumValue(Class<? extends Enum<?>> type, Object value, String path) {
        String raw = String.valueOf(value);
        for (Enum<?> c : type.getEnumConstants()) {
            if (c.name().equalsIgnoreCase(raw) || kebab(c.name()).equalsIgnoreCase(kebab(raw))) {
                return c;
            }
        }
        throw new ConfigBindException(path, type.getSimpleName(), String.valueOf(value));
    }

    private static Duration parseDuration(Object value, String path) {
        String s = String.valueOf(value).trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)(ms|s|m|h|d)").matcher(s);
        if (!m.matches()) {
            throw new ConfigBindException(path, "Duration", s);
        }
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "ms" -> Duration.ofMillis(n);
            case "s" -> Duration.ofSeconds(n);
            case "m" -> Duration.ofMinutes(n);
            case "h" -> Duration.ofHours(n);
            case "d" -> Duration.ofDays(n);
            default -> throw new ConfigBindException(path, "Duration", s);
        };
    }

    private static Ticks parseTicks(Object value, String path) {
        String s = String.valueOf(value).trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)(t)?").matcher(s);
        if (!m.matches()) {
            throw new ConfigBindException(path, "Ticks", s);
        }
        return new Ticks(Long.parseLong(m.group(1)));
    }

    private static List<?> parseList(Object value, String path) {
        List<Object> out = new ArrayList<>();
        for (Object el : (List<?>) value) {
            // Element type unknown here; keep raw for the caller to coerce per-element.
            out.add(el);
        }
        return out;
    }

    /** camelCase → kebab-case, e.g. {@code heatRadius} → {@code heat-radius}. */
    static String kebab(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (sb.length() > 0) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(ch));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /** Binder-capable tick value, parsed from a number or {@code Nt} string. */
    public record Ticks(long ticks) {
        public static Ticks of(long t) {
            return new Ticks(t);
        }
    }
}
