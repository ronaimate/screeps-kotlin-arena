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
    private const val ENGAGE_RANGE      = 20
    private const val RALLY_REACHED_RANGE = 4
    private const val COHESION_MAX_SPREAD = 8

    fun execute(creep: Creep, gameplay: Gameplay) {
        val rally = if (gameplay.isTopSide) pos(51, 51) else pos(51, 48)

        // Shared team target: ha bármelyik csapattag ENGAGE_RANGE-én belül van ellenség,
        // mindenki ugyanazt a targetet kapja → focus fire + együtt rohanja le
        val sharedTarget = getSharedTeamTarget(gameplay)

        // Hybrid: gyógyít ÉS lő + csapat közelében marad
        if (creep.canHeal()) {
            healAllyIfNeeded(creep, gameplay)
            if (sharedTarget != null) {
                val dist = creep.getRangeTo(sharedTarget)
                shootBestAt(creep, sharedTarget, gameplay)
                when {
                    dist < OPTIMAL_RANGE -> kiteFromHostiles(creep, gameplay.getHostileCreeps(), gameplay)
                    dist == OPTIMAL_RANGE -> { /* ideális táv, csak lőj */ }
                    else -> creep.moveTo(sharedTarget)
                }
            } else if (creep.getRangeTo(rally) > RALLY_REACHED_RANGE) {
                creep.moveTo(rally)
            }
            return
        }

        // Shared target van → mindenki rámenegy és lő
        if (sharedTarget != null) {
            val dist = creep.getRangeTo(sharedTarget)
            shootBestAt(creep, sharedTarget, gameplay)
            when {
                dist <= KITE_DANGER_RANGE -> kiteFromHostiles(creep, gameplay.getHostileCreeps(), gameplay)
                else -> creep.moveTo(sharedTarget)
            }
            return
        }

        // Senki nem lát ellenséget – rally
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

    // ── Focus fire ────────────────────────────────────────────────────────────

    /**
     * Lövés az explicit target-re – mindig rangedAttack, sosem mass attack.
     * Mass attack csak akkor érne többet ha 3+ egymás melletti ellenség van,
     * de egyetlen cél ellen töredéke a sima lövés DPS-ének.
     */
    private fun shootBestAt(creep: Creep, target: Creep, gameplay: Gameplay) {
        if (!creep.canRangedAttack()) return
        creep.rangedAttack(target)
    }

    /**
     * Shared team target: ha bármelyik saját harcos ENGAGE_RANGE-én belül lát ellenséget,
     * visszaadja a legkevesebb HP-s (döntetlennél legközelebb lévő) ellenséget.
     * Így az összes creep ugyanarra a targetre fókuszál.
     */
    private fun getSharedTeamTarget(gameplay: Gameplay): Creep? {
        val fighters = gameplay.myCreeps.filter {
            it.role == Role.COMBAT_RANGER || it.role == Role.COMBAT_LIGHT_RANGER ||
                    it.role == Role.COMBAT_CHEAP_RANGER || it.role == Role.COMBAT_SELF_HEAL_RANGER ||
                    it.role == Role.COMBAT_HYBRID
        }
        val enemies = gameplay.getHostileCreeps()
        // Összegyűjti az összes ellenséget akit bármelyik harcos lát
        val visible = enemies.filter { enemy ->
            fighters.any { fighter -> fighter.getRangeTo(enemy) <= ENGAGE_RANGE }
        }
        if (visible.isEmpty()) return null
        // Legkevesebb HP, döntetlennél a csapat centroidjától legközelebb
        val avgX = fighters.map { it.x }.average()
        val avgY = fighters.map { it.y }.average()
        return visible.minWithOrNull(compareBy({ it.hits }, {
            val dx = it.x - avgX; val dy = it.y - avgY; dx * dx + dy * dy
        }))
    }

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