package net.emberhold.shelter;

import net.emberhold.core.api.Cmd;
import net.emberhold.temperature.api.ShelterVerdict;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Shelter command surface (spec 04 §5): {@code /shelter info} (player, current verdict) and
 * {@code /shelter machines list} (admin). Registered via the declarative {@link Cmd}
 * framework.
 */
public final class ShelterCommand {

    private final EmberShelterModule module;

    public ShelterCommand(EmberShelterModule module) {
        this.module = module;
    }

    @Cmd(name = "shelter.info", perm = "ember.shelter.use", playerOnly = true)
    public String info(Player sender) {
        int x = sender.getLocation().getBlockX();
        int y = sender.getLocation().getBlockY();
        int z = sender.getLocation().getBlockZ();
        return module.verdictAsync(sender.getWorld().getName(), x, y, z)
                .handle((v, err) -> {
                    if (err != null || v == null) {
                        return "Shelter: verdict unavailable (scan error).";
                    }
                    return "Shelter: " + v.verdict()
                            + " | insulation " + String.format(java.util.Locale.ROOT, "%.2f", v.structureInsulation())
                            + " | heatBonus +" + String.format(java.util.Locale.ROOT, "%.1f", v.heatBonus());
                }).join();
    }

    @Cmd(name = "shelter.machines-list", perm = "ember.shelter.admin", playerOnly = false)
    public String machines(CommandSender sender) {
        List<BlockPosition> list = module.registry().positions();
        if (list.isEmpty()) {
            return "No machines placed.";
        }
        StringBuilder sb = new StringBuilder("Machines (" + list.size() + "):\n");
        for (BlockPosition p : list) {
            MachineRuntime rt = module.registry().get(p).orElse(null);
            sb.append("  ").append(p).append(" fuel=").append(rt == null ? "?" : String.format(java.util.Locale.ROOT, "%.1f", rt.fuelFeu()))
                    .append(rt != null && rt.enabled() ? " on" : " off").append('\n');
        }
        return sb.toString();
    }

    @Cmd(name = "shelter.place", perm = "ember.shelter.admin", playerOnly = true)
    public String place(Player sender, String type) {
        MachineType t;
        try {
            t = MachineType.valueOf(type.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return "Unknown machine type: " + type;
        }
        int x = sender.getLocation().getBlockX();
        int y = sender.getLocation().getBlockY();
        int z = sender.getLocation().getBlockZ();
        module.placeMachine(new BlockPosition(sender.getWorld().getName(), x, y, z), t, 0);
        return "Placed " + t + " at " + x + "," + y + "," + z + ".";
    }

    @Cmd(name = "shelter.fuel", perm = "ember.shelter.admin", playerOnly = true)
    public String fuel(Player sender, double feu) {
        int x = sender.getLocation().getBlockX();
        int y = sender.getLocation().getBlockY();
        int z = sender.getLocation().getBlockZ();
        java.util.Optional<MachineRuntime> rt = module.registry().get(
                new BlockPosition(sender.getWorld().getName(), x, y, z));
        if (rt.isEmpty()) {
            return "No machine here to refuel.";
        }
        rt.get().refuel(feu);
        return "Refuelled +" + feu + " FEU (now " + String.format(java.util.Locale.ROOT, "%.1f", rt.get().fuelFeu()) + ").";
    }

    @Cmd(name = "shelter.remove", perm = "ember.shelter.admin", playerOnly = true)
    public String remove(Player sender) {
        int x = sender.getLocation().getBlockX();
        int y = sender.getLocation().getBlockY();
        int z = sender.getLocation().getBlockZ();
        module.removeMachine(new BlockPosition(sender.getWorld().getName(), x, y, z));
        return "Removed machine at " + x + "," + y + "," + z + " (if any).";
    }
}
