package net.emberhold.storm;

import net.emberhold.core.api.AuditLog;
import net.emberhold.core.api.Cmd;
import net.emberhold.storm.api.ForecastEvent;
import net.emberhold.storm.api.Sector;
import net.emberhold.storm.api.SectorWeather;
import net.emberhold.storm.api.StormState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

/**
 * Storm admin/player command surface (spec 03 §5): {@code /storm now}, {@code /storm
 * schedule}, {@code /storm force}. Registered via the declarative {@link Cmd} framework.
 * {@code /storm force} is admin-only and records an {@link AuditLog} entry.
 */
public final class StormCommand {

    private final Plugin plugin;
    private final StormModule module;

    public StormCommand(Plugin plugin, StormModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Cmd(name = "storm.now", perm = "ember.storm.use", playerOnly = true)
    public String now(Player sender) {
        StormDirector dir = module.director();
        double size = 512.0;
        Sector s = Sector.ofBlock(sender.getLocation().getX(), sender.getLocation().getZ(), size);
        SectorWeather w = dir.currentWeather(s);
        return "Storm @" + s + " = " + w.state() + " (eat " + w.eatDelta() + " / wind " + w.windFactor() + ")";
    }

    @Cmd(name = "storm.schedule", perm = "ember.storm.use", playerOnly = false)
    public String schedule(CommandSender sender) {
        module.refreshForecast();
        List<ForecastEvent> next = module.forecast().next24h();
        if (next.isEmpty()) {
            return "No storm events forecast in the next 24h.";
        }
        StringBuilder sb = new StringBuilder("Next 24h storm:\n");
        for (ForecastEvent e : next) {
            sb.append("  ").append(e.type().name()).append(" @").append(e.sectorClass())
                    .append(" start=").append(e.startEpochSec())
                    .append(" dur=").append(e.durationSec()).append("s")
                    .append(e.stable() ? " (stable)" : " (unstable)").append('\n');
        }
        return sb.toString();
    }

    @Cmd(name = "storm.force", perm = "ember.storm.admin", playerOnly = false)
    public String force(CommandSender sender, String state) {
        StormState target;
        try {
            target = StormState.valueOf(state.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "Unknown storm state: " + state + " (use CALM/SNOWFALL/HEAVY_SNOW/BLIZZARD/WHITEOUT/EXTREME)";
        }
        // Force by spawning a front of that intensity at world origin for demonstration.
        double intensity = intensityFor(target);
        module.director().spawnFront(
                FrontMovement.spawn(java.util.UUID.randomUUID(), 0, 0, 0, 0, intensity,
                        module.director().currentTick()));
        audit(sender.getName(), "storm.force", target.name());
        return "Forced " + target + " (front intensity " + intensity + ").";
    }

    private static double intensityFor(StormState state) {
        return switch (state) {
            case CALM -> 0.0;
            case SNOWFALL -> 0.25;
            case HEAVY_SNOW -> 0.5;
            case BLIZZARD -> 0.72;
            case WHITEOUT, EXTREME -> 0.95;
        };
    }

    private void audit(String actor, String action, String target) {
        module.api().audit().record(actor, action, target, Map.of("via", "storm.force"));
    }
}
