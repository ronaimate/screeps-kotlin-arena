package season3.escortrun.economy

import screeps.api.*
import season3.escortrun.Gameplay

/**
 * Worker (W1 / W2) viselkedés.
 *
 * **Boost mód** (boostedEconomyBuilt == false, boostEnabled == true):
 *   A Harvesterek behúzzák a Workereket a forrás mellé (pull-chain).
 *   A boostedEconomyBuilt flag EGYSZER vált true-ra és soha nem reset-el:
 *   trigger: H1 a worker2Target pozícióján van ÉS W2 követi W1-et → kész.
 *
 * **Normál mód** (boostedEconomyBuilt == true VAGY boostEnabled == false):
 *   - W1: bányász a forrásból, átadja H1-nek
 *   - W2: bányász a forrásból, átadja H1-nek
 *
 * **Opcionális boost** (boostEnabled):
 *   Ha false (pánik mód), boost fázis kihagyva, azonnal normál mód.
 *
 * KRITIKUS: a boostedEconomyBuilt egy egyszeri, visszavonhatatlan flag –
 * csak a W2 execute-ban állítódik true-ra, pozíció-számítással soha nem reset-elhető.
 */
object WorkerBehavior {

    // Egyszeri, visszavonhatatlan flag – pontosan az eredetivel megegyező szemantika.
    // Csak itt írható, kívülről olvasható.
    var boostedEconomyBuilt: Boolean = false
        private set

    fun execute(creep: Creep, gameplay: Gameplay, boostEnabled: Boolean) {
        val w1   = EnergyChain.getPrimaryWorker(gameplay)
        val isW1 = creep.id == w1?.id

        if (isW1) executeW1(creep, gameplay, boostEnabled)
        else      executeW2(creep, gameplay, boostEnabled)
    }

    // ── W1 ────────────────────────────────────────────────────────────────────

    private fun executeW1(creep: Creep, gameplay: Gameplay, boostEnabled: Boolean) {
        val w2        = EnergyChain.getSecondaryWorker(gameplay)
        val h1        = EnergyChain.getPrimaryHarvester(gameplay)
        val source    = gameplay.mySource
        val positions = EscortPositions.get(gameplay.mySpawn.y)

        if (boostEnabled && !boostedEconomyBuilt) {
            // Pull-chain fázis: W1 nincs helyén VAGY H1 már a w2Target-en van (utolsó ugrás)
            val h1AtW2Pos = h1 != null &&
                    h1.x == positions.worker2Target.x &&
                    h1.y == positions.worker2Target.y

            if (!EnergyChain.isWorker1InPlace(gameplay) || h1AtW2Pos) {
                if (w2 != null) creep.pull(w2)
                if (h1 != null) creep.moveTo(h1)
                return
            }

            // W1 helyén van, H1 nincs még a w2Targeten → ingázás pre-boost módban
            // W1 bányász, átad H1-nek (1:1 lánc W2 nélkül)
            if (creep.getRangeTo(source) <= 1) creep.harvest(source)
            if (creep.store.getUsedCapacity(RESOURCE_ENERGY) > 0 && h1 != null && creep.getRangeTo(h1) <= 1) {
                creep.transfer(h1, RESOURCE_ENERGY)
            }
            return
        }

        // Normál mód (boost kész vagy pánik): bányász + átad W2-nek
        if (creep.getRangeTo(source) <= 1) creep.harvest(source)
        if (w2 != null && creep.store.getUsedCapacity(RESOURCE_ENERGY) > 0 && creep.getRangeTo(w2) <= 1) {
            creep.transfer(w2, RESOURCE_ENERGY)
        }
    }

    // ── W2 ────────────────────────────────────────────────────────────────────

    private fun executeW2(creep: Creep, gameplay: Gameplay, boostEnabled: Boolean) {
        val w1        = EnergyChain.getPrimaryWorker(gameplay)
        val h1        = EnergyChain.getPrimaryHarvester(gameplay)
        val h2        = EnergyChain.getSecondaryHarvester(gameplay)
        val c        = EnergyChain.getCarry(gameplay)
        val source    = gameplay.mySource
        val positions = EscortPositions.get(gameplay.mySpawn.y)

        if (boostEnabled && !boostedEconomyBuilt) {
            // TRIGGER: H1 elérte a worker2Target pozíciót → boost kész, W2 követi W1-et
            val h1AtW2Pos = h1 != null &&
                    h1.x == positions.worker2Target.x &&
                    h1.y == positions.worker2Target.y

            if (h1AtW2Pos) {
                if (w1 != null) creep.moveTo(w1)
                boostedEconomyBuilt = true   // ← egyszer sül el, soha nem reset-el
                return
            }

            // W2 még nem helyén → kövesd H2-t (H2 húz)
            if (!creep.spawning && !EnergyChain.isWorker2InPlace(gameplay)) {
                if (h2 != null) creep.moveTo(h2)
                c?.let {
                    creep.pull(it)
                }
                return
            }
        }

        // Normál mód: bányász + átad CARRY-nak (ha van), különben H1-nek
        val carry = EnergyChain.getCarry(gameplay)
        val transferTarget = carry ?: h1
        if (creep.getRangeTo(source) <= 1) creep.harvest(source)
        if (creep.store.getUsedCapacity(RESOURCE_ENERGY) > 0 && transferTarget != null && creep.getRangeTo(transferTarget) <= 1) {
            creep.transfer(transferTarget, RESOURCE_ENERGY)
        }
    }
}