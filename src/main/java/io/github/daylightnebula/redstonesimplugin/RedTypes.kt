package io.github.daylightnebula.redstonesimplugin

import org.bukkit.Material

enum class RedTypes(val cost: Int, val isSolid: Boolean, val requireSolidBelow: Boolean, val material: Material) {
    AIR(0, false, false, Material.AIR),
    REDSTONE(1, false, true, Material.REDSTONE_WIRE),
    BLOCK(1, true, false, Material.OAK_PLANKS)
}