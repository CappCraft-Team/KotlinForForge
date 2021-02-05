package thedarkcolour.kotlinforforge.eventbus

import net.minecraftforge.fml.common.eventhandler.Event
import net.minecraftforge.fml.common.eventhandler.EventBus
import net.minecraftforge.fml.common.eventhandler.IEventExceptionHandler
import net.minecraftforge.fml.common.eventhandler.IEventListener
import java.util.concurrent.ConcurrentHashMap

public class KotlinEventBusWrapper(private val parent: EventBus) : KotlinEventBus(BusBuilder().apply {
    exceptionHandler = getExceptionHandler(parent)
    startShutdown = getShutdown(parent)
}
) {
    override val busID: Int = getBusID(parent)
    override val listeners: ConcurrentHashMap<Any, MutableList<IEventListener>> = getListeners(parent)
    
    override fun post(event: Event): Boolean {
        return parent.post(event)
    }
    
    // reflection stuff
    private companion object {
        private val GET_BUS_ID = EventBus::class.java.getDeclaredField("busID").also { it.isAccessible = true }
        private val GET_LISTENERS = EventBus::class.java.getDeclaredField("listeners").also { it.isAccessible = true }
        private val GET_EXCEPTION_HANDLER =
            EventBus::class.java.getDeclaredField("exceptionHandler").also { it.isAccessible = true }
        private val GET_SHUTDOWN = EventBus::class.java.getDeclaredField("shutdown").also { it.isAccessible = true }
        
        private fun getBusID(eventBus: EventBus): Int {
            return GET_BUS_ID[eventBus] as Int
        }
        
        @Suppress("UNCHECKED_CAST")
        private fun getListeners(eventBus: EventBus): ConcurrentHashMap<Any, MutableList<IEventListener>> {
            return GET_LISTENERS[eventBus] as ConcurrentHashMap<Any, MutableList<IEventListener>>
        }
        
        private fun getExceptionHandler(eventBus: EventBus): IEventExceptionHandler {
            return GET_EXCEPTION_HANDLER[eventBus] as IEventExceptionHandler
        }
        
        private fun getShutdown(eventBus: EventBus): Boolean {
            return GET_SHUTDOWN[eventBus] as Boolean
        }
    }
}