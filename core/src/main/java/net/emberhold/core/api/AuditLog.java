package net.emberhold.core.api;

import java.util.Map;

/**
 * Audit log for staff + economy actions (spec 01 §9, 07 §B).
 * Every Scrip mutation and staff command must be recorded here.
 *
 * TODO(spec): spec 01 §2 states {@code JsonNode data}. Using {@code Map<String,Object>}
 * instead to avoid introducing a non-spec Jackson compile dependency into the core API.
 * Migrate to JsonNode (or own tiny JSON map) if/when we adopt a JSON lib in core.
 */
public interface AuditLog {

    void record(String actor, String action, String target, Map<String, Object> data);
}
