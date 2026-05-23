package season3.escortrun.combat

import screeps.api.*
import season3.escortrun.Gameplay
import season3.escortrun.Role
import season3.escortrun.canHeal
import season3.escortrun.canRangedAttack
import season3.escortrun.pos
import season3.escortrun.role
import kotlin.math.sqrt

/**
 * Harci creepek (COMBAT_RANGER, COMBAT_HYBRID) viselkedése.
 *
 * Kite logika (Ranger):
 *   - Ellenség < OPTIMAL_RANGE: hátrálj a pálya közepe felé + lőj
 *   - Ellenség == OPTIMAL_RANGE: lőj, ne mozogj
 *   - Ellenség > OPTIMAL_RANGE de <= ENGAGE_RANGE: közeledj + lőj
 *   - Ellenség > ENGAGE_RANGE: rally/kohézió
 *
 * Focus fire: legkevesebb HP-s ellenség.
 * Csatlakozás: ha bárki harcol → a többiek a legközelebbi harcoló
 *   ellenség felé tartanak (nem a harcoló creep pozíciójára).
 */
object CombatBehavior {

    private const val KITE_DANGER_RANGE = 5
    private const val OPTIMAL_RANGE     = 3
    private const val ENGAGE_RANGE      = 10
    private const val RALLY_REACHED_RANGE = 4
    private const val COHESION_MAX_SPREAD = 8

    fun execute(creep: Creep, gameplay: Gameplay) {
        val rally = if (gameplay.isTopSide) pos(51, 51) else pos(51, 48)

        // Hybrid: gyógyít ÉS lő + csapat közelében marad
        if (creep.canHeal()) {
            healAllyIfNeeded(creep, gameplay)
            // Hybrid is lő ha van célpont range-en belül
            val hybridTarget = getFocusTarget(creep, gameplay)
            if (hybridTarget != null) {
                val dist = creep.getRangeTo(hybridTarget)
                shootBest(creep, gameplay)
                when {
                    dist < OPTIMAL_RANGE -> kiteFromHostiles(creep, gameplay.getHostileCreeps(), gameplay)
                    dist == OPTIMAL_RANGE -> { /* ideális táv, csak lőj */ }
                    else -> creep.moveTo(hybridTarget)
                }
            } else {
                val activeTarget = getTeamEngageTarget(gameplay)
                if (activeTarget != null && creep.getRangeTo(activeTarget) > OPTIMAL_RANGE) {
                    creep.moveTo(activeTarget)
                } else if (creep.getRangeTo(rally) > RALLY_REACHED_RANGE) {
                    creep.moveTo(rally)
                }
            }
            return
        }

        // Saját célpont
        val target = getFocusTarget(creep, gameplay)

        if (target != null) {
            val dist = creep.getRangeTo(target)
            shootBest(creep, gameplay)

            when {
                dist <= KITE_DANGER_RANGE -> {
                    // Kite: fusson a spawn felé
                    kiteFromHostiles(creep, gameplay.getHostileCreeps(), gameplay)
                }
                else -> {
                    // Távolabb van → közeledj és lőj
                    creep.moveTo(target)
                }
            }
            return
        }

        // Nincs saját target – ha más harcol, menj a csata felé
        val activeTarget = getTeamEngageTarget(gameplay)
        if (activeTarget != null) {
            creep.moveTo(activeTarget)
            return
        }

        // Senki nem harcol – rally
        if (creep.getRangeTo(rally) > RALLY_REACHED_RANGE) {
            creep.moveTo(rally)
        } else {
            val centroid = getTeamCentroid(gameplay)
            if (centroid != null && creep.getRangeTo(centroid) > COHESION_MAX_SPREAD) {
                creep.moveTo(centroid)
            }
        }
    }

    // ── Kite flee pozíció ─────────────────────────────────────────────────────

