package season3.escortrun

import screeps.api.*
import season3.escortrun.combat.CombatTuning

// ── Szerepek ──────────────────────────────────────────────────────────────────

enum class Role {
    WORKER,               // WORK×3 CARRY – forrás bányász (fix pozíció)
    HARVESTER,            // MOVE CARRY   – energia szállító
    CARRY,                // CARRY only   – W2 és H1 közötti relay közvetítő
    COMBAT_HYBRID,        // MOVE×2 RANGED_ATTACK HEAL
    COMBAT_RANGER,        // TOUGH MOVE×5 RANGED_ATTACK×4
    COMBAT_LIGHT_RANGER,  // MOVE×2 RANGED_ATTACK×2 – gyors kiter
    COMBAT_CHEAP_RANGER,  // MOVE×1 RANGED_ATTACK×1 – özön
    COMBAT_SELF_HEAL_RANGER, // MOVE×3 RANGED_ATTACK×3 HEAL×1 – öngyógyító
    COMBAT_FLAG_BLOCKER,  // Tickenként az ellenséges zászló/spawn cellájára áll
    COMBAT_DIGGER,        // MOVE ATTACK×10 – fal bontó, majd saját flag felé
}

// ── Viselkedések ──────────────────────────────────────────────────────────────

enum class Behavior {
    WAIT,       // Gyülekező ponton vár
    CAPTURE,    // Rally felé megy / ellenséges zászló felé
    FOCUS_FIRE, // Aktívan támad
    HEAL,       // Harcos társ gyógyítása prioritással
    FOLLOW,     // Placeholder
}

// ── Tick-szintű state tárolás ─────────────────────────────────────────────────

private val roleMap     = mutableMapOf<String, Role>()
private val behaviorMap = mutableMapOf<String, Behavior>()

var Creep.role: Role
    get() = roleMap[id] ?: Role.WORKER
    set(value) { roleMap[id] = value }

var Creep.behavior: Behavior
    get() = behaviorMap[id] ?: Behavior.WAIT
    set(value) { behaviorMap[id] = value }

fun Creep.hasRole(): Boolean = roleMap.containsKey(id)

// ── Capability helpers ────────────────────────────────────────────────────────

fun Creep.canAttack(): Boolean        = body.any { it.type == ATTACK }
fun Creep.canRangedAttack(): Boolean  = body.any { it.type == RANGED_ATTACK }
fun Creep.canHeal(): Boolean          = body.any { it.type == HEAL }
fun Creep.isCarryOnly(): Boolean      = body.all { it.type == CARRY }

/**
 * Az ellenfél közvetlen sebzési fenyegetést jelent a saját VIP-re
 * (melee 1-es, ranged 3-as távolságból).
 */
fun Creep.isDirectDamageThreatToEscort(escort: screeps.api.season3.EscortCreep): Boolean {
    val d = getRangeTo(escort)
    if (d <= 1 && body.any { it.type == ATTACK })         return true
    if (d <= CombatTuning.RANGED_ATTACK_RANGE && body.any { it.type == RANGED_ATTACK }) return true
    return false
}