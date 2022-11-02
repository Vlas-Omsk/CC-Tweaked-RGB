/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */
package dan200.computercraft.gametest.api

import dan200.computercraft.gametest.core.ManagedComputers
import dan200.computercraft.mixin.gametest.GameTestHelperAccessor
import dan200.computercraft.mixin.gametest.GameTestSequenceAccessor
import dan200.computercraft.test.core.computer.LuaTaskContext
import net.minecraft.commands.arguments.blocks.BlockInput
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.Container
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Property
import net.minecraftforge.registries.ForgeRegistries

/**
 * Globally usable structures.
 *
 * @see GameTest.template
 */
object Structures {
    /** The "default" structure, a 5x5 area with a polished Andesite floor */
    const val DEFAULT = "default"
}

/** Pre-set in-game times */
object Times {
    const val NOON: Long = 6000
}

/**
 * Custom timeouts for various test types.
 *
 * @see GameTest.timeoutTicks
 */
object Timeouts {
    private const val SECOND: Int = 20

    const val COMPUTER_TIMEOUT: Int = SECOND * 15
}

/**
 * Equivalent to [GameTestSequence.thenExecute], but which won't run the next steps if the parent fails.
 */
fun GameTestSequence.thenExecuteFailFast(task: Runnable): GameTestSequence =
    thenExecute(task).thenWaitUntil {
        val failure = (this as GameTestSequenceAccessor).parent.error
        if (failure != null) throw failure
    }

/**
 * Wait until a computer has finished running and check it is OK.
 */
fun GameTestSequence.thenComputerOk(name: String? = null, marker: String = ComputerState.DONE): GameTestSequence {
    val label = (this as GameTestSequenceAccessor).parent.testName + (if (name == null) "" else ".$name")

    thenWaitUntil {
        val computer = ComputerState.get(label)
        if (computer == null || !computer.isDone(marker)) throw GameTestAssertException("Computer '$label' has not reached $marker yet.")
    }
    thenExecuteFailFast { ComputerState.get(label)!!.check(marker) }
    return this
}

/**
 * Run a task on a computer but don't wait for it to finish.
 */
fun GameTestSequence.thenStartComputer(name: String? = null, action: suspend LuaTaskContext.() -> Unit): GameTestSequence {
    val test = (this as GameTestSequenceAccessor).parent
    val label = test.testName + (if (name == null) "" else ".$name")
    return thenExecuteFailFast { ManagedComputers.enqueue(test, label, action) }
}

/**
 * Run a task on a computer and wait for it to finish.
 */
fun GameTestSequence.thenOnComputer(name: String? = null, action: suspend LuaTaskContext.() -> Unit): GameTestSequence {
    val test = (this as GameTestSequenceAccessor).parent
    val label = test.testName + (if (name == null) "" else ".$name")
    var monitor: ManagedComputers.Monitor? = null
    thenExecuteFailFast { monitor = ManagedComputers.enqueue(test, label, action) }
    thenWaitUntil { if (!monitor!!.isFinished) throw GameTestAssertException("Computer '$label' has not finished yet.") }
    thenExecuteFailFast { monitor!!.check() }
    return this
}

/**
 * Create a new game test sequence
 */
fun GameTestHelper.sequence(run: GameTestSequence.() -> Unit) {
    val sequence = startSequence()
    run(sequence)
    sequence.thenSucceed()
}

/**
 * A custom instance of [GameTestAssertPosException] which allows for longer error messages.
 */
private class VerboseGameTestAssertPosException(message: String, absolutePos: BlockPos, relativePos: BlockPos, tick: Long) :
    GameTestAssertPosException(message, absolutePos, relativePos, tick) {
    override fun getMessageToShowAtBlock(): String = message!!.lineSequence().first()
}

/**
 * Fail this test. Unlike [GameTestHelper.fail], this trims the in-game error message to the first line.
 */
private fun GameTestHelper.failVerbose(message: String, pos: BlockPos): Nothing {
    throw VerboseGameTestAssertPosException(message, absolutePos(pos), pos, tick)
}

