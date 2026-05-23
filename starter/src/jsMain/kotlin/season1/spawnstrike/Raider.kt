package season1.spawnstrike

import screeps.api.*
import season1.spawnstrike.CombatTuning.RANGED_ATTACK_RANGE

object Raider {

    private var myFlagReached = false
    private var raiderCrossing = false
    private var allFlagsCapturedTick: Int? = null
    private var combatFocusTargetId: String? = null
    private val waypointReached = mutableSetOf<String>()
    private val waitingTargetPrimary = mutableMapOf<String, Boolean>()

    private val BOTTOM_HOLD = pos(51, 21)
    private val TOP_HOLD    = pos(48, 78)
    private val BOTTOM_SPAWN_WAYPOINT = pos(72, 4)
    private val TOP_SPAWN_WAYPOINT = pos(27, 96)
    private const val HOLD_AFTER_CAPTURE_TICKS = 450
    private const val FOLLOW_LEADER_RANGE = 5
    private const val HOSTILE_RETREAT_RANGE = 10

    fun execute(creep: Creep, gameplay: Gameplay) {
        when (creep.role) {
            Role.RAIDER        -> executeFlagRunner(creep, gameplay)
            Role.RAIDER_COMBAT -> executeCombatRaider(creep, gameplay)
            else               -> {}
        }
    }

    private fun executeFlagRunner(creep: Creep, gameplay: Gameplay) {
        RaiderPath.recordLeaderStep(creep)

        val myFlag = gameplay.getMyCaptureFlag() ?: return
        val enemyFlag = gameplay.flagsToCapture().firstOrNull()
        val onMyFlag = creep.x == myFlag.x && creep.y == myFlag.y
        val enemyFlagOccupied = enemyFlag?.let { isAnyCreepOnPosition(gameplay, it) } ?: false

        if (onMyFlag) myFlagReached = true

        if (raiderCrossing) {
            if (enemyFlag == null) {
                raiderCrossing = false
                markFlagsCaptured()
            } else if (creep.x == enemyFlag.x && creep.y == enemyFlag.y) {
                raiderCrossing = false
            } else if (enemyFlagOccupied && !(creep.x == enemyFlag.x && creep.y == enemyFlag.y)) {
                raiderCrossing = false
            } else {
                creep.moveTo(enemyFlag)
                return
            }
        }

        if (enemyFlag != null) {
            allFlagsCapturedTick = null
            if (onMyFlag && !enemyFlagOccupied) {
                raiderCrossing = true
                creep.moveTo(enemyFlag)
                return
            }
            if (!onMyFlag) {
                creep.moveTo(myFlag)
            }
            return
        }

        markFlagsCaptured()
        postCaptureRaider(creep, gameplay)
    }

    /**
     * Ha van középkörös célpont, azonnal harcol.
     * Csak célpont nélkül másolja a leader útvonalát, amíg ≤5 range.
     */
    private fun executeCombatRaider(creep: Creep, gameplay: Gameplay) {
        val leader = findFlagRunner(gameplay)

        val centerFoes = gameplay.hostilesInCenter()
        if (centerFoes.isNotEmpty()) {
            shootFocusInCenter(creep, centerFoes)
            return
        }
        combatFocusTargetId = null

        val enemyFlags = gameplay.flagsToCapture()
        if (enemyFlags.isEmpty()) {
            markFlagsCaptured()
            postCaptureRaider(creep, gameplay)
            return
        }
        allFlagsCapturedTick = null

        if (leader != null && creep.getRangeTo(leader) > FOLLOW_LEADER_RANGE) {
            followLeaderPath(creep, leader)
            return
        }

        if (leader != null && creep.getRangeTo(leader) > 2) {
            followLeaderPath(creep, leader)
        }
    }

    private fun followLeaderPath(creep: Creep, leader: Creep) {
        val step = RaiderPath.nextStepFor(creep)
        when {
            step != null && (step.x != creep.x || step.y != creep.y) -> creep.moveTo(step)
            creep.getRangeTo(leader) > FOLLOW_LEADER_RANGE -> creep.moveTo(leader)
        }
    }

    private fun shootFocusInCenter(creep: Creep, centerFoes: List<Creep>) {
        val focus = resolveCombatFocusTarget(centerFoes) ?: return
        val dist = creep.getRangeTo(focus)
        if (dist <= RANGED_ATTACK_RANGE) {
            creep.rangedAttack(focus)
        }
        // Középkörös harcnál ne próbáljon fix 3-as távolságot tartani, mert attól be tud akadni.
        if (dist > 1) creep.moveTo(focus)
    }

    private fun resolveCombatFocusTarget(centerFoes: List<Creep>): Creep? {
        val current = combatFocusTargetId?.let { id -> centerFoes.firstOrNull { it.id == id } }
        val attackers = centerFoes.filter { hostileCanAttack(it) }
        if (current != null && (hostileCanAttack(current) || attackers.isEmpty())) return current

        val targetPool = if (attackers.isNotEmpty()) attackers else centerFoes
        val picked = targetPool.minWithOrNull(
            compareBy<Creep>({ it.getRangeTo(CenterZone.center) }, { it.hits }, { it.id }),
        )
        combatFocusTargetId = picked?.id
        return picked
    }

    private fun hostileCanAttack(hostile: Creep): Boolean =
        hostile.body.any { it.type == ATTACK || it.type == RANGED_ATTACK }

