@file:Suppress("unused")

package thedarkcolour.kotlinforforge.forge

import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.eventhandler.EventBus
import net.minecraftforge.fml.relauncher.Side
import thedarkcolour.kotlinforforge.eventbus.KotlinEventBusWrapper
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/** @since 1.0.0
 * The Forge [EventBus].
 * Many game events are fired on this bus.
 *
 * Examples:
 *   @see net.minecraftforge.event.entity.player.PlayerEvent
 *   @see net.minecraftforge.event.entity.living.LivingEvent
 *   @see net.minecraftforge.event.world.BlockEvent
 */
public val FORGE_BUS: KotlinEventBusWrapper = KotlinEventBusWrapper(MinecraftForge.EVENT_BUS as EventBus)

/** @since 1.0.0
 * The current [Side] of this environment.
 */
public val SIDE: Side = FMLCommonHandler.instance().side

/** @since 1.0.0
 *  @since 1.6.1
 * No longer an inline function to maintain side safety
 */
public fun <T> callWhenOn(dist: Side, toRun: () -> T): T? {
    return if (SIDE == dist) {
        try {
            toRun()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    } else {
        null
    }
}

/** @since 1.0.0
 * that inlines the runnable.
 *
 *  @since 1.6.1
 * No longer an inline function to maintain side safety
 */
public fun runWhenOn(dist: Side, toRun: () -> Unit) {
    if (SIDE == dist) {
        toRun()
    }
}

/** @since 1.0.0
 * that inlines the function call.
 *
 *  @since 1.6.1
 * No longer an inline function to maintain side safety
 */
public fun <T> runForDist(clientTarget: () -> T, serverTarget: () -> T): T {
    return when (SIDE) {
        Side.CLIENT -> clientTarget()
        Side.SERVER -> serverTarget()
    }
}

/** @since 1.2.2
 * Sided delegate with lazy values. This works well with proxies.
 * It is safe to use client-only types for [clientValue]
 * and server-only types for [serverValue].
 *
 * @param clientValue the value of this property on the client side.
 * @param serverValue the value of this property on the server side.
 * @param T the common type of both values. It is recommended to not use [Any] when possible.
 *
 * @see sidedDelegate if you'd like a sided value that is computed each time it is accessed
 */
public fun <T> lazySidedDelegate(clientValue: () -> T, serverValue: () -> T): ReadOnlyProperty<Any?, T> {
    return LazySidedDelegate(clientValue, serverValue)
}

/** @since 1.2.2
 * Sided delegate with values that are evaluated each time they are accessed.
 * It is safe to use client-only types for [clientValue]
 * and server-only types for [serverValue].
 *
 * @param clientValue the value of this property on the client side.
 * @param serverValue the value of this property on the server side.
 * @param T the common type of both values. It is recommended to not use [Any] when possible.
 */
public fun <T> sidedDelegate(clientValue: () -> T, serverValue: () -> T): ReadOnlyProperty<Any?, T> {
    return SidedDelegate(clientValue, serverValue)
}

/** @since 1.2.2
 * Lazy sided delegate.
 * Values are initialized lazily.
 */
private class LazySidedDelegate<T>(clientValue: () -> T, serverValue: () -> T) : ReadOnlyProperty<Any?, T> {
    private val clientValue by lazy(clientValue)
    private val serverValue by lazy(serverValue)
    
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return when (SIDE) {
            Side.CLIENT -> clientValue
            Side.SERVER -> serverValue
        }
    }
}

/** @since 1.2.2
 * Sided delegate for things like proxies,
 * or just a null checker for values that only exist on one side.
 * Values are computed each time they are accessed.
 */
private class SidedDelegate<T>(private val clientValue: () -> T, private val serverValue: () -> T) :
    ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return when (SIDE) {
            Side.CLIENT -> clientValue()
            Side.SERVER -> serverValue()
        }
    }
}
