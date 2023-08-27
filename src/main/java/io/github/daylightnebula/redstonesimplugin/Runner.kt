package io.github.daylightnebula.redstonesimplugin

import net.kyori.adventure.text.event.ClickEvent.callback
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.util.Vector
import java.util.concurrent.CompletableFuture

val vecOne = Vector(1, 1, 1)

fun clearArea(loc: Location, offset: Vector) {
    val data = Material.AIR.createBlockData()
    for (x in loc.x.toInt() .. offset.x.toInt() + 2) {
        for (y in loc.y.toInt() .. offset.y.toInt() + 2) {
            for (z in loc.z.toInt() .. offset.z.toInt() + 2) {
                world.setBlockData(x, y, z, data)
            }
        }
    }
}

fun copyTemplate(loc: Location) {
    // loop through all positions
    for (x in -1 .. size.x.toInt() + 1) {
        for (y in -1 .. size.y.toInt() + 1) {
            for (z in -1 .. size.z.toInt() + 1) {
                // get src and target locations
                val srcLoc = templateMin!!.clone().add(Vector(x, y, z)).toLocation(world)
                val targetLoc = loc.clone().add(Vector(x, y, z))

                // copy block if not template marker
                if (srcLoc.block.type == templateFiller) continue
                targetLoc.block.type = srcLoc.block.type
                targetLoc.block.blockData = srcLoc.block.blockData
            }
        }
    }
}

fun testIfValid(blocks: Array<Array<Array<RedTypes>>>): Boolean {
    var combinedCost = 0

    // make sure there is dust next to each input and output
    if (!inputOffsets.any {
        val x = it.x.coerceIn(0.0, size.x - 1.0).toInt()
        val y = it.y.coerceIn(0.0, size.y - 1.0).toInt()
        val z = it.z.coerceIn(0.0, size.z - 1.0).toInt()
        blocks[x][y][z] == RedTypes.REDSTONE
    }) return false
    if (!outputOffsets.any {
        val x = it.x.coerceIn(0.0, size.x - 1.0).toInt()
        val y = it.y.coerceIn(0.0, size.y - 1.0).toInt()
        val z = it.z.coerceIn(0.0, size.z - 1.0).toInt()
        blocks[x][y][z] == RedTypes.REDSTONE
    }) return false

    // test if each block is valid
    for (x in 0 until size.x.toInt()) {
        for (y in 0 until size.y.toInt()) {
            for (z in 0 until size.z.toInt()) {
                val type = blocks[x][y][z]

                // update combined cost
                combinedCost += type.cost
                if (bestCost != 0 && combinedCost >= bestCost) return false

                // do require solid below check
                if (type.requireSolidBelow) {
                    val pass = if (y > 0) blocks[x][y - 1][z].isSolid else false
                    if (!pass) return false
                }
            }
        }
    }

    // if we made it this far, return pass
    return true
}

fun doTestLoad(blocks: Array<Array<Array<RedTypes>>>, startLoc: Location) {
    for (x in 0 until size.x.toInt()) {
        for (y in 0 until size.y.toInt()) {
            for (z in 0 until size.z.toInt()) {
                val block = startLoc.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
                val redtype = RedTypes.values().first { it.material == block.type }
                blocks[x][y][z] = redtype
            }
        }
    }
}

fun createRunner(startLoc: Location, startIteration: Int, iterationOffset: Int, maxIterations: Int) {
    println("Creating runner $startLoc, $iterationOffset, $maxIterations")
    val blocks = Array(size.x.toInt()) { Array(size.y.toInt()) { Array(size.z.toInt()) { RedTypes.AIR } } }
    stepTypes(blocks, startIteration)
    executeRunnerIteration(blocks, startLoc, iterationOffset, maxIterations, false)
}