    private fun findFlagRunner(gameplay: Gameplay): Creep? =
        gameplay.myCreeps.firstOrNull { it.exists && it.role == Role.RAIDER }

    private fun postCaptureRaider(creep: Creep, gameplay: Gameplay) {
        val primaryHold = primaryHold(gameplay)
        val secondaryHold = secondaryHold(gameplay)
        val primary = primaryRaider(gameplay) ?: return
        val secondary = secondaryRaider(gameplay)

        if (creep.id == primary.id) {
            if (creep.getRangeTo(primaryHold) > 0) creep.moveTo(primaryHold)
            return
        }

        if (creep.role != Role.RAIDER_COMBAT) {
            secondary?.let { if (creep.getRangeTo(it) > 2) creep.moveTo(it) }
            return
        }

        if (shouldHoldAfterCapture()) {
            waitOrEvade(creep, gameplay, secondary, primaryHold, secondaryHold)
            return
        }

        pushEnemySpawnOrRetreat(creep, gameplay, secondaryHold)
    }

    private fun holdSecondaryPosition(creep: Creep, secondary: Creep?, secondaryHold: Position) {
        if (secondary?.id == creep.id || secondary == null) {
            if (creep.getRangeTo(secondaryHold) > 0) creep.moveTo(secondaryHold)
        } else if (creep.getRangeTo(secondary) > 2) {
            creep.moveTo(secondary)
        }
    }

    private fun waitOrEvade(
        creep: Creep,
        gameplay: Gameplay,
        secondary: Creep?,
        primaryHold: Position,
        secondaryHold: Position,
    ) {
        val tooClose = gameplay.getHostileCreeps().any { creep.getRangeTo(it) <= HOSTILE_RETREAT_RANGE }
        if (!tooClose) {
            waitingTargetPrimary.remove(creep.id)
            holdSecondaryPosition(creep, secondary, secondaryHold)
            return
        }

        val goPrimary = waitingTargetPrimary.getOrPut(creep.id) {
            val closerToSecondary = creep.getRangeTo(secondaryHold) <= creep.getRangeTo(primaryHold)
            closerToSecondary
        }
        val target = if (goPrimary) primaryHold else secondaryHold
        if (creep.getRangeTo(target) <= 5) {
            waitingTargetPrimary[creep.id] = !goPrimary
        }
        val nextTarget = if (waitingTargetPrimary[creep.id] == true) primaryHold else secondaryHold
        creep.moveTo(nextTarget)
    }

    private fun pushEnemySpawnOrRetreat(creep: Creep, gameplay: Gameplay, retreatPos: Position) {
        val spawn = gameplay.getEnemySpawn()
        val hostiles = gameplay.getHostileCreeps()
        val passedWaypoint = waypointReached.contains(creep.id)

        CombatActions.shootHostiles(creep, hostiles)

        val tooClose = hostiles.any { creep.getRangeTo(it) <= HOSTILE_RETREAT_RANGE }
        if ((!passedWaypoint && tooClose) || spawn == null) {
            if (creep.getRangeTo(retreatPos) > 0) creep.moveTo(retreatPos)
            return
        }

        val waypoint = if (gameplay.isInTop()) TOP_SPAWN_WAYPOINT else BOTTOM_SPAWN_WAYPOINT
        if (!passedWaypoint) {
            if (creep.getRangeTo(waypoint) <= 1) {
                waypointReached.add(creep.id)
            } else {
                creep.moveTo(waypoint)
                return
            }
        }

        if (creep.getRangeTo(spawn) <= RANGED_ATTACK_RANGE) {
            creep.rangedAttack(spawn)
            return
        }
        creep.moveTo(spawn)
    }

    private fun markFlagsCaptured() {
        if (allFlagsCapturedTick == null) {
            allFlagsCapturedTick = getTicks()
        }
    }

    private fun shouldHoldAfterCapture(): Boolean {
        val capturedTick = allFlagsCapturedTick ?: return false
        return getTicks() - capturedTick < HOLD_AFTER_CAPTURE_TICKS
    }

    private fun primaryHold(gameplay: Gameplay): Position =
        if (gameplay.isInTop()) TOP_HOLD else BOTTOM_HOLD

    private fun secondaryHold(gameplay: Gameplay): Position =
        if (gameplay.isInTop()) BOTTOM_HOLD else TOP_HOLD

    private fun aliveRaiders(gameplay: Gameplay): List<Creep> =
        gameplay.myCreeps
            .filter { it.exists && (it.role == Role.RAIDER || it.role == Role.RAIDER_COMBAT) }
            .sortedBy { it.id }

    private fun primaryRaider(gameplay: Gameplay): Creep? {
        val raiders = aliveRaiders(gameplay)
        return raiders.firstOrNull { it.role == Role.RAIDER } ?: raiders.firstOrNull()
    }

    private fun secondaryRaider(gameplay: Gameplay): Creep? {
        val primary = primaryRaider(gameplay) ?: return null
        return aliveRaiders(gameplay).firstOrNull { it.id != primary.id }
    }

    private fun isAnyCreepOnPosition(gameplay: Gameplay, position: Position): Boolean {
        val hostiles = gameplay.getHostileCreeps()
        val myCreeps = gameplay.myCreeps
        return (hostiles + myCreeps).any { it.exists && it.x == position.x && it.y == position.y }
    }
}
