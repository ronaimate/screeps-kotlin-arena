package season3.escortrun.economy

import screeps.api.Creep
import season3.escortrun.Gameplay
import season3.escortrun.Role
import season3.escortrun.pos
import season3.escortrun.role

// ── Pozíció konstansok ────────────────────────────────────────────────────────

/**
 * Lánc-pozíciók – spawn Y koordinátájából auto-választva (top/bottom szimmetria).
 *
 * Top:  spawn(9,9),  source(2,2)  → W1(3,3),  W2(2,3)
 * Bot:  spawn(9,90), source(2,97) → W1(3,96), W2(2,96)
 *
 * Lánc 1W+1H: Source → W1(fix) → H1(ingázik W1↔Spawn)
 * Lánc 2W+2H: Source → W2(fix) → W1(fix) → H1(ingázik W1↔H2) → H2(ingázik H1↔Spawn)
 */
data class ChainPositions(
    val worker1Target:                screeps.api.Position,
    val worker2Target:                screeps.api.Position,
    val harvester1JumpForW1:          screeps.api.Position,
    val harvester1JumpForW2:          screeps.api.Position,
    val harvester2JumpForW2:          screeps.api.Position,
    val waitingForWorker1:            screeps.api.Position,
    val harvester1WaitingForWorker2:  screeps.api.Position,
    val harvester1waitingForCarry1:   screeps.api.Position,
    val harvester2WaitingForWorker2:  screeps.api.Position,
    val harvester2waitingForCarry1:   screeps.api.Position,
)

object EscortPositions {
    fun get(spawnY: Int): ChainPositions = if (spawnY < 50) topPositions else botPositions

    private val topPositions = ChainPositions(
        worker1Target                = pos(3, 3),
        worker2Target                = pos(2, 3),
        harvester1JumpForW1          = pos(4, 4),
        harvester1JumpForW2          = pos(3, 4),
        harvester2JumpForW2          = pos(4, 4),
        waitingForWorker1            = pos(7, 7),
        harvester1WaitingForWorker2  = pos(6, 6),
        harvester1waitingForCarry1   = pos(5, 5),
        harvester2WaitingForWorker2  = pos(7, 7),
        harvester2waitingForCarry1   = pos(6, 6),
    )

    private val botPositions = ChainPositions(
        worker1Target                = pos(3, 96),
        worker2Target                = pos(2, 96),
        harvester1JumpForW1          = pos(4, 96),
        harvester1JumpForW2          = pos(3, 95),
        harvester2JumpForW2          = pos(4, 95),
        waitingForWorker1            = pos(7, 92),
        harvester1WaitingForWorker2  = pos(6, 93),
        harvester1waitingForCarry1   = pos(5, 94),
        harvester2WaitingForWorker2  = pos(7, 92),
        harvester2waitingForCarry1   = pos(6, 93),
    )
}

// ── EnergyChain ───────────────────────────────────────────────────────────────

object EnergyChain {

    fun getSortedWorkers(gameplay: Gameplay): List<Creep> =
        gameplay.myCreeps.filter { it.role == Role.WORKER }.sortedBy { it.id }

    fun getSortedHarvesters(gameplay: Gameplay): List<Creep> =
        gameplay.myCreeps.filter { it.role == Role.HARVESTER }.sortedBy { it.id }

    fun getPrimaryWorker(gameplay: Gameplay):     Creep? = getSortedWorkers(gameplay).firstOrNull()
    fun getSecondaryWorker(gameplay: Gameplay):   Creep? = getSortedWorkers(gameplay).getOrNull(1)
    fun getPrimaryHarvester(gameplay: Gameplay):  Creep? = getSortedHarvesters(gameplay).firstOrNull()
    fun getSecondaryHarvester(gameplay: Gameplay):Creep? = getSortedHarvesters(gameplay).getOrNull(1)
    fun getCarry(gameplay: Gameplay):             Creep? = gameplay.myCreeps.firstOrNull { it.role == Role.CARRY }

    fun isWorker1InPlace(gameplay: Gameplay): Boolean {
        val w1     = getPrimaryWorker(gameplay) ?: return false
        val target = EscortPositions.get(gameplay.mySpawn.y).worker1Target
        return w1.x == target.x && w1.y == target.y
    }

    fun isWorker2InPlace(gameplay: Gameplay): Boolean {
        val w2     = getSecondaryWorker(gameplay) ?: return false
        val target = EscortPositions.get(gameplay.mySpawn.y).worker2Target
        return w2.x == target.x && w2.y == target.y
    }

    /** CARRY a relay pozícióján van-e (top: 4,4 / bot: 4,95). */
    fun isCarryInPlace(gameplay: Gameplay): Boolean {
        val carry  = getCarry(gameplay) ?: return false
        val target = if (gameplay.mySpawn.y < 50) pos(4, 4) else pos(4, 95)
        return carry.x == target.x && carry.y == target.y
    }
}