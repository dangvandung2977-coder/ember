package net.emberhold.settlement;

import net.emberhold.core.api.Cmd;
import net.emberhold.settlement.api.Hold;
import net.emberhold.settlement.api.HoldRole;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Hold command surface (spec 07 §A.3): {@code /ember hold create}, {@code /ember hold info},
 * {@code /ember hold members}, {@code /ember hold contribute <feu>}, {@code /ember hold treasury}.
 * Registered via the declarative {@link Cmd} framework.
 */
public final class HoldCommand {

    private static final double MIN_CONTRIBUTION = 0.01;

    private final EmberSettlementModule module;

    public HoldCommand(EmberSettlementModule module) {
        this.module = module;
    }

    @Cmd(name = "ember.hold.create", perm = "ember.hold.create", playerOnly = true)
    public String create(Player sender, String name) {
        if (name == null || name.isBlank()) {
            return "Usage: /ember hold create <name>";
        }
        if (module.registry().byOwner(sender.getUniqueId()).isPresent()) {
            return "You already own a hold.";
        }
        Hold h = module.registry().create(name.trim(), sender.getUniqueId());
        module.persist(h);
        return "Created hold #" + h.id() + " '" + h.name() + "'.";
    }

    @Cmd(name = "ember.hold.info", perm = "ember.hold.info", playerOnly = true)
    public String info(Player sender) {
        Hold h = module.registry().byOwner(sender.getUniqueId()).orElse(null);
        if (h == null) {
            return "You do not own a hold.";
        }
        return "Hold #" + h.id() + " '" + h.name() + "' level " + h.level()
                + " | fuelFeu " + String.format(java.util.Locale.ROOT, "%.0f", h.genFuelFeu())
                + " | radius " + String.format(java.util.Locale.ROOT, "%.1f", h.genRadiusScale())
                + " | treasury " + String.format(java.util.Locale.ROOT, "%.0f", module.treasury().balance(h.id()))
                + " | roster " + module.registry().rosterSize(h.id());
    }

    @Cmd(name = "ember.hold.members", perm = "ember.hold.info", playerOnly = true)
    public String members(Player sender) {
        Hold h = module.registry().byOwner(sender.getUniqueId()).orElse(null);
        if (h == null) {
            return "You do not own a hold.";
        }
        StringBuilder sb = new StringBuilder("Hold #" + h.id() + " members:\n");
        for (UUID m : module.registry().members(h.id())) {
            sb.append("  ").append(m).append(" (").append(module.registry().role(h.id(), m)).append(")\n");
        }
        return sb.toString();
    }

    @Cmd(name = "ember.hold.contribute", perm = "ember.hold.contribute", playerOnly = true)
    public String contribute(Player sender, double feu) {
        if (feu < MIN_CONTRIBUTION) {
            return "Contribution must be at least " + MIN_CONTRIBUTION + " FEU.";
        }
        Hold h = module.registry().byOwner(sender.getUniqueId()).orElse(null);
        if (h == null) {
            return "You do not own a hold.";
        }
        long tick = module.currentTick();
        boolean applied = module.ledger().record(h.id(), sender.getUniqueId(), "player", tick, feu);
        return applied ? "Recorded " + feu + " FEU contribution (tx " + ContributionLedger.txKey("player", tick, feu) + ")."
                : "Contribution already recorded (duplicate tx, no double-count).";
    }

    @Cmd(name = "ember.hold.treasury", perm = "ember.hold.officer", playerOnly = true)
    public String treasury(CommandSender sender, double amount) {
        UUID actor = sender instanceof Player p ? p.getUniqueId() : null;
        if (amount == 0) {
            return "Usage: /ember hold treasury <amount>";
        }
        long holdId = module.currentHoldId(actor == null ? UUID.randomUUID() : actor);
        boolean ok = module.treasury().deposit(holdId, amount, actor, "holder-command:" + amount);
        return ok ? "Deposited " + amount + " Scrip to treasury #" + holdId + "."
                : "Treasury deposit rejected.";
    }

    @Cmd(name = "ember.hold.role", perm = "ember.hold.officer", playerOnly = true)
    public String role(Player sender, UUID member, HoldRole role) {
        Hold h = module.registry().byOwner(sender.getUniqueId()).orElse(null);
        if (h == null) {
            return "You do not own a hold.";
        }
        module.registry().setRole(h.id(), member, role);
        module.persist(h);
        return "Set " + member + " role to " + role + " in hold #" + h.id() + ".";
    }
}
