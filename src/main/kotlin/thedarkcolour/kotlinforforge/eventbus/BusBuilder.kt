package thedarkcolour.kotlinforforge.eventbus

import net.minecraftforge.fml.common.eventhandler.Event
import net.minecraftforge.fml.common.eventhandler.EventBus
import net.minecraftforge.fml.common.eventhandler.IEventExceptionHandler

@Suppress("unused")
public class BusBuilder {
    public var exceptionHandler: IEventExceptionHandler? = null
    
    public var startShutdown: Boolean = false
    public var markerType: Class<*> = Event::class.java
        set(value) {
            require(value.isInterface) { "Cannot specify a class marker type" }
            field = value
        }
    
    public fun build(): EventBus {
        return EventBus(exceptionHandler!!)
    }
}