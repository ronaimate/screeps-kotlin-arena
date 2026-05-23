package season1.spawnstrike

import screeps.api.Creep
import screeps.api.Position

/** A flag runner (RAIDER) útvonala – a combat raiderök ezt másolják 5 range-ig. */
object RaiderPath {

    private val waypoints = mutableListOf<Position>()
    private const val MAX_WAYPOINTS = 500

    fun recordLeaderStep(creep: Creep) {
        val p = pos(creep.x, creep.y)
        if (waypoints.isNotEmpty()) {
            val last = waypoints.last()
            if (last.x == p.x && last.y == p.y) return
        }
        waypoints.add(p)
        if (waypoints.size > MAX_WAYPOINTS) {
            waypoints.removeAt(0)
        }
    }

    /** Következő lépés a leader által járt úton (nem shortcut pathfinder). */
    fun nextStepFor(follower: Creep): Position? {
        if (waypoints.isEmpty()) return null

        var bestIdx = 0
        var bestDist = follower.getRangeTo(waypoints[0])
        for (i in 1 until waypoints.size) {
            val d = follower.getRangeTo(waypoints[i])
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }

        val targetIdx = minOf(bestIdx + 1, waypoints.lastIndex)
        return waypoints[targetIdx]
    }

    fun clear() {
        waypoints.clear()
    }
}
