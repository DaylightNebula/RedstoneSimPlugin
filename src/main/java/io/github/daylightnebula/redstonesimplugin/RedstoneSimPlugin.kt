package io.github.daylightnebula.redstonesimplugin

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import org.checkerframework.checker.units.qual.min

val minY = -50.0
val size = Vector(0, 0,0)
val world: World
    get() = Bukkit.getWorlds().first()

var bestCost = 0
var maxIterations = 0
var bestSolution: Array<Array<Array<RedTypes>>>? = null

var templateMin: Vector? = null
var templateMax: Vector? = null
val inputOffsets = mutableListOf<Vector>()
val outputOffsets = mutableListOf<Vector>()

val inputMat = Material.SPONGE
val outputMat = Material.REDSTONE_LAMP
val templateFiller = Material.GREEN_STAINED_GLASS

val truthTable = listOf(
    listOf(false, false) to listOf(false),
    listOf(false, true) to listOf(true),
    listOf(true, false) to listOf(true),
    listOf(true, true) to listOf(true),
)

lateinit var plugin: JavaPlugin

class RedstoneSimPlugin : JavaPlugin() {
    override fun onLoad() {
        plugin = this
    }

    override fun onEnable() {
        // create setup command
        getCommand("setup")?.setExecutor(object : CommandExecutor {
            override fun onCommand(
                sender: CommandSender, command: Command,
                label: String, args: Array<out String>?
            ): Boolean {
                val player = sender as? Player ?: return true

                // send args
                if (args == null || args.size != 3 || args.any { it.toIntOrNull() == null }) {
                    sender.sendMessage("/setup <width> <height> <depth>")
                    return true
                }

                // read dimensions
                size.x = args[0].toDouble()
                size.y = args[1].toDouble()
                size.z = args[2].toDouble()
                maxIterations = (size.x * size.y * size.z * RedTypes.values().size).toInt()

                // make sure y is in range
                if (size.y + minY > 255) {
                    sender.sendMessage("Setup invalid do to Y out of range")
                    return true
                }

                // create template
                templateMin = Vector(-1.0 - size.x, minY + 1.0, -1.0 - size.z)
                templateMax = Vector(-2.0, minY + size.y.toInt(), -2.0)
                val data = templateFiller.createBlockData()
                world.setBlockData(templateMin!!.x.toInt(), templateMin!!.y.toInt(), templateMin!!.z.toInt(), data)
                world.setBlockData(templateMin!!.x.toInt(), templateMin!!.y.toInt(), templateMax!!.z.toInt(), data)
                world.setBlockData(templateMin!!.x.toInt(), templateMax!!.y.toInt(), templateMin!!.z.toInt(), data)
                world.setBlockData(templateMin!!.x.toInt(), templateMax!!.y.toInt(), templateMax!!.z.toInt(), data)
                world.setBlockData(templateMax!!.x.toInt(), templateMin!!.y.toInt(), templateMin!!.z.toInt(), data)
                world.setBlockData(templateMax!!.x.toInt(), templateMin!!.y.toInt(), templateMax!!.z.toInt(), data)
                world.setBlockData(templateMax!!.x.toInt(), templateMax!!.y.toInt(), templateMax!!.z.toInt(), data)
                world.setBlockData(templateMax!!.x.toInt(), templateMax!!.y.toInt(), templateMin!!.z.toInt(), data)

                // set inventory
                player.inventory.clear()
                player.inventory.setItem(0, ItemStack(inputMat))
                player.inventory.setItem(1, ItemStack(outputMat))

                return true
            }
        })

        // create run command
        getCommand("run")?.setExecutor(object : CommandExecutor {
            override fun onCommand(
                sender: CommandSender, command: Command,
                label: String, args: Array<out String>?
            ): Boolean {
                // make sure args
                if (args == null || args.size != 1 || args.any { it.toIntOrNull() == null }) {
                    sender.sendMessage("/run <number of runners, this number will be tripled>")
                    return true
                }
                bestCost = 0

                if (templateMin == null || templateMax == null) return true

                // get number of runners
                val runnerBlockWidth = args.first().toInt()

                // scan for offset locations
                inputOffsets.clear()
                outputOffsets.clear()
                for (x in -1 .. size.x.toInt() + 1) {
                    for (y in -1 .. size.y.toInt() + 1) {
                        for (z in -1 .. size.z.toInt() + 1) {
                            val loc = templateMin!!.clone().add(Vector(x, y, z)).toLocation(world)
                            when(loc.block.type) {
                                inputMat -> inputOffsets.add(Vector(x, y, z))
                                outputMat -> outputOffsets.add(Vector(x, y, z))
                                else -> {}
                            }
                        }
                    }
                }

                // create runners
                val runnerOffset = size.x.coerceAtLeast(size.y).coerceAtLeast(size.z) + 5
                var runTracker = 0
                for (runX in 0 until runnerBlockWidth) {
                    for (runY in 0 until runnerBlockWidth) {
                        for (runZ in 0 until runnerBlockWidth) {
                            val runLoc = Location(world, runX * runnerOffset, runY * runnerOffset + minY, runZ * runnerOffset)
                            createRunner(runLoc, runTracker, runnerBlockWidth, maxIterations)
                            runTracker++
                        }
                    }
                }

                return true
            }
        })
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
