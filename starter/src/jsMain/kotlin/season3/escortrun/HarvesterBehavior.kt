package season3.escortrun.economy

import screeps.api.*
import season3.escortrun.Gameplay
import season3.escortrun.pos

/**
 * Harvester (H1 / H2) viselkedés.
 *
 * **Boost mód** (!WorkerBehavior.boostedEconomyBuilt && boostEnabled):
 *   - H1 húzza W1-et a forrás mellé, majd H1+H2 együtt húzza W2-t a helyére
 *   - H2 segít W2 elhelyezésében
 *
 * **Normál mód** (WorkerBehavior.boostedEconomyBuilt || !boostEnabled):
 *
 *   H1 fix állomásokon ingázik (top):
 *     (4,4) → (5,5) → (6,6) → átadja H2-nek ha range ≤ 1 → vissza (4,4)
 *   H1 fix állomásokon ingázik (bot):
 *     (4,95) → (5,94) → (6,93) → átadja H2-nek ha range ≤ 1 → vissza
 *
 *   H2 két állomás között ugrál (top): (7,7) ↔ (8,8)
 *   H2 két állomás között ugrál (bot): (7,92) ↔ (8,91)
 *   Ha H1 range ≤ 1 → átveszi az energiát, majd a spawn felé adja át a lánc végén.
 *   H2 teli → spawn felé megy és átadja.
 */
object HarvesterBehavior {

    // Egyszeri flag: a boost utáni első visszaút pozicionálása megtörtént-e
    // (H2: spawn → 5,5 → 6,6 / H1: spawn → 4,4 → 5,5)
    var chainPositioningDone: Boolean = false
        internal set

    // H2 már elvégezte a saját cseréjét → nem kell visszamennie step2-re
    private var h2SwapDone: Boolean = false

    fun execute(creep: Creep, gameplay: Gameplay, boostEnabled: Boolean) {
        val h1 = EnergyChain.getPrimaryHarvester(gameplay)
        val isH1 = creep.id == h1?.id

        if (isH1) executeH1(creep, gameplay, boostEnabled)
        else executeH2(creep, gameplay, boostEnabled)
    }

    // ── H1 ────────────────────────────────────────────────────────────────────

    private fun executeH1(creep: Creep, gameplay: Gameplay, boostEnabled: Boolean) {
        val boostedEconomyBuilt = WorkerBehavior.boostedEconomyBuilt

        val w1 = EnergyChain.getPrimaryWorker(gameplay) ?: return
        val w2 = EnergyChain.getSecondaryWorker(gameplay)
        val h2 = EnergyChain.getSecondaryHarvester(gameplay)
        val carry = EnergyChain.getCarry(gameplay)
        val positions = EscortPositions.get(gameplay.mySpawn.y)

        if (boostEnabled && !boostedEconomyBuilt) {
            // W1 még spawol → várakozás
            if (w1.spawning) {
                creep.moveTo(positions.waitingForWorker1)
                return
            }
            // W2 még spawol → relay W1-gyel közben
            if (w2?.spawning == true) {
                executeNormalRelayH1(creep, gameplay, h2, carry)
                return
            }
            // W1 nincs helyén → húzd oda
            if (!EnergyChain.isWorker1InPlace(gameplay)) {
                val w1Target = positions.worker1Target
                val jumpPos = positions.harvester1JumpForW1
                creep.pull(w1)
                if (creep.x == w1Target.x && creep.y == w1Target.y) creep.moveTo(jumpPos)
                else creep.moveTo(w1Target)
                return
            }
            // W2 létezik, nincs helyén → H1 segít H2-vel W2 húzásában
            if (w2 != null && !EnergyChain.isWorker2InPlace(gameplay)) {
                val w2Target = positions.worker2Target
                val midPos = if (gameplay.isTopSide) pos(5, 5) else pos(5, 94)

                // Ha H1 a midPos-on van → várjuk hogy CARRY lespawnolódjon
                val h1AtMid = creep.x == midPos.x && creep.y == midPos.y
                if (h1AtMid && (carry == null || carry.spawning)) {
                    return
                }

                if (creep.getRangeTo(h2!!) > 1) {
                    creep.moveTo(positions.harvester1WaitingForWorker2)
                } else {
                    creep.pull(h2)
                    if (creep.x == w2Target.x && creep.y == w2Target.y) {
                        creep.pull(w1)
                        creep.moveTo(h2)
                    } else {
                        creep.moveTo(w2Target)
                    }
                }
                if (creep.x == w2Target.x && creep.y == w2Target.y) {
                    creep.pull(w1)
                    creep.moveTo(h2)
                }
                return
            }
        }

        // Boost utáni egyszeri pozicionálás: H1 megy (4,4) → (5,5)
        // H2 már (6,6)/(6,93)-on van, C1 (5,5)/(5,94)-en → H1 pullozza C1-et és megy (5,5)-re
        if ((boostedEconomyBuilt || EnergyChain.isWorker2InPlace(gameplay)) && !chainPositioningDone) {
            val h2StepTwo = if (gameplay.isTopSide) pos(6, 6) else pos(6, 93)
            val h2AtFinal = h2 != null && h2.x == h2StepTwo.x && h2.y == h2StepTwo.y
            if (!h2AtFinal) return  // H2 még nem ért oda, H1 vár

            val step1 = if (gameplay.isTopSide) pos(4, 4) else pos(4, 95)
            val step2 = if (gameplay.isTopSide) pos(5, 5) else pos(5, 94)
            when {
                creep.x == step2.x && creep.y == step2.y -> {
                    chainPositioningDone = true
                }
                creep.x == step1.x && creep.y == step1.y -> {
                    // C1 (5,5)/(5,94)-en áll → pullozzuk és menjünk oda (helycserés módszer)
                    if (carry != null) creep.pull(carry)
                    creep.moveTo(step2)
                }
                else -> {
                    creep.moveTo(step1)
                }
            }
            return
        }

        executeNormalRelayH1(creep, gameplay, h2, carry)
    }

