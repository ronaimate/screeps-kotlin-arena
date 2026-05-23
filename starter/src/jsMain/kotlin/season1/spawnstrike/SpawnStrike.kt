package season1.spawnstrike

import screeps.api.*

enum class Role {
    RAIDER,
    RAIDER_COMBAT,
    ASSAULT,
    SCOUT,
}

private val roleMap = mutableMapOf<String, Role>()

var Creep.role: Role
    get() = roleMap[id] ?: Role.RAIDER
    set(value) { roleMap[id] = value }

fun Creep.hasRole(): Boolean = roleMap.containsKey(id)

fun Creep.canHeal(): Boolean = body.any { it.type == HEAL }

fun Creep.canRangedAttack(): Boolean = body.any { it.type == RANGED_ATTACK }

fun Creep.execute(gameplay: Gameplay) {
    when (role) {
        Role.RAIDER, Role.RAIDER_COMBAT -> Raider.execute(this, gameplay)
        Role.ASSAULT                    -> Assault.execute(this, gameplay)
        Role.SCOUT                      -> Scout.execute(this, gameplay)
    }
}

private val gameplay = Gameplay()

@OptIn(ExperimentalJsExport::class)
@JsExport
fun loop() {
    gameplay
        .apply { analyze() }
        .apply { spawnCreep() }
        .apply { assignStaticRoles() }
        .apply { myCreeps.forEach { it.execute(this) } }
}