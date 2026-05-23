package season3.escortrun

import screeps.api.*
import screeps.api.structures.StructureSpawn

// ── Creep típusok ─────────────────────────────────────────────────────────────

enum class CreepType {
    WORKER,        // WORK×3 CARRY
    HEAVY_WORKER,  // WORK×5 CARRY
    HARVESTER,     // MOVE CARRY
    CARRY,         // CARRY – relay közvetítő W2 és H1 között
    RANGER,        // TOUGH MOVE×5 RANGED_ATTACK×4
    HYBRID,        // MOVE×2 RANGED_ATTACK HEAL
    DIGGER,        // MOVE ATTACK×10
    LIGHT_RANGER,  // MOVE×2 RANGED_ATTACK×2 – gyors, olcsó kiter
    CHEAP_RANGER,  // MOVE×1 RANGED_ATTACK×1 – legolcsóbb, özön taktika
    SELF_HEAL_RANGER, // MOVE×3 RANGED_ATTACK×3 HEAL×1 – öngyógyító harcos
}

// ── Factory ───────────────────────────────────────────────────────────────────

interface CreepFactory {
    fun createCreep(spawn: StructureSpawn): Creep?

    companion object {
        fun of(type: CreepType): CreepFactory = when (type) {
            CreepType.WORKER       -> WorkerFactory
            CreepType.HEAVY_WORKER -> HeavyWorkerFactory
            CreepType.HARVESTER    -> HarvesterFactory
            CreepType.CARRY        -> CarryFactory
            CreepType.RANGER       -> RangerFactory
            CreepType.HYBRID       -> HybridFactory
            CreepType.DIGGER       -> DiggerFactory
            CreepType.LIGHT_RANGER -> LightRangerFactory
            CreepType.CHEAP_RANGER -> CheapRangerFactory
            CreepType.SELF_HEAL_RANGER -> SelfHealRangerFactory
        }
    }
}

private object WorkerFactory : CreepFactory {
    override fun createCreep(spawn: StructureSpawn) =
        spawn.spawnCreep(arrayOf(WORK, WORK, WORK, CARRY)).`object`
}

private object HeavyWorkerFactory : CreepFactory {
    override fun createCreep(spawn: StructureSpawn) =
        spawn.spawnCreep(arrayOf(WORK, WORK, WORK, WORK, WORK, CARRY)).`object`
}

private object HarvesterFactory : CreepFactory {
    override fun createCreep(spawn: StructureSpawn) =
        spawn.spawnCreep(arrayOf(MOVE, CARRY)).`object`
}

private object CarryFactory : CreepFactory {
    override fun createCreep(spawn: StructureSpawn) =
        spawn.spawnCreep(arrayOf(CARRY)).`object`
}

private object RangerFactory : CreepFactory {
    override fun createCreep(spawn: StructureSpawn) =
        spawn.spawnCreep(arrayOf(TOUGH, MOVE, MOVE, MOVE, MOVE, MOVE, RANGED_ATTACK, RANGED_ATTACK, RANGED_ATTACK, RANGED_ATTACK)).`object`
}

private object HybridFactory : CreepFactory {
    override fun createCreep(spawn: StructureSpawn) =
        spawn.spawnCreep(arrayOf(MOVE, MOVE, MOVE, HEAL, HEAL)).`object`
}

private object SelfHealRangerFactory : CreepFactory {
    override fun createCreep(spawn: StructureSpawn) =
        spawn.spawnCreep(arrayOf(MOVE, MOVE, MOVE, RANGED_ATTACK, RANGED_ATTACK, RANGED_ATTACK, HEAL)).`object`
}

private object CheapRangerFactory : CreepFactory {
    override fun createCreep(spawn: StructureSpawn) =
        spawn.spawnCreep(arrayOf(MOVE, RANGED_ATTACK)).`object`
}

private object LightRangerFactory : CreepFactory {
    override fun createCreep(spawn: StructureSpawn) =
        spawn.spawnCreep(arrayOf(MOVE, MOVE, RANGED_ATTACK, RANGED_ATTACK)).`object`
}

private object DiggerFactory : CreepFactory {
    override fun createCreep(spawn: StructureSpawn) =
        spawn.spawnCreep(arrayOf(MOVE, ATTACK, ATTACK, ATTACK, ATTACK, ATTACK, ATTACK, ATTACK, ATTACK, ATTACK, ATTACK)).`object`
}