package season1.spawnstrike

import screeps.api.*
import screeps.api.structures.*

class Gameplay(
    var mySpawn: StructureSpawn = GameplayUtil.getMySpawn(),
    var myCreeps: List<Creep> = GameplayUtil.getMyCreeps(),
) {
    // Saját oldali "critical" falcellák (10-10), amit ha az ellenfél legalább 2 helyen áttör
    // és rá is áll valamelyikre, az assault spawn defense triggerelhető.
    private val TOP_OWN_WALL_ALERT = listOf(
        pos(2, 40), pos(3, 41), pos(4, 42), pos(5, 43), pos(6, 44),
        pos(6, 43), pos(5, 42), pos(4, 41), pos(3, 40), pos(2, 39),
    )
    private val BOTTOM_OWN_WALL_ALERT = listOf(
        pos(93, 55), pos(94, 56), pos(95, 57), pos(96, 58), pos(97, 59),
        pos(93, 56), pos(94, 57), pos(95, 58), pos(96, 59), pos(96, 60),
    )

    // Opening queue: RAIDER, RAIDER_COMBAT x2, ASSAULT x2, SCOUT
    private val openingSpawnQueue: MutableList<Role> = mutableListOf(
        Role.RAIDER,
        Role.RAIDER_COMBAT,
        Role.RAIDER_COMBAT,
        Role.ASSAULT,
        Role.ASSAULT,
        Role.SCOUT,
    )

    fun analyze() {
        mySpawn = GameplayUtil.getMySpawn()
        myCreeps = GameplayUtil.getMyCreeps()
    }

    fun spawnCreep() {
        mySpawn.takeIf { it.spawning == null }?.let { spawn ->
            val role = nextSpawnRole() ?: return@let
            val body = bodyForRole(role)
            val creep = spawn.spawnCreep(body.toTypedArray()).`object` ?: return@let
            creep.role = role
            if (openingSpawnQueue.isNotEmpty()) openingSpawnQueue.removeFirst()
        }
    }

    private fun bodyForRole(role: Role): List<BodyPartType> = when (role) {
        Role.RAIDER        -> listOf(MOVE)
        Role.RAIDER_COMBAT -> listOf(MOVE, RANGED_ATTACK)
        Role.ASSAULT       -> List(16) { MOVE } + List(20) { RANGED_ATTACK } + listOf(HEAL)
        Role.SCOUT         -> listOf(TOUGH, MOVE)  // TOUGH megkülönbözteti a RAIDER-től
    }

    private fun nextSpawnRole(): Role? {
        return openingSpawnQueue.firstOrNull()
    }

    fun assignStaticRoles() {
        val unassigned = myCreeps.filter { !it.hasRole() }.sortedBy { it.id }
        for (creep in unassigned) {
            creep.role = when {
                creep.canRangedAttack() && creep.canHeal()        -> Role.ASSAULT
                creep.canRangedAttack()                            -> Role.RAIDER_COMBAT
                creep.body.any { it.type == TOUGH }               -> Role.SCOUT
                else                                               -> Role.RAIDER
            }
        }
    }

    // --- Defend mód ---

    /**
     * Defend mód ha bármely ellenség ≤10 range-re van:
     * - a saját spawntól, VAGY
     * - a bottom/top checkpointok bármelyikétől
     */
    fun isDefendMode(): Boolean {
        if (isEnemyBreakingOurWall()) return true

        val hostiles = getHostileCreeps()
        if (hostiles.isEmpty()) return false

        val checkpoints = defendCheckpoints()
        return hostiles.any { h ->
            checkpoints.any { cp -> h.getRangeTo(cp) <= SpawnStrikeStrategy.DEFEND_CHECKPOINT_RANGE }
        }
    }

    private fun defendCheckpoints(): List<Position> {
        return if (isInTop()) listOf(
            mySpawnPos(),
            pos(78, 66),
            pos(95, 47),
            pos(91, 32),
            pos(83, 12),
            pos(70, 2),
        ) else listOf(
            mySpawnPos(),
            pos(21, 33),
            pos(4, 52),
            pos(8, 67),
            pos(16, 87),
            pos(29, 97),
        )
    }

    private fun mySpawnPos(): Position = pos(mySpawn.x, mySpawn.y)

    /** Checkpoint / spawn közeli klasszikus defend trigger (fal-törés nélkül). */
    fun isCheckpointDefendMode(): Boolean {
        return getCheckpointDefenseThreats().isNotEmpty()
    }

    fun getCheckpointDefenseThreats(): List<Creep> {
        val hostiles = getHostileCreeps()
        if (hostiles.isEmpty()) return emptyList()
        val checkpoints = defendCheckpoints()
        return hostiles.filter { h ->
            checkpoints.any { cp -> h.getRangeTo(cp) <= SpawnStrikeStrategy.DEFEND_CHECKPOINT_RANGE }
        }
    }

    /** Saját fal törése: legalább 2 kijelölt fal eltűnt ÉS ellenség áll valamelyik cellán. */
    fun isEnemyBreakingOurWall(): Boolean {
        val watchedCoords = if (isInTop()) TOP_OWN_WALL_ALERT else BOTTOM_OWN_WALL_ALERT
        val walls = getObjectsByPrototype(StructureWall::class.js).toList().filter { it.exists }
        val brokenCount = watchedCoords.count { coord ->
            walls.none { it.x == coord.x && it.y == coord.y }
        }
        if (brokenCount < 2) return false

        val hostiles = getHostileCreeps()
        return hostiles.any { hostile ->
            watchedCoords.any { w -> hostile.x == w.x && hostile.y == w.y }
        }
    }

    // --- Flanker/Assault wall-breach state ---
    fun isFlankerWallBreached(): Boolean = Assault.wallBreached

    // --- Rally pontok ---

    /** Gyülekezési/védelmi pont */
    fun getDefensiveRallyPoint(): Position =
        if (isInTop()) pos(51, 78) else pos(48, 21)

    fun getHostileCreeps(): List<Creep> =
        getObjectsByPrototype(Creep::class.js).toList().filter { it.exists && !it.my }

    fun getEnemySpawns(): List<StructureSpawn> =
        getObjectsByPrototype(StructureSpawn::class.js).toList().filter { it.exists && it.my== false }

    fun hostilesInCenter(): List<Creep> =
        getHostileCreeps().filter { CenterZone.contains(it) }

    fun getMyCaptureFlag(): Flag? =
        getObjectsByPrototype(Flag::class.js).toList().firstOrNull { it.exists && it.my==true }

    fun flagsToCapture(): List<Flag> =
        getObjectsByPrototype(Flag::class.js).toList().filter { it.exists && it.my==false }

    fun getEnemySpawn(): StructureSpawn? = getEnemySpawns().minByOrNull { mySpawn.getRangeTo(it) }

    fun isInTop(): Boolean = mySpawn.y < 50

    fun shouldAssaultEnemySpawn(): Boolean =
        Assault.wallBreached || getTicks() >= SpawnStrikeStrategy.ASSAULT_START_TICK
}

object GameplayUtil {
    fun getMySpawn(): StructureSpawn =
        getObjectsByPrototype(StructureSpawn::class.js).toList().first { it.my == true }

    fun getMyCreeps(): List<Creep> =
        getObjectsByPrototype(Creep::class.js).toList().filter { it.exists && it.my && !it.spawning }
}

fun pos(x: Int, y: Int): Position = object : Position {
    override var x = x
    override var y = y
}