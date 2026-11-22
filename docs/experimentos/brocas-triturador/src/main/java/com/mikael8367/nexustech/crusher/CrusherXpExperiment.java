package com.mikael8367.nexustech.crusher;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Quarantined, pure evidence model. This is NOT a configuration switch or an
 * implementation of XP; it is not wired to the game or any Forge event.
 * Even an empty blocker set does not authorize spawning XP. Evidence must not
 * be inferred from an attack result or from a still-running event callback.
 */
final class CrusherXpExperiment {
    static final boolean IMPLEMENTED = false;

    enum Fact {
        SERVER_SIDE,
        NON_PLAYER_TARGET,
        CRUSHER_IS_CONFIRMED_DEATH_CAUSE,
        DEATH_FINAL_NOT_CANCELLED_OR_REVERSED,
        SUPPORTED_DEATH_AND_XP_PATH,
        MOB_LOOT_ALLOWED_AT_XP_TIME,
        MOB_CAN_DROP_XP_AT_XP_TIME,
        NATIVE_OR_CUSTOM_XP_EXCLUDED_FOR_ENTIRE_DEATH,
        MOB_XP_COMPUTED_ONCE_WITH_CORRECT_CONTEXT,
        FORGE_XP_EVENT_PROCESSED_EXACTLY_ONCE,
        FORGE_XP_EVENT_NOT_CANCELLED,
        EVENT_ADJUSTED_XP_POSITIVE,
        NO_ATTACKER_OR_NORMAL_DROPS_CHANGED,
        NO_REENTRY_RELOAD_OR_OTHER_DUPLICATE
    }

    enum Evidence { PROVEN, DISPROVEN, UNKNOWN }

    /** Missing/null facts are unknown; results are snapshots, never a permit. */
    static Set<Fact> blockers(Map<Fact, Evidence> evidence) {
        EnumSet<Fact> blocked = EnumSet.noneOf(Fact.class);
        for (Fact fact : Fact.values()) {
            if (evidence == null || evidence.get(fact) != Evidence.PROVEN) {
                blocked.add(fact);
            }
        }
        return Collections.unmodifiableSet(blocked);
    }

    private CrusherXpExperiment() {}
}