    /**
     * Kite: az ellenség mögé mutat 15 mezőre, a moveTo pathfinder megkerüli a falakat.
     * KITE_DANGER_RANGE-en belül mindig fut, azon kívül áll és lő.
     */
    private fun kiteFromHostiles(creep: Creep, hostiles: List<Creep>, gameplay: Gameplay) {
        val nearby = hostiles.filter { creep.getRangeTo(it) <= KITE_DANGER_RANGE }
        if (nearby.isEmpty()) return
        // Átlagos ellenség pozíció
        val avgX = nearby.sumOf { it.x } / nearby.size
        val avgY = nearby.sumOf { it.y } / nearby.size
        // Flee irány: el az ellenségtől, 15 mezőre
        val dx = creep.x - avgX
        val dy = creep.y - avgY
        val len = sqrt((dx * dx + dy * dy).toDouble()).coerceAtLeast(1.0)
        val fleeX = (creep.x + (dx / len * 15).toInt()).coerceIn(2, 97)
        val fleeY = (creep.y + (dy / len * 15).toInt()).coerceIn(2, 97)
        // moveTo pathfinder megkerüli a falakat
        creep.moveTo(pos(fleeX, fleeY))
    }

    // ── Csapat aktív célpontja ────────────────────────────────────────────────

    /**
     * Ha bármelyik saját harcos harcol, visszaadja a legközelebbi ellenséges
     * creep pozícióját (nem a harcoló creep pozícióját – az mozog).
     * Így a csatlakozó creepek egy stabil célra tartanak.
     */
    private fun getTeamEngageTarget(gameplay: Gameplay): screeps.api.Position? {
        val fighters = gameplay.myCreeps.filter {
            it.role == Role.COMBAT_RANGER || it.role == Role.COMBAT_LIGHT_RANGER || it.role == Role.COMBAT_CHEAP_RANGER || it.role == Role.COMBAT_SELF_HEAL_RANGER || it.role == Role.COMBAT_HYBRID
        }
        val enemies = gameplay.getHostileCreeps()
        for (fighter in fighters) {
            val nearEnemy = enemies.filter { fighter.getRangeTo(it) <= ENGAGE_RANGE }
                .minByOrNull { fighter.getRangeTo(it) }
            if (nearEnemy != null) return pos(nearEnemy.x, nearEnemy.y)
        }
        return null
    }

    // ── Focus fire ────────────────────────────────────────────────────────────

    /**
     * Lövés: ha 3+ ellenség van OPTIMAL_RANGE-en belül → rangedMassAttack (több DPS).
     * Egyébként focus fire a legkevesebb HP-s célra.
     */
    private fun shootBest(creep: Creep, gameplay: Gameplay) {
        if (!creep.canRangedAttack()) return
        // Ha 2+ saját creep is közel van az ellenséghez → mass attack több DPS-t ad
        val target = getFocusTarget(creep, gameplay) ?: return
        val alliesNearTarget = gameplay.myCreeps.count { ally ->
            ally.id != creep.id && ally.getRangeTo(target) <= OPTIMAL_RANGE
        }
        if (alliesNearTarget >= 2) {
            creep.rangedMassAttack()
        } else {
            creep.rangedAttack(target)
        }
    }

    private fun getFocusTarget(creep: Creep, gameplay: Gameplay): Creep? =
        gameplay.getHostileCreeps()
            .filter { creep.getRangeTo(it) <= ENGAGE_RANGE }
            .minWithOrNull(compareBy({ it.hits }, { creep.getRangeTo(it) }))

    // ── Heal ──────────────────────────────────────────────────────────────────

    private fun healAllyIfNeeded(creep: Creep, gameplay: Gameplay) {
        val worstAlly = gameplay.myCreeps
            .filter { it.id != creep.id && it.hits < it.hitsMax * CombatTuning.ALLY_HEAL_HP_RATIO }
            .minByOrNull { it.hits }
            ?: return

        if (creep.getRangeTo(worstAlly) <= 1) {
            creep.heal(worstAlly)
        } else {
            creep.rangedHeal(worstAlly)
        }
    }

    // ── Csapat centroid ───────────────────────────────────────────────────────

    private fun getTeamCentroid(gameplay: Gameplay): screeps.api.Position? {
        val fighters = gameplay.myCreeps.filter {
            it.role == Role.COMBAT_RANGER || it.role == Role.COMBAT_LIGHT_RANGER || it.role == Role.COMBAT_CHEAP_RANGER || it.role == Role.COMBAT_SELF_HEAL_RANGER || it.role == Role.COMBAT_HYBRID
        }
        if (fighters.isEmpty()) return null
        val avgX = fighters.map { it.x }.average().toInt()
        val avgY = fighters.map { it.y }.average().toInt()
        return pos(avgX, avgY)
    }
}