package org.bitwisemadness.abilityiss;

import org.bitwisemadness.abilitycore.api.AbilityAvailability;
import org.bitwisemadness.abilitycore.api.AvailabilityState;
import org.bitwisemadness.abilitycore.api.Metric;
import org.bitwisemadness.abilitycore.api.ResourceCost;

/**
 * The per-cell overlay tuple for one spellbook bar cell (ability-iss#3,
 * consuming ability-hotbar#115's {@code availabilityFor} hook).
 *
 * <p>Pure decision, no Minecraft or Iron's Spells imports: the client
 * provider does the ISS reads (client-synced cooldowns, synced mana, the
 * cell's {@code SpellData}) and this class maps them to the core tuple the
 * bar renders. Kept import-clean so the plain JUnit suite can pin the
 * mapping - the fraction direction and the REQ-HOT-08 honesty rule
 * (unknown renders nothing, never a wrong value) are the parts worth
 * locking down.</p>
 *
 * <p>Semantics, matched to the bar's renderer:</p>
 * <ul>
 *   <li><b>Cooldown</b> is the readiness fraction the renderer sweeps by
 *       (0 = just cast, fully covered; 1 = ready, clear) - the inverse of
 *       ISS's {@code getCooldownPercent} (remaining/total). Unknown when no
 *       cooldown entry exists: no overlay, not a fake full or empty
 *       sweep.</li>
 *   <li><b>Cost</b> is the spell's mana cost when it has one, badged as an
 *       {@code irons_spellbooks:mana} resource (the renderer draws the
 *       badge bar; the id stays self-describing for any future resolution).
 *       Free spells stay unknown - no badge for nothing.</li>
 *   <li><b>Availability</b> dims the cell (the renderer's unavailable tint)
 *       when the spell cannot be cast right now: locked, or mana below the
 *       cost. Cooldowns are deliberately not a state - their sweep already
 *       shows them, and ISS's own HUD does not dim for them either.</li>
 * </ul>
 */
public final class SpellCellOverlays {

    /** The resource id mana costs badge under (ISS owns mana; core has no mana pair). */
    public static final String MANA_RESOURCE = "irons_spellbooks:mana";

    private SpellCellOverlays() {
    }

    /**
     * The overlay tuple for one cell's spell.
     *
     * @param cooldownRemaining ticks left on the spell's cooldown (0 = none)
     * @param cooldownTotal     the cooldown's total ticks (0 = none)
     * @param manaCost          the spell's mana cost at its inscribed level
     * @param locked            whether the cell's spell is locked in its book
     * @param mana              the player's current (synced) mana
     * @return the tuple to draw with
     */
    public static AbilityAvailability forCell(int cooldownRemaining, int cooldownTotal,
            int manaCost, boolean locked, float mana) {
        Metric<Double> cooldown;
        if (cooldownTotal > 0 && cooldownRemaining > 0) {
            double readiness = 1.0 - cooldownRemaining / (double) cooldownTotal;
            // The renderer clamps unknown shapes out ([0,1] only); a stale
            // entry must never draw a negative or over-full sweep.
            cooldown = Metric.known(Math.max(0.0, Math.min(1.0, readiness)));
        } else {
            cooldown = Metric.unknown();
        }
        Metric<ResourceCost> cost = manaCost > 0
                ? Metric.known(new ResourceCost(MANA_RESOURCE, manaCost))
                : Metric.unknown();
        AvailabilityState state;
        if (locked) {
            state = AvailabilityState.unavailable("spell locked");
        } else if (mana < manaCost) {
            state = AvailabilityState.unavailable("not enough mana");
        } else {
            state = AvailabilityState.available();
        }
        return new AbilityAvailability(cooldown, cost, state);
    }
}
