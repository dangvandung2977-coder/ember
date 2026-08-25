package net.emberhold.events;

import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML → {@link EventDefinition} parser (spec 06 §B.1).
 *
 * <p>Parses the {@code modules/events/<event>.yml} schema and validates fail-fast via
 * {@link EventDefinition#validated()}. On any schema shape we don't recognise, throws a clear
 * {@link IllegalArgumentException} naming the offending key.</p>
 */
public final class EventDefinitionParser {

    private final Yaml yaml = new Yaml();

    public EventDefinition parse(String yamlText) {
        Object loaded = yaml.load(yamlText);
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("event YAML must be a map");
        }
        String id = str(root, "id");
        String typeRaw = str(root, "type");
        EventType type = typeRaw == null ? null : parseType(typeRaw);
        String schedule = str(root, "schedule");

        int announceLead = 0;
        List<String> channels = List.of();
        Map<?, ?> announce = mapOrNull(root, "announce");
        if (announce != null) {
            announceLead = intVal(announce, "lead-minutes") * 60;
            channels = strList(announce, "channel");
        }

        List<EventPhase> phases = new ArrayList<>();
        Object phasesRaw = root.get("phases");
        if (!(phasesRaw instanceof List<?> phaseList)) {
            throw new IllegalArgumentException("event '" + id + "' requires a 'phases' list");
        }
        for (Object po : phaseList) {
            if (!(po instanceof Map<?, ?> pm)) {
                throw new IllegalArgumentException("event '" + id + "' has a non-map phase");
            }
            String pid = str(pm, "id");
            double dur = doubleVal(pm, "duration-s");
            phases.add(new EventPhase(pid, dur, strList(pm, "effects"), strList(pm, "mechanics"),
                    strList(pm, "mobs")));
        }

        double throttleMinHours = 0;
        Object throttleRaw = root.get("throttle");
        if (throttleRaw instanceof Map<?, ?> tm) {
            throttleMinHours = doubleVal(tm, "min-gap-hours");
        }

        return new EventDefinition(id, type, schedule, announceLead, channels, phases,
                throttleMinHours).validated();
    }

    private static EventType parseType(String raw) {
        try {
            return EventType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown event type '" + raw
                    + "' (expected SCHEDULED | DIRECTOR_HOOK | ADMIN)");
        }
    }

    private static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static int intVal(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v == null ? 0 : (int) Double.parseDouble(String.valueOf(v));
    }

    private static double doubleVal(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return 0;
        }
        return Double.parseDouble(String.valueOf(v));
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (v instanceof String s) {
            return List.of(s);
        }
        throw new IllegalArgumentException("'" + key + "' must be a list or string");
    }

    private static Map<?, ?> mapOrNull(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v instanceof Map<?, ?> mm ? mm : null;
    }
}