/** Fail with an optional context message. */
private fun GameTestHelper.fail(message: String?, detail: String, pos: BlockPos): Nothing {
    failVerbose(if (message.isNullOrEmpty()) detail else "$message: $detail", pos)
}

/**
 * A version of [GameTestHelper.assertBlockState] which also includes the current block state.
 */
fun GameTestHelper.assertBlockIs(pos: BlockPos, predicate: (BlockState) -> Boolean, message: String = "") {
    val state = getBlockState(pos)
    if (!predicate(state)) fail(message, state.toString(), pos)
}

/**
 * A version of [GameTestHelper.assertBlockProperty] which includes the current block state in the error message.
 */
fun <T : Comparable<T>> GameTestHelper.assertBlockHas(pos: BlockPos, property: Property<T>, value: T, message: String = "") {
    val state = getBlockState(pos)
    if (!state.hasProperty(property)) {
        val id = ForgeRegistries.BLOCKS.getKey(state.block)
        fail(message, "block $id does not have property ${property.name}", pos)
    } else if (state.getValue(property) != value) {
        fail(message, "${property.name} is ${state.getValue(property)}, expected $value", pos)
    }
}

/**
 * Assert a container contains exactly these items and no more.
 *
 * @param pos The position of the container.
 * @param items The list of items this container must contain. This should be equal to the expected contents of the
 * first `n` slots - the remaining are required to be empty.
 */
fun GameTestHelper.assertContainerExactly(pos: BlockPos, items: List<ItemStack>) {
    val container = getBlockEntity(pos) ?: failVerbose("Expected a container at $pos, found nothing", pos)
    if (container !is Container) {
        failVerbose("Expected a container at $pos, found ${getName(container.type)}", pos)
    }

    val slot = (0 until container.containerSize).indexOfFirst { slot ->
        val expected = if (slot >= items.size) ItemStack.EMPTY else items[slot]
        !ItemStack.matches(container.getItem(slot), expected)
    }

    if (slot >= 0) {
        failVerbose(
            """
            Items do not match (first mismatch at slot $slot).
            Expected:  $items
            Container: ${(0 until container.containerSize).map { container.getItem(it) }.dropLastWhile { it.isEmpty }}
            """.trimIndent(),
            pos,
        )
    }
}

private fun getName(type: BlockEntityType<*>): ResourceLocation = ForgeRegistries.BLOCK_ENTITIES.getKey(type)!!

/**
 * Get a [BlockEntity] of a specific type.
 */
fun <T : BlockEntity> GameTestHelper.getBlockEntity(pos: BlockPos, type: BlockEntityType<T>): T {
    val tile = getBlockEntity(pos)
    @Suppress("UNCHECKED_CAST")
    return when {
        tile == null -> failVerbose("Expected ${getName(type)}, but no tile was there", pos)
        tile.type != type -> failVerbose("Expected ${getName(type)} but got ${getName(tile.type)}", pos)
        else -> tile as T
    }
}

/**
 * Get all entities of a specific type within the test structure.
 */
fun <T : Entity> GameTestHelper.getEntities(type: EntityType<T>): List<T> {
    val info = (this as GameTestHelperAccessor).testInfo
    return level.getEntities(type, info.structureBounds!!) { it.isAlive }
}

/**
 * Get an [Entity] inside the game structure, requiring there to be a single one.
 */
fun <T : Entity> GameTestHelper.getEntity(type: EntityType<T>): T {
    val entities = getEntities(type)
    when (entities.size) {
        0 -> throw GameTestAssertException("No $type entities")
        1 -> return entities[0]
        else -> throw GameTestAssertException("Multiple $type entities (${entities.size} in bounding box)")
    }
}

/**
 * Set a block within the test structure.
 */
fun GameTestHelper.setBlock(pos: BlockPos, state: BlockInput) = state.place(level, absolutePos(pos), 3)

/**
 * Modify a block state within the test.
 */
fun GameTestHelper.modifyBlock(pos: BlockPos, modify: (BlockState) -> BlockState) {
    setBlock(pos, modify(getBlockState(pos)))
}