package season1.spawnstrike

import screeps.api.*
import screeps.api.structures.*
import season1.spawnstrike.CombatTuning.RANGED_ATTACK_RANGE

object Assault {

    private const val SELF_HEAL_THRESHOLD = 0.95
    private const val SPAWN_DEFENSE_RANGE = 15
    private const val SPAWN_DEFENDER_MAX_RANGE = 5
    private const val MAX_COHESION_DIST = 4
    private const val ASSAULT_ENGAGE_RANGE = 5

    private val BOTTOM_STAND       = pos(7, 44)
    private val BOTTOM_WALL_COORDS = listOf(pos(6, 44), pos(5, 43), pos(4, 42), pos(3, 41), pos(2, 40))

    private val TOP_STAND       = pos(92, 55)
    private val TOP_WALL_COORDS = listOf(pos(93, 55), pos(94, 56), pos(95, 57), pos(96, 58), pos(97, 59))

    var wallBreached = false
        private set
    private var spawnDefenseTriggered = false
    private var leaderPreviousPosition: Position? = null

    fun execute(creep: Creep, gameplay: Gameplay) {
        healAssaultPack(creep, gameplay)
        if (!isSpawnDefender(creep, gameplay)) {
            leaderPreviousPosition = pos(creep.x, creep.y)
        }
        if (isSpawnDefender(creep, gameplay)) {
            executeSpawnDefender(creep, gameplay)
        } else {
            executeWallBreacher(creep, gameplay)
        }
    }

    /**
     * Hátsó assault: ellenfél falbetörésekor spawn defense (nem a falat lövő).
     * Egyébként hátsó sorban segít törni / pusholni.
     */
    private fun executeSpawnDefender(creep: Creep, gameplay: Gameplay) {
        if (gameplay.isEnemyBreakingOurWall()) {
            spawnDefenseTriggered = true
        }
        if (spawnDefenseTriggered || gameplay.isCheckpointDefendMode()) {
            spawnDefenseBehavior(creep, gameplay)
            return
        }
        wallBreachBehavior(creep, gameplay, useRearLane = true)
    }

    /**
     * Elülső assault: csak falat fúr, majd enemy spawnra megy.
     */
    private fun executeWallBreacher(creep: Creep, gameplay: Gameplay) {
        if (wallBreached) {
            pushBehavior(creep, gameplay)
            return
        }
        wallBreachBehavior(creep, gameplay, useRearLane = false)
    }

    private fun healAssaultPack(creep: Creep, gameplay: Gameplay) {
        if (!creep.canHeal()) return
        val injuredAssault = gameplay.myCreeps
            .filter { it.exists && it.role == Role.ASSAULT && it.hits < it.hitsMax }
            .minWithOrNull(compareBy<Creep>({ it.hits.toDouble() / it.hitsMax }, { creep.getRangeTo(it) }))
        if (injuredAssault != null) {
            val dist = creep.getRangeTo(injuredAssault)
            if (dist <= 1) {
                creep.heal(injuredAssault)
                return
            }
            if (dist <= RANGED_ATTACK_RANGE) {
                creep.rangedHeal(injuredAssault)
                return
            }
        }
        if (creep.hits.toDouble() / creep.hitsMax < SELF_HEAL_THRESHOLD) {
            creep.heal(creep)
        }
    }

    private fun checkpointDefenseBehavior(creep: Creep, gameplay: Gameplay) {
        val threats = gameplay.getCheckpointDefenseThreats().filter { isRelevantEnemy(it) }
        if (threats.isEmpty()) return

        shootBestTarget(creep, threats)

        val target = threats.minWithOrNull(
            compareBy<Creep>({ creep.getRangeTo(it) }, { it.hits }, { it.id }),
        ) ?: return
        when (creep.getRangeTo(target)) {
            0, 1, 2 -> kiteAwayFromHostiles(creep, threats)
            3 -> { /* ideális ranged távolság */ }
            else -> CombatActions.moveToward(creep, target, stopRange = RANGED_ATTACK_RANGE)
        }
    }

    /** Spawn defense: spawn 15 range ellenség, lő vagy közelít. */
    private fun spawnDefenseBehavior(creep: Creep, gameplay: Gameplay) {
        val spawnDist = creep.getRangeTo(gameplay.mySpawn)
        val nearSpawn = gameplay.getHostileCreeps()
            .filter { isRelevantEnemy(it) && gameplay.mySpawn.getRangeTo(it) <= SPAWN_DEFENSE_RANGE }
        val target = nearSpawn.minWithOrNull(
            compareBy<Creep>({ gameplay.mySpawn.getRangeTo(it) }, { creep.getRangeTo(it) }, { it.hits }, { it.id }),
        )

        if (target != null) {
            if (creep.canRangedAttack() && creep.getRangeTo(target) <= RANGED_ATTACK_RANGE) {
                creep.rangedAttack(target)
            }
            if (spawnDist > SPAWN_DEFENDER_MAX_RANGE) {
                creep.moveTo(gameplay.mySpawn)
            } else if (spawnDist < SPAWN_DEFENDER_MAX_RANGE && creep.getRangeTo(target) > RANGED_ATTACK_RANGE) {
                CombatActions.moveToward(creep, target, stopRange = RANGED_ATTACK_RANGE)
            }
            return
        }

        if (spawnDist > SPAWN_DEFENDER_MAX_RANGE) {
            creep.moveTo(gameplay.mySpawn)
            return
        }
        if (spawnDist > 2) {
            creep.moveTo(gameplay.mySpawn)
        }
    }

