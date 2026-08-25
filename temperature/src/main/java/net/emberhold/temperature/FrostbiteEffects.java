package net.emberhold.temperature;

import net.emberhold.temperature.FrostbiteModel.Tier;
import net.emberhold.temperature.FrostbiteModel.Tier;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Applies frostbite effects to a {@link Player} as normal attribute modifiers (spec
 * 02 §2.7), so they are cleanly removed when the tier drops.
 *
 * <p>Modifiers are identified by a stable {@link NamespacedKey} (one per attribute) and
 * we always remove any existing one before adding, so repeated {@link #apply} calls
 * converge to the exact set without leaking stale values.</p>
 *
 * <p>Attribute mapping (spec §2.7.7–9):</p>
 * <ul>
 *   <li>MINING (1–3): {@code MINING_EFFICIENCY} ×(1 + −0.10·stacks)</li>
 *   <li>HEALTH (4–6): {@code MAX_HEALTH} −2♥/stack (ADD_NUMBER)</li>
 *   <li>CONTROL (7–9): {@code MOVEMENT_SPEED} ×0.90</li>
 * </ul>
 */
public final class FrostbiteEffects {

    private final NamespacedKey miningKey;
    private final NamespacedKey healthKey;
    private final NamespacedKey speedKey;

    public FrostbiteEffects(Plugin plugin) {
        String ns = plugin.getName().toLowerCase(java.util.Locale.ROOT);
        this.miningKey = new NamespacedKey(ns, "frostbite_mining");
        this.healthKey = new NamespacedKey(ns, "frostbite_health");
        this.speedKey = new NamespacedKey(ns, "frostbite_speed");
    }

    /**
     * Apply the attribute modifiers for the player's current frostbite tier, adding the
     * frostbite modifier where needed and removing it when the tier leaves the relevant
     * band. Idempotent and cheap; call once per player per tick.
     */
    public void apply(Player player, int stacks) {
        Tier tier = FrostbiteModel.tierFor(stacks);
        setMining(player, tier, stacks);
        setHealth(player, tier, stacks);
        setSpeed(player, tier);
    }

    /** @return the existing frostbite modifier on {@code inst} (matched by key), else null. */
    private AttributeModifier existing(AttributeInstance inst, NamespacedKey key) {
        for (AttributeModifier m : inst.getModifiers()) {
            if (key.equals(m.getKey())) {
                return m;
            }
        }
        return null;
    }

    private void setMining(Player player, Tier tier, int stacks) {
        AttributeInstance inst = player.getAttribute(Attribute.MINING_EFFICIENCY);
        if (inst == null) {
            return;
        }
        if (tier == Tier.MINING) {
            double factor = 1.0 + FrostbiteModel.miningSlowdown(stacks); // e.g. 0.70
            AttributeModifier mod = new AttributeModifier(
                    miningKey, factor, Operation.MULTIPLY_SCALAR_1);
            AttributeModifier old = existing(inst, miningKey);
            if (old != null && old.getAmount() != factor) {
                inst.removeModifier(old);
            }
            if (existing(inst, miningKey) == null) {
                inst.addModifier(mod);
            }
        } else {
            AttributeModifier old = existing(inst, miningKey);
            if (old != null) {
                inst.removeModifier(old);
            }
        }
    }

    private void setHealth(Player player, Tier tier, int stacks) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        if (inst == null) {
            return;
        }
        if (tier == Tier.HEALTH) {
            double delta = FrostbiteModel.maxHealthDelta(stacks); // negative
            AttributeModifier mod = new AttributeModifier(healthKey, delta, Operation.ADD_NUMBER);
            AttributeModifier old = existing(inst, healthKey);
            if (old != null && old.getAmount() != delta) {
                inst.removeModifier(old);
            }
            if (existing(inst, healthKey) == null) {
                inst.addModifier(mod);
            }
            // Clamp current health so it can't exceed the reduced max.
            if (player.getHealth() > inst.getValue()) {
                player.setHealth(inst.getValue());
            }
        } else {
            AttributeModifier old = existing(inst, healthKey);
            if (old != null) {
                inst.removeModifier(old);
            }
        }
    }

    private void setSpeed(Player player, Tier tier) {
        AttributeInstance inst = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (inst == null) {
            return;
        }
        if (tier == Tier.CONTROL) {
            AttributeModifier mod = new AttributeModifier(
                    speedKey, FrostbiteModel.CONTROL_SPEED_MULTIPLIER, Operation.MULTIPLY_SCALAR_1);
            AttributeModifier old = existing(inst, speedKey);
            if (old != null && old.getAmount() != FrostbiteModel.CONTROL_SPEED_MULTIPLIER) {
                inst.removeModifier(old);
            }
            if (existing(inst, speedKey) == null) {
                inst.addModifier(mod);
            }
        } else {
            AttributeModifier old = existing(inst, speedKey);
            if (old != null) {
                inst.removeModifier(old);
            }
        }
    }
}
