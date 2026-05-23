package season3.escortrun

import screeps.api.*
import screeps.api.season3.EscortCreep
import screeps.api.structures.*
import season3.escortrun.combat.CombatTuning
import season3.escortrun.escort.EscortCreepController

// ── Position helper ───────────────────────────────────────────────────────────

fun pos(x: Int, y: Int): Position = object : Position {
    override var x = x
    override var y = y
}

// ── GameplayUtil ──────────────────────────────────────────────────────────────

object GameplayUtil {
    fun getMySpawn(): StructureSpawn =
        getObjectsByPrototype(StructureSpawn::class.js).toList().first { it.my == true }

    fun getMySource(): Source {
        val sources = getObjectsByPrototype(Source::class.js).toList()
        return getMySpawn().findClosestByRange(sources.toTypedArray())
    }

    /** Saját "harci" creepek – VIP (hitsMax 5000) kizárva. */
    fun getMyCreeps(): List<Creep> =
        getObjectsByPrototype(Creep::class.js).toList()
            .filter { it.exists && it.my == true && it.hitsMax != 5000 }

    fun getMyEscortCreep(): EscortCreep? =
        getObjectsByPrototype(EscortCreep::class.js).toList().firstOrNull { it.my == true }
}

// ── Gameplay ──────────────────────────────────────────────────────────────────

/**
 * Tick-szintű snapshot: minden fontos objektum egy helyen.
 * Stratégiai döntések (spawn, rally, célválasztás) innen olvasnak.
 */
class Gameplay {
    val mySpawn: StructureSpawn  = GameplayUtil.getMySpawn()
    val mySource: Source         = GameplayUtil.getMySource()
    val myCreeps: List<Creep>    = GameplayUtil.getMyCreeps()
    val myEscortCreep: EscortCreep? = GameplayUtil.getMyEscortCreep()

    val isTopSide: Boolean get() = mySpawn.y < 50

    // ── Spawns ────────────────────────────────────────────────────────────────

    fun getMySpawns(): List<StructureSpawn> =
        getObjectsByPrototype(StructureSpawn::class.js).toList()
            .filter { it.exists && it.my == true }

    fun getEnemySpawns(): List<StructureSpawn> =
        getObjectsByPrototype(StructureSpawn::class.js).toList()
            .filter { it.exists && it.my == false }

    // ── Creeps ────────────────────────────────────────────────────────────────

    /** Összes ellenséges creep, VIP nélkül. */
    fun getEnemyCreeps(): List<Creep> =
        getObjectsByPrototype(Creep::class.js).toList()
            .filter { it.exists && it.my == false && it.hitsMax != 5000 }

    /** Összes ellenséges creep (VIP-pel együtt) – lövéshez, célválasztáshoz. */
    fun getHostileCreeps(): List<Creep> =
        getObjectsByPrototype(Creep::class.js).toList()
            .filter { it.exists && it.my == false }

    /** Ellenséges EscortCreep (hitsMax == 5000). */
    fun getEnemyEscortVip(): Creep? =
        getObjectsByPrototype(Creep::class.js).toList()
            .firstOrNull { it.exists && it.my == false && it.hitsMax == 5000 }

    // ── Flags & Sites ─────────────────────────────────────────────────────────

    fun getMyCaptureFlag(): Flag? =
        getObjectsByPrototype(Flag::class.js).toList().firstOrNull { it.exists && it.my == true }

    fun getEnemyTeamFlag(): Flag? =
        getObjectsByPrototype(Flag::class.js).toList().firstOrNull { it.exists && it.my == false }

    fun getHostileConstructionSites(): List<ConstructionSite> =
        getObjectsByPrototype(ConstructionSite::class.js).toList().filter { it.exists && it.my != true }

    // ── Pozíciók ──────────────────────────────────────────────────────────────

    /**
     * Saját capture cél: a saját zászló, fallback az ellenséges fő spawn.
     * Ide tart az EscortCreep / kígyó vezető.
     */
    fun getCaptureTarget(): Position? =
        getObjectsByPrototype(Flag::class.js).toList().firstOrNull { it.my == true }
            ?: getObjectsByPrototype(StructureSpawn::class.js).toList().firstOrNull { it.my == false }

    fun getMapCenterRally(): Position = if (isTopSide) pos(49, 49) else pos(49, 50)

    /** Harci csapat gyülekezője (top: 51,51 / bottom: 51,48). */
    fun getCombatRallyPoint(): Position = if (isTopSide) pos(51, 51) else pos(51, 48)

    // ── Target prioritás ──────────────────────────────────────────────────────

