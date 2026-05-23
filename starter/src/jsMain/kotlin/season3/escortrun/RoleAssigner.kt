package season3.escortrun

import screeps.api.*
import season3.escortrun.isCarryOnly

/**
 * Minden ticken a még nem szereppel rendelkező creepeket besorolja
 * a testfelépítésük alapján.
 *
 * Sorrendben:
 *   1. ATTACK (move nélkül, csak 1 move) → COMBAT_DIGGER
 *   2. RANGED_ATTACK + HEAL → COMBAT_HYBRID
 *   3. RANGED_ATTACK (heal nélkül) → COMBAT_RANGER
 *   4. WORK → WORKER
 *   5. MOVE + CARRY (WORK nélkül) → HARVESTER
 */
object RoleAssigner {

    fun assign(gameplay: Gameplay) {
        val unassigned = gameplay.myCreeps
            .filter { !it.hasRole() }
            .sortedBy { it.id }

        for (creep in unassigned) {
            creep.role = detect(creep, gameplay)
        }
    }

    private fun detect(creep: Creep, gameplay: Gameplay): Role {
        val hasAttack = creep.body.any { it.type == ATTACK }
        val hasRanged = creep.canRangedAttack()
        val hasHeal   = creep.canHeal()
        val hasWork   = creep.body.any { it.type == WORK }

        val hasTough  = creep.body.any { it.type == TOUGH }
        val moveCount = creep.body.count { it.type == MOVE }
        val raCount   = creep.body.count { it.type == RANGED_ATTACK }

        return when {
            hasAttack                                    -> Role.COMBAT_DIGGER
            hasRanged && hasHeal && !hasTough            -> Role.COMBAT_SELF_HEAL_RANGER
            hasRanged && hasHeal                         -> Role.COMBAT_HYBRID
            hasHeal                                      -> Role.COMBAT_HYBRID
            hasRanged && !hasTough && moveCount == 1 && raCount == 1 -> Role.COMBAT_CHEAP_RANGER
            hasRanged && !hasTough && moveCount <= 2     -> Role.COMBAT_LIGHT_RANGER
            hasRanged                                    -> Role.COMBAT_RANGER
            hasWork                                      -> Role.WORKER
            creep.isCarryOnly()                          -> Role.CARRY
            else                                         -> Role.HARVESTER
        }
    }
}