package net.emberhold.expedition;

import net.emberhold.core.api.Cmd;
import net.emberhold.expedition.api.ExpeditionState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Expedition command surface (spec 05 §6): {@code /exp start <tier>}, {@code /exp status}
 * and {@code /extract} (hint, 60 s cooldown). Registered via the declarative {@link Cmd}
 * framework.
 */
public final class ExpCommand {

    /** Extract-hint cooldown (spec §6: 60 s). */
    public static final long HINT_COOLDOWN_SECONDS = 60;

    private final EmberExpeditionModule module;
    private final Map<UUID, Long> lastHint = new HashMap<>();

    public ExpCommand(EmberExpeditionModule module) {
        this.module = module;
    }

    @Cmd(name = "exp.start", perm = "ember.exp.use", playerOnly = true)
    public String start(Player sender, int tier) {
        if (tier < 1 || tier > 3) {
            return "Tier must be 1-3.";
        }
        if (!module.zoneProvider().supportsTier(tier)) {
            return "Tier " + tier + " zone not available yet.";
        }
        return module.startExpedition(sender, tier);
    }

    @Cmd(name = "exp.status", perm = "ember.exp.use", playerOnly = true)
    public String status(Player sender) {
        String partyId = module.partyOf(sender.getUniqueId());
        if (partyId == null) {
            return "You are not in an expedition.";
        }
        ExpeditionSession s = module.registry().get(partyId).orElse(null);
        if (s == null) {
            return "No active expedition for your party.";
        }
        long now = System.currentTimeMillis() / 1000L;
        return "Expedition " + partyId + " tier " + s.tier()
                + " | " + s.state()
                + " | ring r=" + String.format(java.util.Locale.ROOT, "%.0f", s.radius(now))
                + " | phase " + s.phaseIndex(now)
                + " | time-left " + (s.timeLeftSec(now) / 60) + "m";
    }

    @Cmd(name = "extract", perm = "ember.exp.use", playerOnly = true)
    public String extract(Player sender) {
        long now = System.currentTimeMillis() / 1000L;
        Long last = lastHint.get(sender.getUniqueId());
        if (last != null && now - last < HINT_COOLDOWN_SECONDS) {
            return "Extract hint cooling down.";
        }
        lastHint.put(sender.getUniqueId(), now);
        String partyId = module.partyOf(sender.getUniqueId());
        if (partyId == null) {
            return "No active expedition — nothing to extract from.";
        }
        return "Extract beacon is ahead (ring is closing). Stand in the beacon to channel.";
    }

    @Cmd(name = "exp.force", perm = "ember.exp.admin", playerOnly = false)
    public String force(CommandSender sender, String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return "Usage: /exp force <partyId>";
        }
        ExpeditionSession s = module.registry().get(partyId).orElse(null);
        if (s == null) {
            return "No active expedition for party " + partyId + ".";
        }
        s.wipe();
        return "Force-ended expedition " + partyId + " (WIPED).";
    }
}
