package season1.spawnstrike

import screeps.api.Position

/** Középső harci zóna – 8-szög a pálya közepe körül. */
object CenterZone {
    const val X = 50
    const val Y = 50
    const val HALF_SIDE = 7
    const val DIAGONAL_LIMIT = 11

    val center: Position = pos(X, Y)

    fun contains(position: Position): Boolean {
        val dx = kotlin.math.abs(position.x - X)
        val dy = kotlin.math.abs(position.y - Y)
        return dx <= HALF_SIDE &&
            dy <= HALF_SIDE &&
            dx + dy <= DIAGONAL_LIMIT
    }
}

fun creepInCenter(creep: Position): Boolean = CenterZone.contains(creep)
