package com.mikael8367.nexustech.crusher;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Isolated decision-model tests. These do not simulate Minecraft or Forge. */
public final class CrusherXpExperimentTest {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static Map<CrusherXpExperiment.Fact, CrusherXpExperiment.Evidence> proven() {
        Map<CrusherXpExperiment.Fact, CrusherXpExperiment.Evidence> facts =
            new EnumMap<>(CrusherXpExperiment.Fact.class);
        for (CrusherXpExperiment.Fact f : CrusherXpExperiment.Fact.values())
            facts.put(f, CrusherXpExperiment.Evidence.PROVEN);
        return facts;
    }
    private static void rejected(String scenario, CrusherXpExperiment.Fact... absent) {
        Map<CrusherXpExperiment.Fact, CrusherXpExperiment.Evidence> facts = proven();
        for (CrusherXpExperiment.Fact f : absent) facts.put(f, CrusherXpExperiment.Evidence.DISPROVEN);
        Set<CrusherXpExperiment.Fact> blocked = CrusherXpExperiment.blockers(facts);
        for (CrusherXpExperiment.Fact f : absent) check(blocked.contains(f), scenario + ": " + f);
    }
    public static void main(String[] args) {
        CrusherXpExperiment.Fact[] all = CrusherXpExperiment.Fact.values();
        check(!CrusherXpExperiment.IMPLEMENTED, "must remain disabled");
        check(CrusherXpExperiment.blockers(null).size() == all.length, "null input closed");
        Map<CrusherXpExperiment.Fact, CrusherXpExperiment.Evidence> facts = proven();
        check(CrusherXpExperiment.blockers(facts).isEmpty(), "all hypothetical facts recorded");
        for (CrusherXpExperiment.Fact fact : all) {
            facts.put(fact, CrusherXpExperiment.Evidence.UNKNOWN);
            check(CrusherXpExperiment.blockers(facts).contains(fact), "unknown " + fact);
            facts.put(fact, CrusherXpExperiment.Evidence.DISPROVEN);
            check(CrusherXpExperiment.blockers(facts).contains(fact), "disproven " + fact);
            facts.put(fact, null);
            check(CrusherXpExperiment.blockers(facts).contains(fact), "null " + fact);
            facts.remove(fact);
            check(CrusherXpExperiment.blockers(facts).contains(fact), "missing " + fact);
            facts.put(fact, CrusherXpExperiment.Evidence.PROVEN);
        }
        rejected("damage cancelled, death cancelled or revived",
            CrusherXpExperiment.Fact.DEATH_FINAL_NOT_CANCELLED_OR_REVERSED);
        rejected("wrong or concurrent final damage source",
            CrusherXpExperiment.Fact.CRUSHER_IS_CONFIRMED_DEATH_CAUSE);
        rejected("player hit already or later; vanilla XP can still happen",
            CrusherXpExperiment.Fact.NATIVE_OR_CUSTOM_XP_EXCLUDED_FOR_ENTIRE_DEATH);
        rejected("doMobLoot false", CrusherXpExperiment.Fact.MOB_LOOT_ALLOWED_AT_XP_TIME);
        rejected("baby or no XP", CrusherXpExperiment.Fact.MOB_CAN_DROP_XP_AT_XP_TIME,
            CrusherXpExperiment.Fact.EVENT_ADJUSTED_XP_POSITIVE);
        rejected("XP event cancelled", CrusherXpExperiment.Fact.FORGE_XP_EVENT_NOT_CANCELLED);
        rejected("XP event skipped or emitted twice", CrusherXpExperiment.Fact.FORGE_XP_EVENT_PROCESSED_EXACTLY_ONCE);
        rejected("event reduced XP to zero", CrusherXpExperiment.Fact.EVENT_ADJUSTED_XP_POSITIVE);
        rejected("dragon or unknown custom override", CrusherXpExperiment.Fact.SUPPORTED_DEATH_AND_XP_PATH);
        rejected("mob-specific XP calculation uncertain", CrusherXpExperiment.Fact.MOB_XP_COMPUTED_ONCE_WITH_CORRECT_CONTEXT);
        rejected("fake player affects loot", CrusherXpExperiment.Fact.NO_ATTACKER_OR_NORMAL_DROPS_CHANGED);
        rejected("client or player target", CrusherXpExperiment.Fact.SERVER_SIDE, CrusherXpExperiment.Fact.NON_PLAYER_TARGET);
        rejected("reentrant call or save/reload", CrusherXpExperiment.Fact.NO_REENTRY_RELOAD_OR_OTHER_DUPLICATE);
        Set<CrusherXpExperiment.Fact> snapshot = CrusherXpExperiment.blockers(facts);
        facts.remove(all[0]);
        check(snapshot.isEmpty(), "returned set is an independent snapshot");
        boolean immutable = false;
        try { snapshot.add(all[0]); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "snapshot immutable");
        // Exercise every combination of missing/proven facts, not just examples.
        int combinations = 1 << all.length;
        for (int mask = 0; mask < combinations; mask++) {
            Map<CrusherXpExperiment.Fact, CrusherXpExperiment.Evidence> input = new EnumMap<>(CrusherXpExperiment.Fact.class);
            for (int i = 0; i < all.length; i++) if ((mask & (1 << i)) != 0) input.put(all[i], CrusherXpExperiment.Evidence.PROVEN);
            Set<CrusherXpExperiment.Fact> result = CrusherXpExperiment.blockers(input);
            check(result.size() == all.length - Integer.bitCount(mask), "combination " + mask);
            for (int i = 0; i < all.length; i++)
                check(result.contains(all[i]) == ((mask & (1 << i)) == 0), "membership " + mask + "/" + i);
        }
        System.out.println("PASS isolated model: " + assertions + " assertions, " + combinations + " combinations; XP remains unimplemented");
    }
}