    /**
     * H1 normál relay: CARRY-tól veszi át az energiát (5,5 / 5,94), majd H2-nek adja.
     * Ha nincs CARRY még → W1 közeléből (4,4 / 4,95) vesz.
     */
    private fun executeNormalRelayH1(creep: Creep, gameplay: Gameplay, h2: Creep?, carry: Creep?) {
        if (chainPositioningDone) {
            // C1 a helyén → H1 fix relay: (5,5)/(5,94) vesz, (6,6)/(6,93) ad H2-nek
            val pickupPos   = if (gameplay.isTopSide) pos(5, 5) else pos(5, 94)
            val transferPos = if (gameplay.isTopSide) pos(6, 6) else pos(6, 93)
            if (creep.store.getUsedCapacity(RESOURCE_ENERGY) == 0) {
                if (creep.x != pickupPos.x || creep.y != pickupPos.y) creep.moveTo(pickupPos)
            } else {
                if (creep.x != transferPos.x || creep.y != transferPos.y) creep.moveTo(transferPos)
                else if (h2 != null && creep.getRangeTo(h2) <= 1) creep.transfer(h2, RESOURCE_ENERGY)
            }
            return
        }

        // C1 még nem a helyén → eredeti relay
        val pickupPos = if (carry != null) {
            if (gameplay.isTopSide) pos(5, 5) else pos(5, 94)
        } else {
            if (gameplay.isTopSide) pos(4, 4) else pos(4, 95)
        }
        if (creep.store.getUsedCapacity(RESOURCE_ENERGY) == 0) {
            if (creep.x != pickupPos.x || creep.y != pickupPos.y) creep.moveTo(pickupPos)
        } else {
            if (h2 != null) {
                if (creep.getRangeTo(h2) <= 1) creep.transfer(h2, RESOURCE_ENERGY)
                else creep.moveTo(h2)
            } else {
                val spawn = gameplay.mySpawn
                if (creep.getRangeTo(spawn) <= 1) creep.transfer(spawn, RESOURCE_ENERGY)
                else creep.moveTo(spawn)
            }
        }
    }

    // ── H2 ────────────────────────────────────────────────────────────────────

