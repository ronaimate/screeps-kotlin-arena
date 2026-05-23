package season3.escortrun.economy

import screeps.api.*
import season3.escortrun.Gameplay
import season3.escortrun.pos

/**
 * CARRY creep viselkedés.
 *
 * **Boost fázis** (boostedEconomyBuilt == false):
 *   H1 és H2 húzzák W2-vel együtt. Amikor H1 eléri (5,5)/(5,94)-et,
 *   megvárja hogy a CARRY lespawnolódjon, majd H1 húzza CARRY-t amíg
 *   el nem éri (6,6)/(6,93)-at (2 tick), aztán elengedi.
 *   H1+H2 visszamennek a spawnhoz "átugorva" CARRY-t →
 *   CARRY automatikusan a (4,4)/(4,95) pozícióba kerül.
 *
 * **Normál fázis** (boostedEconomyBuilt == true):
 *   Fix relay pozíción áll: top (4,4) / bot (4,95).
 *   W2 adja át az energiát ide, CARRY átadja H1-nek (aki (5,5)/(5,94)-en áll).
 *   - Üres → (4,4)/(4,95)-re megy és vár
 *   - Teli → H1-hez megy (H1 (5,5)/(5,94)-en van) és átad
 */
object CarryBehavior {

    private val CARRY_POS_TOP  = pos(4, 4)
    private val CARRY_POS_BOT  = pos(4, 95)
    private val H1_PICKUP_TOP  = pos(5, 5)
    private val H1_PICKUP_BOT  = pos(5, 94)

    fun execute(creep: Creep, gameplay: Gameplay) {
        if (WorkerBehavior.boostedEconomyBuilt && !HarvesterBehavior.chainPositioningDone) {
            // boostedEconomyBuilt true, de H2/C1 helycseréje még nem történt meg
            executeSwapWait(creep, gameplay)
        } else if (WorkerBehavior.boostedEconomyBuilt) {
            executeNormal(creep, gameplay)
        } else {
            executeBoostWait(creep, gameplay)
        }
    }

    /**
     * Boost fázisban W2 húzza CARRY-t sorban: spawn → 7,7 → 6,6 (top) / 7,92 → 6,93 (bot).
     * CARRY a következő fix állomás felé megy, W2 pull-olja.
     */
    private fun executeBoostWait(creep: Creep, gameplay: Gameplay) {
        val stop1    = if (gameplay.isTopSide) pos(7, 7)  else pos(7, 92)
        val stop2    = if (gameplay.isTopSide) pos(6, 6)  else pos(6, 93)
        val relayPos = if (gameplay.isTopSide) CARRY_POS_TOP else CARRY_POS_BOT

        // Ha H2 közvetlenül mellettünk van (step1-en) és pull-ol → C1 mozog H2 felé (helycseréhez)
        val h2 = EnergyChain.getSecondaryHarvester(gameplay)
        val h2Step1 = if (gameplay.isTopSide) pos(5, 5) else pos(5, 94)
        val h2AtStep1 = h2 != null && h2.x == h2Step1.x && h2.y == h2Step1.y
        // H2 szomszédos és mi stop2-n vagyunk → helycseréhez C1 mozdul H2 felé
        val h2Adjacent = h2 != null && creep.getRangeTo(h2) <= 1
        if (h2Adjacent && creep.x == stop2.x && creep.y == stop2.y) {
            creep.moveTo(h2)
            return
        }

        when {
            creep.x == relayPos.x && creep.y == relayPos.y -> { /* kész */ }
            creep.x == stop2.x    && creep.y == stop2.y    -> creep.moveTo(relayPos)
            creep.x == stop1.x    && creep.y == stop1.y    -> creep.moveTo(stop2)
            else                                            -> creep.moveTo(stop1)
        }
    }

    /**
     * Helycserés várakozás: boostedEconomyBuilt true, de chainPositioningDone még false.
     * C1 a (6,93)/(6,6) pozícióban van és H2 pullolja → C1 mozdul H2 felé.
     */
    private fun executeSwapWait(creep: Creep, gameplay: Gameplay) {
        val h1 = EnergyChain.getPrimaryHarvester(gameplay)
        val h2 = EnergyChain.getSecondaryHarvester(gameplay)
        // H2 csere: C1 (6,6)/(6,93)-on, H2 szomszéd → mozdul H2 felé
        val h2SwapPos = if (gameplay.isTopSide) pos(6, 6) else pos(6, 93)
        val h2Adjacent = h2 != null && creep.getRangeTo(h2) <= 1
        // H1 csere: C1 (5,5)/(5,94)-on, H1 szomszéd → mozdul H1 felé
        val h1SwapPos = if (gameplay.isTopSide) pos(5, 5) else pos(5, 94)
        val h1Adjacent = h1 != null && creep.getRangeTo(h1) <= 1
        when {
            h2Adjacent && creep.x == h2SwapPos.x && creep.y == h2SwapPos.y -> {
                creep.moveTo(h2)
            }
            h1Adjacent && creep.x == h1SwapPos.x && creep.y == h1SwapPos.y -> {
                creep.moveTo(h1)
            }
            // egyébként vár
        }
    }

    /**
     * Normál mód: (4,4)/(4,95) ↔ H1 pickup pozíció között ingázik.
     * W2-től veszi át, H1-nek adja.
     */
    private fun executeNormal(creep: Creep, gameplay: Gameplay) {
        val h1       = EnergyChain.getPrimaryHarvester(gameplay)
        val relayPos = if (gameplay.isTopSide) CARRY_POS_TOP else CARRY_POS_BOT
        val h1Pos    = if (gameplay.isTopSide) H1_PICKUP_TOP else H1_PICKUP_BOT

        if (creep.store.getUsedCapacity(RESOURCE_ENERGY) == 0) {
            // Üres → relay pozícióba, W2 oda adja át
            if (creep.x != relayPos.x || creep.y != relayPos.y) creep.moveTo(relayPos)
            // Helyes pozícióban → W2 oldalán van a transfer, várunk
        } else {
            // Teli → H1 pickup pozíciójára megy és átadja H1-nek
            if (h1 != null) {
                if (creep.getRangeTo(h1) <= 1) creep.transfer(h1, RESOURCE_ENERGY)
                else creep.moveTo(h1Pos)
            }
        }
    }
}