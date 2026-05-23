package season3.escortrun

import screeps.api.Creep
import season3.escortrun.combat.CombatBehavior
import season3.escortrun.combat.DiggerBehavior
import season3.escortrun.economy.CarryBehavior
import season3.escortrun.economy.HarvesterBehavior
import season3.escortrun.economy.WorkerBehavior

/**
 * Creep végrehajtás belépési pontja.
 */
fun Creep.execute(gameplay: Gameplay) {
    val boostEnabled = !SpawnQueue.isPanicMode(gameplay)

    when (role) {
        Role.WORKER              -> WorkerBehavior.execute(this, gameplay, boostEnabled)
        Role.HARVESTER           -> HarvesterBehavior.execute(this, gameplay, boostEnabled)
        Role.CARRY               -> CarryBehavior.execute(this, gameplay)
        Role.COMBAT_HYBRID,
        Role.COMBAT_RANGER,
        Role.COMBAT_LIGHT_RANGER,
        Role.COMBAT_CHEAP_RANGER,
        Role.COMBAT_SELF_HEAL_RANGER -> CombatBehavior.execute(this, gameplay)
        Role.COMBAT_DIGGER       -> DiggerBehavior.execute(this, gameplay)
        Role.COMBAT_FLAG_BLOCKER -> { /* TODO */ }
    }
}