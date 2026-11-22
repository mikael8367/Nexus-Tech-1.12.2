package com.mikael8367.nexustech.crusher;

/**
 * Quarantined design marker, not an XP implementation or a configuration switch.
 * The 1.12.2 Forge hooks do not safely enable the normal mob XP path for this
 * non-player damage source. Do not wire this marker into damage or death events.
 * See docs/crusher-xp-experiment.md before attempting an implementation.
 */
final class CrusherXpExperiment {
    static final boolean IMPLEMENTED = false;

    private CrusherXpExperiment() {}
}
