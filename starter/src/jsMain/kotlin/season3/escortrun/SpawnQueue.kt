package season3.escortrun

import screeps.api.*
import screeps.api.structures.StructureSpawn

/**
 * Spawn ütemező.
 *
 * **Normál sorrend** (nincs pánik mód):
 *   HARVESTER → WORKER → HARVESTER → HEAVY_WORKER →
 *   (combat ciklus: RANGER → RANGER → HYBRID, ismétlés)
 *   A 9. harci creep után (6 ranger + 3 hybrid) egyszer spawnolja a DIGGER-t,
 *   utána folytatja a RANGER/HYBRID ciklust a maradék 3 slotra (max 12 harci).
 *
 * **Pánik sorrend** (ellenség pánik zónán belül):
 *   HARVESTER → WORKER → WORKER → RANGER → RANGER → HYBRID → (ismétlés)
 *   Pánik módban DIGGER nem spawnolandó.
 *
 * **Spawn irány:**
 *   - Worker / Harvester / HeavyWorker: TOP_LEFT (top) / BOTTOM_LEFT (bottom)
 *   - Ranger / Hybrid / Digger: BOTTOM_RIGHT (top) / TOP_RIGHT (bottom)
 */
object SpawnQueue {

    // ── Pánik zóna koordináták ────────────────────────────────────────────────

    private const val PANIC_ZONE_TOP_X    = 41
    private const val PANIC_ZONE_TOP_Y    = 41
    private const val PANIC_ZONE_BOTTOM_X = 41
    private const val PANIC_ZONE_BOTTOM_Y = 60
    private const val PANIC_ZONE_RANGE    = 5

    // ── Harci cap ─────────────────────────────────────────────────────────────

    /** Összesen ennyi harci creep spawnolandó (ranger + hybrid + digger). */
    private const val MAX_COMBAT_CREEPS = 12

    /** Ennyi harci creep után spawn a Digger (egyszer). */
    private const val DIGGER_AFTER_COMBAT_COUNT = 9

    // ── Spawn sorrendek ───────────────────────────────────────────────────────

    private val NORMAL_ECONOMY = listOf(
        CreepType.HARVESTER,
        CreepType.WORKER,
        CreepType.HARVESTER,
        CreepType.HEAVY_WORKER,
        CreepType.CARRY,
    )
    private val NORMAL_COMBAT = listOf(
        CreepType.CHEAP_RANGER,
    )

    private val PANIC_ECONOMY = listOf(
        CreepType.HARVESTER,
        CreepType.WORKER,
        CreepType.WORKER,
    )
    private val PANIC_COMBAT = listOf(
        CreepType.CHEAP_RANGER,
    )

    // ── Belső állapot ─────────────────────────────────────────────────────────

    private var economyDone      = false
    private var combatCycleIndex = 0
    private var panicModeActive  = false
    private var diggerSpawned    = false  // egyszer spawnolandó

    private val targetWorkers    get() = if (panicModeActive) 2 else 1
    private val targetHarvesters = 1
    private val targetCarry      = 1   // csak normál módban, 1 db

    // ── Publikus API ──────────────────────────────────────────────────────────

    fun next(gameplay: Gameplay): CreepType? {
        panicModeActive = isPanicMode(gameplay)

        val revival = needsRevival(gameplay)
        if (revival != null) return revival

        val economy = currentEconomy()
        if (!economyDone) {
            val spawnedWorkers    = gameplay.myCreeps.count { it.role == Role.WORKER }
            val spawnedHarvesters = gameplay.myCreeps.count { it.role == Role.HARVESTER }
            val spawnedCarry      = gameplay.myCreeps.count { it.role == Role.CARRY }
            val targetW = economy.count { it == CreepType.WORKER || it == CreepType.HEAVY_WORKER }
            val targetH = economy.count { it == CreepType.HARVESTER }
            val targetC = economy.count { it == CreepType.CARRY }
            if (spawnedWorkers >= targetW && spawnedHarvesters >= targetH && spawnedCarry >= targetC) {
                economyDone = true
            }
        }

        if (!economyDone) return nextEconomyType(gameplay, economy)

        return nextCombatType(gameplay)
    }

