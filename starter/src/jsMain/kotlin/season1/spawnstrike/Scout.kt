package season1.spawnstrike

import screeps.api.Creep

object Scout {

    private val BOTTOM_POS = pos(96, 63)
    private val TOP_POS    = pos(3, 36)

    fun execute(creep: Creep, gameplay: Gameplay) {
        val target = if (gameplay.isInTop()) TOP_POS else BOTTOM_POS
        if (creep.getRangeTo(target) > 0) creep.moveTo(target)
        // Ha megérkezett, nem csinál semmit
    }
}