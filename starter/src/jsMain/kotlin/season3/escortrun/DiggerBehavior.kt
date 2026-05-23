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
 * **Fázis 2 – Saját flag felé:**
 *   A saját capture flag-re indul (getCaptureTarget()).
 *   Ha nincs flag → helyben marad.
 */
object DiggerBehavior {

    private const val WALL_X_MIN = 43
    private const val WALL_X_MAX = 61
    private const val WALL_Y_TOP = 9
    private const val WALL_Y_BOT = 90

    fun execute(creep: Creep, gameplay: Gameplay) {
        val wallY = if (gameplay.isTopSide) WALL_Y_TOP else WALL_Y_BOT

        // Legközelebbi törhetõ fal a sorban
        val targetWall = findNearestWall(creep, wallY)

        if (targetWall != null) {
            // Odamegyünk és bontjuk
            if (creep.getRangeTo(targetWall) <= 1) {
                creep.attack(targetWall)
            } else {
                creep.moveTo(targetWall)
            }
        } else {
            // Falak törve → saját flag felé
            val flagTarget = gameplay.getCaptureTarget() ?: return
            creep.moveTo(flagTarget)
        }
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