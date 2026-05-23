package season3.escortrun.combat

/**
 * Harci mikro-paraméterek.
 * Makró (spawn sorrend, VIP deny távolság) → [season3.escortrun.EscortRunStrategy].
 */
object CombatTuning {

    const val RANGED_ATTACK_RANGE: Int   = 3
    const val IMMEDIATE_THREAT_RANGE: Int = 8

    /** Harcos e közelségen belül van a saját VIP-hez → escort guard prioritás aktív. */
    const val ESCORT_GUARD_FIGHTER_MAX_RANGE: Int = 22

    /** FlagBlocker: ennél közelebb van a VIP-hez → VIP fókusz mód. */
    const val FLAG_BLOCKER_VIP_FOCUS_MAX_RANGE: Int = 10

    /** WAIT/CAPTURE közbeni aktív „becsukódás" – ha ellenség ennél közelebb kerül. */
    const val HOLD_AND_RALLY_ENGAGE_RANGE: Int = 8

    // --- Globális harc detektálás ---
    const val COMBAT_AGGRO_RANGE: Int           = 26
    const val ENEMY_PRESSURE_ON_SPAWN_RANGE: Int = 16
    const val ENEMY_NEAR_RALLY_RANGE: Int        = 15
    const val ENEMY_CAMP_MAP_MID_RANGE: Int      = 14
    const val ENEMY_DEEP_ECON_RAIDER_RANGE: Int  = 40

    const val ENEMY_NEAR_HOSTILE_BUILD_RANGE: Int = 16
    const val ENEMY_NEAR_EXTRA_SPAWN_RANGE: Int   = 20

    const val RALLY_TOWARD_THREAT_T: Double = 0.52

    // --- Heal ---
    const val SELF_HEAL_HP_RATIO: Double              = 0.85
    const val ALLY_HEAL_START_RATIO: Double           = 0.998
    const val ALLY_HEAL_HP_RATIO: Double              = ALLY_HEAL_START_RATIO
    const val SELF_HEAL_DEFER_TO_ALLY_ABOVE_RATIO: Double = 0.92

    // --- Összetartás ---
    const val COMBAT_COHESION_MAX_SPREAD: Int         = 11
    const val COHESION_RALLY_BLEND_WEIGHT: Double     = 0.55
    const val COHESION_SUSPEND_HOSTILE_RANGE: Int     = 12
}