    private fun executeH2(creep: Creep, gameplay: Gameplay, boostEnabled: Boolean) {
        val boostedEconomyBuilt = WorkerBehavior.boostedEconomyBuilt

        val h1 = EnergyChain.getPrimaryHarvester(gameplay) ?: return
        val w2 = EnergyChain.getSecondaryWorker(gameplay)
        val positions = EscortPositions.get(gameplay.mySpawn.y)
        val spawn = gameplay.mySpawn


        if (boostEnabled && !boostedEconomyBuilt) {
            // W2 még nincs helyén → H2 húzza W2-t
            if (w2 != null && !w2.spawning && !EnergyChain.isWorker2InPlace(gameplay)) {
                val approachPos = positions.harvester1JumpForW2
                if (creep.getRangeTo(w2) > 1) {
                    creep.moveTo(w2)
                } else {
                    creep.pull(w2)
                    if (creep.x == approachPos.x && creep.y == approachPos.y) {
                        creep.pull(h1)
                        creep.moveTo(w2)
                    } else {
                        creep.moveTo(h1)
                    }
                }
                return
            }
        }

        // Boost utáni egyszeri pozicionálás: H2 megy (5,94)→(6,93) / (5,5)→(6,6)
        // Ha C1 blokkolja step2-t → H2 pullolja és megy oda → helyet cserélnek
        // Trigger: W2 már a helyén van (boostedEconomyBuilt még ugyanabban a tickben false lehet)
        val w2InPlace = EnergyChain.isWorker2InPlace(gameplay)
        if ((boostedEconomyBuilt || w2InPlace) && !chainPositioningDone && !h2SwapDone) {
            val step1 = if (gameplay.isTopSide) pos(5, 5)  else pos(5, 94)
            val step2 = if (gameplay.isTopSide) pos(6, 6)  else pos(6, 93)
            val carry = EnergyChain.getCarry(gameplay)
            when {
                creep.x == step2.x && creep.y == step2.y -> {
                    val carryGone = carry == null || !(carry.x == step2.x && carry.y == step2.y)
                    if (carryGone) {
                        h2SwapDone = true
                        executeNormalRelayH2(creep, gameplay, h1, spawn)
                    }
                }
                creep.x == step1.x && creep.y == step1.y -> {
                    if (carry != null) creep.pull(carry)
                    creep.moveTo(step2)
                }
                else -> {
                    creep.moveTo(step1)
                }
            }
            return
        }

        // ── Normál relay ingázás ──────────────────────────────────────────────
        executeNormalRelayH2(creep, gameplay, h1, spawn)
    }

    private fun executeNormalRelayH2(creep: Creep, gameplay: Gameplay, h1: Creep, spawn: screeps.api.structures.StructureSpawn) {
        if (chainPositioningDone) {
            // C1 a helyén → H2 fix relay: (7,7)/(7,92) vesz H1-től, (8,8)/(8,91) ad spawnnak
            val pickupPos   = if (gameplay.isTopSide) pos(7, 7) else pos(7, 92)
            val transferPos = if (gameplay.isTopSide) pos(8, 8) else pos(8, 91)
            if (creep.store.getUsedCapacity(RESOURCE_ENERGY) == 0) {
                if (creep.x != pickupPos.x || creep.y != pickupPos.y) creep.moveTo(pickupPos)
            } else {
                if (creep.x != transferPos.x || creep.y != transferPos.y) creep.moveTo(transferPos)
                else if (creep.getRangeTo(spawn) <= 1) creep.transfer(spawn, RESOURCE_ENERGY)
            }
            return
        }

        // C1 még nem a helyén → eredeti relay: H1-hez megy, teli → spawn
        val h1RelayPos = if (gameplay.isTopSide) pos(5, 5) else pos(5, 94)
        if (creep.store.getUsedCapacity(RESOURCE_ENERGY) == 0) {
            if (creep.getRangeTo(h1) <= 1) {
                // H1 átad – várunk
            } else {
                creep.moveTo(h1RelayPos)
            }
        } else {
            if (creep.getRangeTo(spawn) <= 1) creep.transfer(spawn, RESOURCE_ENERGY)
            else creep.moveTo(spawn)
        }
    }
}