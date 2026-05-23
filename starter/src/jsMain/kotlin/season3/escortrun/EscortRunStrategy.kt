package season3.escortrun

/**
 * Makró-szintű stratégiai konstansok.
 * Combat mikro → [season3.escortrun.combat.CombatTuning].
 */
object EscortRunStrategy {

    /** Ellenséges VIP ennél közelebb a mi zászlónkhoz → ő az elsődleges cél (capture deny). */
    const val ENEMY_VIP_FLAG_DENY_RANGE: Int = 25

    /** VIP versenyfutás: ha az ellenfél ennél közelebb van a célhoz mint mi → mindig FOCUS. */
    const val VIP_RACE_CHASE_MAX_FLAG_DISTANCE: Int = 48

    /** Spawn ↔ zászló staging rally arány (saját fél). */
    const val DEFAULT_RALLY_SPAWN_TO_FLAG_T: Double = 0.42
}
