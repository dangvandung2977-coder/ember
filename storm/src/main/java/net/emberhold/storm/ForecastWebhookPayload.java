package net.emberhold.storm;

import net.emberhold.storm.api.ForecastEvent;

import java.util.List;

/**
 * Serializes the storm forecast to a Discord-webhook payload (spec 03 §3, 08 §4).
 *
 * <p>Produces a deterministic Discord {@code embed} JSON body from a forecast list, so the
 * {@code /forecast} command and the Discord bot render the same schedule. The exact template
 * is not frozen by the spec (08 §4 only specifies retry/async semantics), so this class owns
 * one well-defined shape and exposes {@link #payload(List)} for byte-for-byte testing.</p>
 */
public final class ForecastWebhookPayload {

    private ForecastWebhookPayload() {
    }

    /** Build the webhook JSON body (a single Discord embed) from forecast events. */
    public static String payload(List<ForecastEvent> events) {
        StringBuilder fields = new StringBuilder();
        if (events != null) {
            for (int i = 0; i < events.size(); i++) {
                ForecastEvent e = events.get(i);
                if (i > 0) {
                    fields.append(',');
                }
                fields.append("{\"name\":\"")
                        .append(escape(e.type().name() + ' ' + e.sectorClass()))
                        .append("\",\"value\":\"")
                        .append(e.stable() ? "stable" : "unstable")
                        .append("\"}");
            }
        }
        return "{\"embeds\":[{\"title\":\"EmberStorm Forecast\",\"fields\":[" + fields + "]}]}";
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