    private fun wallBreachBehavior(creep: Creep, gameplay: Gameplay, useRearLane: Boolean) {
        val isLeader = !useRearLane
        val hostiles = gameplay.getHostileCreeps().filter { isRelevantEnemy(it) }
        val isTop      = gameplay.isInTop()
        val wallCoords = if (isTop) TOP_WALL_COORDS else BOTTOM_WALL_COORDS
        val allWalls   = getObjectsByPrototype(StructureWall::class.js).toList().filter { it.exists }

        val nextWallCoord = wallCoords.firstOrNull { coord ->
            allWalls.any { w -> w.x == coord.x && w.y == coord.y }
        }

        if (nextWallCoord == null) {
            wallBreached = true
            pushBehavior(creep, gameplay)
            return
        }

        val wallTarget = allWalls.first { w -> w.x == nextWallCoord.x && w.y == nextWallCoord.y }
        val prevIdx    = wallCoords.indexOf(nextWallCoord) - 1
        val shootFrom  = if (prevIdx < 0) {
            if (isTop) TOP_STAND else BOTTOM_STAND
        } else {
            wallCoords[prevIdx]
        }
        val standPos = if (useRearLane) {
            leaderPreviousPosition ?: rearStandPosition(isTop, shootFrom, nextWallCoord)
        } else {
            shootFrom
        }

        if (shootNearbyThreat(creep, hostiles)) {
            // Ranged attack és fal lövés ugyanaz az akció; ha harcolt, tartsa a lane pozíciót.
            CombatActions.moveToward(creep, standPos, stopRange = 1)
            return
        }

        if (creep.getRangeTo(wallTarget) <= RANGED_ATTACK_RANGE && creep.canRangedAttack()) {
            creep.rangedAttack(wallTarget)
        }

        val moveTarget = if (isLeader && creep.getRangeTo(wallTarget) <= RANGED_ATTACK_RANGE) nextWallCoord else standPos
        CombatActions.moveToward(creep, moveTarget, stopRange = if (isLeader) 0 else 1)
    }

    private fun pushBehavior(creep: Creep, gameplay: Gameplay) {
        val sameTactic = !spawnDefenseTriggered
        val ally = assaultAlly(creep, gameplay)
        val isLeader = !isSpawnDefender(creep, gameplay)
        val spawn    = gameplay.getEnemySpawn() ?: return
        val hostiles = gameplay.getHostileCreeps().filter { isRelevantEnemy(it) }

        val shotHostile = shootNearbyThreat(creep, hostiles)
        if (!shotHostile && creep.canRangedAttack() && creep.getRangeTo(spawn) <= RANGED_ATTACK_RANGE) {
            creep.rangedAttack(spawn)
            return
        }

        if (sameTactic && !isLeader && ally != null && creep.getRangeTo(ally) > MAX_COHESION_DIST) {
            creep.moveTo(ally)
            return
        }

        CombatActions.moveToward(creep, spawn, stopRange = RANGED_ATTACK_RANGE)
    }

    private fun shootBestTarget(creep: Creep, hostiles: List<Creep>): Boolean {
        if (!creep.canRangedAttack()) return false
        val inRange = hostiles.filter { creep.getRangeTo(it) <= RANGED_ATTACK_RANGE }
        if (inRange.isEmpty()) return false
        if (inRange.size >= 3) creep.rangedMassAttack()
        else creep.rangedAttack(inRange.minByOrNull { it.hits }!!)
        return true
    }

    private fun shootNearbyThreat(creep: Creep, hostiles: List<Creep>): Boolean {
        val nearby = hostiles.filter { creep.getRangeTo(it) <= ASSAULT_ENGAGE_RANGE }
        if (nearby.isEmpty()) return false
        return shootBestTarget(creep, nearby)
    }

    private fun isRelevantEnemy(enemy: Creep): Boolean =
        enemy.body.any { it.type == ATTACK || it.type == RANGED_ATTACK || it.type == HEAL }

    private fun kiteAwayFromHostiles(creep: Creep, hostiles: List<Creep>) {
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

    private fun assaultAlly(creep: Creep, gameplay: Gameplay): Creep? =
        gameplay.myCreeps.firstOrNull { it.exists && it.role == Role.ASSAULT && it.id != creep.id }

    private fun rearStandPosition(isTop: Boolean, shootFrom: Position, nextWallCoord: Position): Position {
        val dx = (shootFrom.x - nextWallCoord.x).coerceIn(-1, 1)
        val dy = (shootFrom.y - nextWallCoord.y).coerceIn(-1, 1)
        if (dx == 0 && dy == 0) {
            return if (isTop) pos(TOP_STAND.x - 1, TOP_STAND.y) else pos(BOTTOM_STAND.x + 1, BOTTOM_STAND.y)
        }
        return pos(
            (shootFrom.x + dx).coerceIn(1, 98),
            (shootFrom.y + dy).coerceIn(1, 98),
        )
    }

    /** Hátsó assault (id szerint a második): spawn defender – nem a falat lövő. */
    private fun isSpawnDefender(creep: Creep, gameplay: Gameplay): Boolean {
        val assaults = gameplay.myCreeps
            .filter { it.exists && it.role == Role.ASSAULT }
            .sortedBy { it.id }
        return assaults.size >= 2 && assaults[1].id == creep.id
    }
}
