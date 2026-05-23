package season1.spawnstrike

object SpawnStrikeStrategy {

    /** Tick után a combat raiderök középről az enemy spawn felé indulnak (előtte entry point). */
    const val RAIDER_COMBAT_ASSAULT_START_TICK: Int = 120

    /** Ennyi tick után (vagy ha az assault áttörte a falat) push mód. */
    const val ASSAULT_START_TICK: Int = 620

    /** Ennyi range-en belül lévő ellenség triggereli a defend módot a checkpointoknál. */
    const val DEFEND_CHECKPOINT_RANGE: Int = 10
}