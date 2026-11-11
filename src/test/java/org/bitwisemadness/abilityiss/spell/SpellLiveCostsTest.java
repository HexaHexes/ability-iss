package org.bitwisemadness.abilityiss.spell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.bitwisemadness.abilitycore.api.ResourceCost;
import org.junit.jupiter.api.Test;

/**
 * The tensura_iron_spells repricing (the owner's directive: ISS spells
 * quote their <b>magicule</b> cost, not mana): the pure quote math -
 * the full conversion, the sub-1 multiplier's mana remainder truncated
 * like the charge, and every off-state answering null so the caller
 * falls back to native mana (LiveCosts' fail-soft contract).
 */
class SpellLiveCostsTest {

    @Test
    void fullMultiplierQuotesMagiculeOnly() {
        // the pack's config: manaConversionMultiplier = 50.0
        List<ResourceCost> costs = SpellCatalog.magiculeCosts(30, 50.0);
        assertNotNull(costs, "a live conversion must quote");
        assertEquals(1, costs.size(), "the full conversion charges no mana");
        assertEquals("tensura:magicule", costs.get(0).resourceId().toString());
        assertEquals(1500.0, costs.get(0).amount(), 1e-9,
                "30 mana x 50 = 1500 magicule (the charge's own formula)");
    }

    @Test
    void subOneMultiplierKeepsTheManaRemainder() {
        List<ResourceCost> costs = SpellCatalog.magiculeCosts(30, 0.5);
        assertNotNull(costs);
        assertEquals(2, costs.size(),
                "a partial conversion charges magicule AND the mana remainder");
        assertEquals("tensura:magicule", costs.get(0).resourceId().toString());
        assertEquals(15.0, costs.get(0).amount(), 1e-9);
        assertEquals("irons_spellbooks:mana", costs.get(1).resourceId().toString());
        assertEquals(15.0, costs.get(1).amount(), 1e-9);
    }

    @Test
    void theRemainderTruncatesLikeTheCharge() {
        // the charge does (int)(mana - converted): (int)(7 - 3.5) = 3
        List<ResourceCost> costs = SpellCatalog.magiculeCosts(7, 0.5);
        assertNotNull(costs);
        assertEquals(2, costs.size());
        assertEquals(3.5, costs.get(0).amount(), 1e-9);
        assertEquals(3.0, costs.get(1).amount(), 1e-9,
                "the mana remainder truncates exactly as onSpellCast does");
    }

    @Test
    void conversionOffAnswersNullForNativeFallback() {
        assertNull(SpellCatalog.magiculeCosts(30, 0.0), "zero = conversion off");
        assertNull(SpellCatalog.magiculeCosts(30, -2.0), "negative = conversion off");
        assertNull(SpellCatalog.magiculeCosts(30, Double.NaN), "degenerate = never a guess");
        assertNull(SpellCatalog.magiculeCosts(30, Double.POSITIVE_INFINITY));
    }
}
