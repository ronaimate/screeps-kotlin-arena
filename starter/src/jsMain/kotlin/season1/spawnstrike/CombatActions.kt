package season1.spawnstrike

import screeps.api.*
import screeps.api.structures.StructureSpawn
import season1.spawnstrike.CombatTuning.RANGED_ATTACK_RANGE

/** Közös harci akciók – előbb lövés, aztán mozgás. */
object CombatActions {

    /** @return true ha ranged akció lefutott (creep vagy struktúra). */
    fun shootHostiles(creep: Creep, hostiles: List<Creep>): Boolean {
        if (!creep.canRangedAttack()) return false
        val inRange = hostiles.filter { creep.getRangeTo(it) <= RANGED_ATTACK_RANGE }
        if (inRange.isEmpty()) return false
        if (inRange.size >= 2) creep.rangedMassAttack()
        else creep.rangedAttack(inRange.minByOrNull { it.hits }!!)
        return true
    }

    fun shootHostilesOrSpawn(creep: Creep, hostiles: List<Creep>, spawn: StructureSpawn?): Boolean {
        if (shootHostiles(creep, hostiles)) return true
        if (spawn != null && creep.canRangedAttack() && creep.getRangeTo(spawn) <= RANGED_ATTACK_RANGE) {
            creep.rangedAttack(spawn)
            return true
        }
        return false
    }

    fun hasMeleeThreat(creep: Creep, hostiles: List<Creep>): Boolean =
        hostiles.any { creep.getRangeTo(it) <= 1 }

    fun kiteFromHostiles(creep: Creep, hostiles: List<Creep>) {
        val nearby = hostiles.filter { creep.getRangeTo(it) <= RANGED_ATTACK_RANGE + 1 }
        if (nearby.isEmpty()) return
        val avgX = nearby.sumOf { it.x } / nearby.size
        val avgY = nearby.sumOf { it.y } / nearby.size
        val dx = creep.x - avgX
        val dy = creep.y - avgY
        val moveX = if (dx == 0 && dy == 0) 1 else dx.coerceIn(-1, 1)
        val moveY = if (dx == 0 && dy == 0) 0 else dy.coerceIn(-1, 1)
        creep.moveTo(pos(
            (creep.x + moveX).coerceIn(1, 98),
            (creep.y + moveY).coerceIn(1, 98),
        ))
    }

    fun moveToward(creep: Creep, goal: Position, stopRange: Int = 1) {
        if (creep.getRangeTo(goal) > stopRange) creep.moveTo(goal)
    }

    fun nearestHostile(creep: Creep, hostiles: List<Creep>): Creep? =
        hostiles.minByOrNull { creep.getRangeTo(it) }
}