    fun directionFor(type: CreepType, spawn: StructureSpawn): Array<DirectionConstant> {
        val isTop = spawn.y < 50
        return when (type) {
            CreepType.WORKER,
            CreepType.HEAVY_WORKER,
            CreepType.HARVESTER,
            CreepType.CARRY     -> if (isTop) arrayOf(TOP_LEFT) else arrayOf(BOTTOM_LEFT)

            CreepType.RANGER,
            CreepType.LIGHT_RANGER,
            CreepType.CHEAP_RANGER,
            CreepType.SELF_HEAL_RANGER,
            CreepType.HYBRID,
            CreepType.DIGGER    -> if (isTop) arrayOf(BOTTOM_RIGHT) else arrayOf(TOP_RIGHT)
        }
    }

    fun onSuccess(type: CreepType, gameplay: Gameplay) {
        when (type) {
            CreepType.RANGER, CreepType.LIGHT_RANGER, CreepType.CHEAP_RANGER, CreepType.SELF_HEAL_RANGER, CreepType.HYBRID -> {
                combatCycleIndex = (combatCycleIndex + 1) % currentCombat().size
            }
            CreepType.DIGGER -> {
                diggerSpawned = true
            }
            else -> {}
        }
    }

    // ── Pánik detektálás ──────────────────────────────────────────────────────

    fun isPanicMode(gameplay: Gameplay): Boolean {
        val (zx, zy) = if (gameplay.isTopSide) {
            PANIC_ZONE_TOP_X to PANIC_ZONE_TOP_Y
        } else {
            PANIC_ZONE_BOTTOM_X to PANIC_ZONE_BOTTOM_Y
        }
        val zone = pos(zx, zy)
        return gameplay.getHostileCreeps().any { enemy ->
            enemy.getRangeTo(zone) <= PANIC_ZONE_RANGE
        }
    }

    // ── Belső helpers ─────────────────────────────────────────────────────────

    private fun currentEconomy() = if (panicModeActive) PANIC_ECONOMY else NORMAL_ECONOMY
    private fun currentCombat()  = if (panicModeActive) PANIC_COMBAT  else NORMAL_COMBAT

    private fun nextEconomyType(gameplay: Gameplay, economy: List<CreepType>): CreepType? {
        val spawnedWorkers    = gameplay.myCreeps.count { it.role == Role.WORKER }
        val spawnedHarvesters = gameplay.myCreeps.count { it.role == Role.HARVESTER }
        val spawnedCarry      = gameplay.myCreeps.count { it.role == Role.CARRY }

        var wNeed = 0
        var hNeed = 0
        var cNeed = 0
        for (type in economy) {
            when (type) {
                CreepType.WORKER, CreepType.HEAVY_WORKER -> {
                    if (spawnedWorkers <= wNeed) return type
                    wNeed++
                }
                CreepType.HARVESTER -> {
                    if (spawnedHarvesters <= hNeed) return type
                    hNeed++
                }
                CreepType.CARRY -> {
                    if (spawnedCarry <= cNeed) return type
                    cNeed++
                }
                else -> {}
            }
        }
        economyDone = true
        return nextCombatType(gameplay)
    }

    /**
     * Harci spawn logika:
     * - Ha elértük a 12-es capt → null (ne spawoljon több harci creepet)
     * - Ha normál módban vagyunk és 9 harci creep van és Digger még nem spawolt → DIGGER
     * - Egyébként a RANGER/HYBRID ciklus
     */
    private fun nextCombatType(gameplay: Gameplay): CreepType? {
        val combatCount = countCombatCreeps(gameplay)

        // Cap elérve
        if (combatCount >= MAX_COMBAT_CREEPS) return null

        // Digger: csak normál módban, 9. után, egyszer
        if (!panicModeActive && !diggerSpawned && combatCount >= DIGGER_AFTER_COMBAT_COUNT) {
            return CreepType.DIGGER
        }

        val combat = currentCombat()
        return combat[combatCycleIndex % combat.size]
    }

    /** Harci creepek száma: ranger + hybrid + digger összesen. */
    private fun countCombatCreeps(gameplay: Gameplay): Int =
        gameplay.myCreeps.count {
            it.role == Role.COMBAT_RANGER ||
                    it.role == Role.COMBAT_HYBRID ||
                    it.role == Role.COMBAT_DIGGER
        }

    private fun needsRevival(gameplay: Gameplay): CreepType? {
        if (!economyDone) return null
        val workers    = gameplay.myCreeps.count { it.role == Role.WORKER }
        val harvesters = gameplay.myCreeps.count { it.role == Role.HARVESTER }
        val carry      = gameplay.myCreeps.count { it.role == Role.CARRY }
        if (harvesters < targetHarvesters) return CreepType.HARVESTER
        if (workers    < targetWorkers)    return CreepType.WORKER
        if (!panicModeActive && carry < targetCarry) return CreepType.CARRY
        return null
    }
}