    /**
     * Ha a harcos közel van a saját VIP-hez, az őt közvetlenül sebző ellenfél kap prioritást.
     * Több jelölt közül: legalacsonyabb HP, döntetlennél legközelebbi.
     */
    fun getEscortDamageThreatPriority(from: Creep): Creep? {
        val escort = myEscortCreep ?: return null
        if (from.getRangeTo(escort) > CombatTuning.ESCORT_GUARD_FIGHTER_MAX_RANGE) return null
        val threats = getHostileCreeps().filter { it.isDirectDamageThreatToEscort(escort) }
        if (threats.isEmpty()) return null
        return threats.minWithOrNull(compareBy({ it.hits }, { from.getRangeTo(it) }))
    }

    /**
     * Stratégiai célprioritás (részletek a forráskódon belüli kommentben):
     * 1. Ellen VIP a mi zászlónk 25 range-én belül → ő a cél (capture deny)
     * 2. Saját EscortCreepet közvetlen sebző ellenség
     * 3. Ellenséges bővítés közelén lévő worker/builder
     * 4. Második ellenséges spawn közelén lévő harcos
     * 5. Ellenfél VIP kísérői (healer → attacker → maga a VIP)
     * 6. Nincs VIP → legközelebbi ellenség
     */
    fun getPriorityTarget(from: Creep): Creep? {
        val allEnemies = getHostileCreeps()
        if (allEnemies.isEmpty()) return null

        val enemyVip     = allEnemies.firstOrNull { it.hitsMax == 5000 }
        val nonVipEnemies = allEnemies.filter { it.hitsMax != 5000 }
        val myFlag       = getCaptureTarget()

        // 1. VIP deny
        if (enemyVip != null && myFlag != null &&
            enemyVip.getRangeTo(myFlag) <= EscortRunStrategy.ENEMY_VIP_FLAG_DENY_RANGE
        ) return enemyVip

        // 2. Escort guard
        getEscortDamageThreatPriority(from)?.let { return it }

        // 3. Ellenséges építő
        val hostileSites = getHostileConstructionSites()
        if (hostileSites.isNotEmpty()) {
            nonVipEnemies
                .filter { enemy -> hostileSites.any { site -> enemy.getRangeTo(site) <= CombatTuning.ENEMY_NEAR_HOSTILE_BUILD_RANGE } }
                .minWithOrNull(compareBy({ from.getRangeTo(it) }, { it.hits }))
                ?.let { return it }
        }

        // 4. Második spawn körüli harcos
        val enemySpawns = getEnemySpawns()
        if (enemySpawns.size >= 2) {
            val farthestSpawn = enemySpawns.maxByOrNull { it.getRangeTo(mySpawn) }!!
            nonVipEnemies
                .filter { it.getRangeTo(farthestSpawn) <= CombatTuning.ENEMY_NEAR_EXTRA_SPAWN_RANGE }
                .minByOrNull { from.getRangeTo(it) }
                ?.let { return it }
        }

        // 5. VIP kísérői / maga a VIP
        if (enemyVip != null) {
            nonVipEnemies.filter { it.getRangeTo(enemyVip) <= 8 && it.body.any { p -> p.type == HEAL } }
                .minByOrNull { from.getRangeTo(it) }?.let { return it }
            nonVipEnemies.filter { it.getRangeTo(enemyVip) <= 8 && it.body.any { p -> p.type == RANGED_ATTACK || p.type == ATTACK } }
                .minByOrNull { from.getRangeTo(it) }?.let { return it }
            return enemyVip
        }

        // 6. Fallback
        return nonVipEnemies.minByOrNull { from.getRangeTo(it) }
    }
}

// ── Main loop ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalJsExport::class)
@JsExport
fun loop() {
    val gameplay = Gameplay()

    // Spawn
    val idleSpawns = gameplay.getMySpawns().filter { it.spawning == null }
    if (idleSpawns.isNotEmpty()) {
        val next = SpawnQueue.next(gameplay)
        if (next != null) {
            for (spawn in idleSpawns.sortedBy { it.id }) {
                spawn.setDirections(SpawnQueue.directionFor(next, spawn))
                val result = CreepFactory.of(next).createCreep(spawn)
                if (result != null) {
                    SpawnQueue.onSuccess(next, gameplay)
                    break
                }
            }
        }
    }

    // Role + formation frissítés
    RoleAssigner.assign(gameplay)

    // EscortCreep vezérlés
    EscortCreepController.execute(gameplay)

    // Creep végrehajtás
    for (creep in gameplay.myCreeps) {
        creep.execute(gameplay)
    }
}