fun executeRunnerIteration(blocks: Array<Array<Array<RedTypes>>>, startLoc: Location, iterationOffset: Int, maxIterations: Int, performStep: Boolean): CompletableFuture<Boolean> {
    val future = CompletableFuture<Boolean>()

    Bukkit.getScheduler().runTaskLater(plugin, { task ->
        // clear area
        clearArea(startLoc, size)

        // copy template
        copyTemplate(startLoc)

        // step forward array
        if (performStep) {
            do {
                stepTypes(blocks, iterationOffset)
            } while(!testIfValid(blocks))
        }

        // copy in types
        copyTypes(startLoc, blocks)

        // execute test
        runTestIteration(startLoc, 0).whenComplete { success, _ ->
            if (success) future.complete(true)
            else {
                executeRunnerIteration(blocks, startLoc, iterationOffset, maxIterations, true).whenComplete { a, _ ->
                    future.complete(a)
                }
            }
        }
    }, 1L)

    return future
}

fun copyTypes(startLoc: Location, blocks: Array<Array<Array<RedTypes>>>) {
    for (x in 0 until size.x.toInt()) {
        for (y in 0 until size.y.toInt()) {
            for (z in 0 until size.z.toInt()) {
                val loc = startLoc.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                val mat = blocks[x][y][z].material
                loc.block.blockData = mat.createBlockData()
                loc.block.type = mat
            }
        }
    }
}

fun stepTypes(blocks: Array<Array<Array<RedTypes>>>, step: Int) {
    var nextStep = step
    var index = 0
    do {
        val uncombinedIndex = toUncombined(index)
        val curTypeID = blocks[uncombinedIndex.first][uncombinedIndex.second][uncombinedIndex.third].ordinal
        val newID = (curTypeID + nextStep) % RedTypes.values().size
        blocks[uncombinedIndex.first][uncombinedIndex.second][uncombinedIndex.third] = RedTypes.values()[newID]
        nextStep = (curTypeID + nextStep) / RedTypes.values().size
        index++
    } while (nextStep != 0)
}

fun toCombined(x: Int, y: Int, z: Int) = (z * (size.y * size.x).toInt()) + (y * size.x.toInt()) + x
fun toUncombined(index: Int): Triple<Int, Int, Int> {
    val z = index / (size.x * size.y).toInt()
    val y = (index - (z * size.x * size.y).toInt()) / size.x.toInt()
    val x = (index - (z * size.x * size.y).toInt()) - (y * size.x).toInt()
    return Triple(x, y, z)
}

fun runTestIteration(startLoc: Location, index: Int): CompletableFuture<Boolean> {
    val future = CompletableFuture<Boolean>()

    // if index out of bounds, return true
    if (index >= truthTable.size) future.complete(true)
    // otherwise run test
    else {
        val table = truthTable[index]
        executeTest(startLoc, table).whenComplete { success, _ ->
            if (success) {
                runTestIteration(startLoc, index + 1)
                    .whenComplete { success, _ -> future.complete(success) }
            } else future.complete(false)
        }
    }

    return future
}

val numTicks = 3
fun executeTest(startLoc: Location, table: Pair<List<Boolean>, List<Boolean>>): CompletableFuture<Boolean> {
    val future = CompletableFuture<Boolean>()
    var ticker = 0

    // update inputs
    inputOffsets.forEachIndexed { index, vector ->
        val location = vector.toLocation(world).add(startLoc)
        location.block.type = if (table.first[index]) Material.REDSTONE_BLOCK else Material.BLACK_WOOL
    }

    Bukkit.getScheduler().runTaskTimer(plugin, { task ->
        // do continue checks
        if (!continueTableTest(startLoc, table)) {
            future.complete(false)
            task.cancel()
        }

        // update ticker and handle complete
        ticker++
        if (ticker > numTicks) {
            future.complete(matchesTruthTable(startLoc, table))
            task.cancel()
        }
    }, 3L, 1L)

    return future
}

fun matchesTruthTable(startLoc: Location, table: Pair<List<Boolean>, List<Boolean>>): Boolean {
    return outputOffsets
        .mapIndexed { index, vector -> index to vector }
        .all { (index, vector) ->
            val location = vector.toLocation(world).add(startLoc)
            (location.block.blockPower > 0) == table.second[index]
        }
}

fun continueTableTest(startLoc: Location, table: Pair<List<Boolean>, List<Boolean>>): Boolean {
    return outputOffsets
        .mapIndexed { index, vector -> index to vector }
        .all { (index, vector) ->
            val location = vector.toLocation(world).add(startLoc)
            if (location.block.blockPower > 0) table.second[index] else true
        }
}
