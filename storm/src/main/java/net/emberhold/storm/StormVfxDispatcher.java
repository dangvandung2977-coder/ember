package net.emberhold.storm;

import net.emberhold.storm.api.Sector;
import net.emberhold.storm.api.StormState;

import java.util.UUID;

/**
 * Abstraction for sending VFX/audio to a player (spec 03 §4).
 *
 * <p>The director asks the dispatcher to emit a cue for a state transition, ambient snow or
 * a gust streak. The concrete implementation (bound to packetevents on the live server) is
 * responsible for packet construction and per-player visibility culling; this interface keeps
 * the game-logic module free of a packetevents dependency, so the whole budget/priority logic
 * stays testable without the packet library. Callers are expected to gate each call on the
 * {@link TokenBucket} first.</p>
 */
public interface StormVfxDispatcher {

    /** Emit the state-transition cue (a brief overworld/audio pulse). */
    void cueTransition(UUID player, Sector sector, StormState oldState, StormState newState);

    /** Emit ambient snow for a sector (a few particles). */
    void ambientSnow(UUID player, Sector sector, Stroke stroke);

    /** Emit a gust streak (a line of gust particles / sound). */
    void gustStreak(UUID player, Sector sector, double windFactor);

    /** A short visual stroke spec (lightweight value). */
    record Stroke(int x, int z, int y, double intensity) {
    }
}
