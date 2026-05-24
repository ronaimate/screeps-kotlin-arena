package season3.escortrun.combat

import screeps.api.*
import screeps.api.structures.StructureWall
import season3.escortrun.Gameplay
import season3.escortrun.pos

/**
 * Digger (COMBAT_DIGGER) viselkedés.
 *
 * **Fázis 1 – Falak bontása:**
 *   Top: x=43..61, y=9
 *   Bot: x=43..61, y=90
 *   A Digger megkeresi a legközelebbi Constructed Wall-t a saját fal-sorában,
 *   odamegy és megtámadja. Ha már nincs fal → Fázis 2.
 *
 * **Fázis 2 – Guard pozíció + védelem:**
 *   Guard pos: top=(94,4), bot=(94,95) – a saját flag mellől véd.
 *   Prioritás:
 *     1. EscortCreep 20 yardján belüli ellenség → öld meg
 *     2. Saját flag 20 yardján belüli ellenség → öld meg
 *     3. Nincs fenyegetés → menj/maradj a guard pozícióban
 */
object DiggerBehavior {

    private const val WALL_X_MIN = 43
    private const val WALL_X_MAX = 61
    private const val WALL_Y_TOP = 9
    private const val WALL_Y_BOT = 90

    private const val GUARD_THREAT_RANGE = 20

    private val GUARD_POS_TOP = pos(94, 4)
    private val GUARD_POS_BOT = pos(94, 95)

    fun execute(creep: Creep, gameplay: Gameplay) {
        val wallY = if (gameplay.isTopSide) WALL_Y_TOP else WALL_Y_BOT

        // Fázis 1: fal bontás
        val targetWall = findNearestWall(creep, wallY)
        if (targetWall != null) {
            if (creep.getRangeTo(targetWall) <= 1) creep.attack(targetWall)
            else creep.moveTo(targetWall)
            return
        }

        // Fázis 2: guard + védelem
        val guardPos = if (gameplay.isTopSide) GUARD_POS_TOP else GUARD_POS_BOT

        // 1. prioritás: escort közelében lévő ellenség
        val escortThreat = findEscortThreat(gameplay)
        if (escortThreat != null) {
            if (creep.getRangeTo(escortThreat) <= 1) creep.attack(escortThreat)
            else creep.moveTo(escortThreat)
            return
        }

        // 2. prioritás: flag közelében lévő ellenség
        val flagThreat = findFlagThreat(gameplay)
        if (flagThreat != null) {
            if (creep.getRangeTo(flagThreat) <= 1) creep.attack(flagThreat)
            else creep.moveTo(flagThreat)
            return
        }

        // 3. nincs fenyegetés → guard pozícióba
        if (creep.x != guardPos.x || creep.y != guardPos.y) {
            creep.moveTo(guardPos)
        }
    }

    /** Escort creeptől GUARD_THREAT_RANGE-en belüli legközelebbi ellenség. */
    private fun findEscortThreat(gameplay: Gameplay): Creep? {
        val escort = gameplay.myEscortCreep ?: return null
        return gameplay.getHostileCreeps()
            .filter { it.getRangeTo(escort) <= GUARD_THREAT_RANGE }
            .minByOrNull { it.getRangeTo(escort) }
    }

    /** Saját flag-től GUARD_THREAT_RANGE-en belüli legközelebbi ellenség. */
    private fun findFlagThreat(gameplay: Gameplay): Creep? {
        val flag = gameplay.getMyCaptureFlag() ?: return null
        return gameplay.getHostileCreeps()
            .filter { it.getRangeTo(flag) <= GUARD_THREAT_RANGE }
            .minByOrNull { it.getRangeTo(flag) }
    }

    /**
     * Megkeresi a legközelebbi Constructed Wall-t a megadott y-sorban (x=43..61).
     */
    private fun findNearestWall(creep: Creep, wallY: Int): StructureWall? {
        val walls = getObjectsByPrototype(StructureWall::class.js).toList()
            .filter { it.exists && it.x in WALL_X_MIN..WALL_X_MAX && it.y == wallY }

        if (walls.isEmpty()) return null
        return walls.minByOrNull { creep.getRangeTo(it) }
    }
}