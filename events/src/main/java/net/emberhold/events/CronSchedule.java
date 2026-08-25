package net.emberhold.events;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * DST-safe cron schedule (spec 06 §B.1, §B.2).
 *
 * <p>Wraps the real cron-utils library (never hand-rolled) and computes next run times in a
 * {@link ZonedDateTime} so daylight-saving transitions are handled correctly. Accepts the
 * QUARTZ six- or seven-field form used by the event YAML ({@code "0 0 13,19,23 * * *"}).</p>
 */
public final class CronSchedule {

    private final Cron cron;
    private final ExecutionTime executionTime;

    private CronSchedule(Cron cron) {
        this.cron = cron;
        this.executionTime = ExecutionTime.forCron(cron);
    }

    /** Parse a cron expression (QUARTZ). @throws IllegalArgumentException if invalid. */
    public static CronSchedule parse(String expr) {
        if (expr == null || expr.isBlank()) {
            throw new IllegalArgumentException("cron expression must be non-empty");
        }
        try {
            CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING));
            return new CronSchedule(parser.parse(expr));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("invalid cron '" + expr + "': " + ex.getMessage(), ex);
        }
    }

    /** The next run strictly after {@code after}, if any. */
    public Optional<ZonedDateTime> nextRun(ZonedDateTime after) {
        return executionTime.nextExecution(after);
    }

    /** Whether {@code instant} coincides with a run (within the current second). */
    public boolean matches(ZonedDateTime instant) {
        return executionTime.isMatch(instant);
    }

    public String expression() {
        return cron.asString();
    }
}
