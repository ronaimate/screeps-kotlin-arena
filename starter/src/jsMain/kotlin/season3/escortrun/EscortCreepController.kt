package season3.escortrun.escort

import screeps.api.*
import screeps.api.structures.StructureWall
import season3.escortrun.Gameplay
import season3.escortrun.Role
import season3.escortrun.economy.EnergyChain
import season3.escortrun.economy.WorkerBehavior
import season3.escortrun.pos
import season3.escortrun.role

/**
 * EscortCreep (VIP) vezérlése.
 *
 * **Ha van Digger** (COMBAT_DIGGER szerepű creep él):
 *   Az escort folyamatosan követi a Diggert.
 *   Amint a Digger áttörte a falat és a saját flag felé indul,
 *   az escort is a flag felé tart (ezt a Digger pozíciójából detektáljuk:
 *   ha a Digger már nem talál falat → flag fázis → escort is flag felé).
 *
 * **Ha nincs Digger:**
 *   Állapot 1 – pre-boost: VIP a forrás közelében vár (Top: 4,5 / Bot: 4,94)
 *   Állapot 2 – post-boost: VIP előre megy staging ponthoz (Top: 8,10 / Bot: 8,90)
 */
object EscortCreepController {

    private val PRE_BOOST_TOP  = pos(4, 5)
    private val PRE_BOOST_BOT  = pos(4, 94)
    private val POST_BOOST_TOP = pos(8, 10)
    private val POST_BOOST_BOT = pos(8, 90)

    fun execute(gameplay: Gameplay) {
        val escort = gameplay.myEscortCreep ?: return

        // Ha van Digger → nézzük meg van-e még fal
        val digger = gameplay.myCreeps.firstOrNull { it.role == Role.COMBAT_DIGGER }
        if (digger != null) {
            val wallY = if (gameplay.isTopSide) 9 else 90
            val wallsRemain = getObjectsByPrototype(StructureWall::class.js).toList()
                .any { it.exists && it.x in 43..61 && it.y == wallY }

            if (wallsRemain) {
                // Fal még áll → kövesse a Diggert
                escort.moveTo(digger)
            } else {
                // Fal áttörve → menjen egyenesen a saját flagra
                val flag = gameplay.getCaptureTarget() ?: return
                escort.moveTo(flag)
            }
            return
        }

        // Nincs Digger → eredeti pre/post-boost logika
        // Post-boost: csak akkor indul el ha CARRY már a relay pozícióján van
        val carryReady = !WorkerBehavior.boostedEconomyBuilt || EnergyChain.isCarryInPlace(gameplay)
        val target = if (WorkerBehavior.boostedEconomyBuilt && carryReady) {
            if (gameplay.isTopSide) POST_BOOST_TOP else POST_BOOST_BOT
        } else {
            if (gameplay.isTopSide) PRE_BOOST_TOP  else PRE_BOOST_BOT
        }
        escort.moveTo(target)
    }
}