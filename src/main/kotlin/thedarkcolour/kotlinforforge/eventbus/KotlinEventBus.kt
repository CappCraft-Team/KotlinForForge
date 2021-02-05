package thedarkcolour.kotlinforforge.eventbus

import net.jodah.typetools.TypeResolver
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.eventhandler.*
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.MarkerManager
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

/** @since 1.2.0
 * Fixes [addListener] and [addGenericListener] for Kotlin KCallable.
 *
 * @param builder The BusBuilder used to configure this event bus
 * @param synthetic Whether this event bus is just a wrapper for another bus
 */
@Suppress("LeakingThis", "MemberVisibilityCanBePrivate", "unused")
public open class KotlinEventBus(builder: BusBuilder, synthetic: Boolean = false) : EventBus(),
    IEventExceptionHandler {
    
    private val exceptionHandler = builder.exceptionHandler ?: this
    
    @Volatile
    private var shutdown = builder.startShutdown
    protected open val busID: Int = MAX_ID++
    protected open val listeners: ConcurrentHashMap<Any, MutableList<IEventListener>> = ConcurrentHashMap()
    
    
    init {
        // see companion object
        if (!synthetic) {
            RESIZE_LISTENER_LIST(busID + 1)
        }
    }
    
    override fun register(target: Any) {
        if (!listeners.containsKey(target)) {
            if (target.javaClass == Class::class.java) {
                registerClass(target as Class<*>)
            } else {
                registerObject(target)
            }
        }
    }
    
    private fun registerClass(clazz: Class<*>) {
        for (method in clazz.methods) {
            if (Modifier.isStatic(method.modifiers) && method.isAnnotationPresent(SubscribeEvent::class.java)) {
                registerListener(clazz, method, method)
            }
        }
    }
    
    private fun registerObject(target: Any) {
        val classes = HashSet<Class<*>>()
        typesFor(target.javaClass, classes)
        Arrays.stream(target.javaClass.methods).filter { m ->
            !Modifier.isStatic(m.modifiers)
        }.forEach { m ->
            classes.map { c ->
                getDeclareMethod(c, m)
            }.firstOrNull { rm ->
                rm?.isAnnotationPresent(SubscribeEvent::class.java) == true
            }?.let { rm ->
                registerListener(target, m, rm)
            }
        }
    }
    
    private fun typesFor(clz: Class<*>, visited: MutableSet<Class<*>>) {
        if (clz.superclass == null) return
        typesFor(clz.superclass, visited)
        Arrays.stream(clz.interfaces).forEach { typesFor(it, visited) }
        visited.add(clz)
    }
    
    private fun getDeclareMethod(clz: Class<*>, m: Method): Method? {
        return try {
            clz.getDeclaredMethod(m.name, *m.parameterTypes)
        } catch (nse: NoSuchMethodException) {
            null
        }
    }
    
    private fun registerListener(target: Any, f: Method, real: Method) {
        val params: Array<Class<*>> = f.parameterTypes
        
        if (params.size != 1) {
            throw IllegalArgumentException(
                """
                Function $f has @SubscribeEvent annotation.
                It has ${params.size} value parameters,
                but event handler functions require1 value parameter.
            """.trimIndent()
            )
        }
        
        val type = params[0]
        
        if (!Event::class.java.isAssignableFrom(type)) {
            throw IllegalArgumentException(
                """
                Function $f has @SubscribeEvent annotation,
                but takes an argument that is not an Event subtype : $type
            """.trimIndent()
            )
        }
        
        register(type, target, real)
    }
    
    private fun register(type: Class<*>, target: Any, f: Method) {
        try {
            val asm = ASMEventHandler(
                target,
                f,
                Loader.instance().activeModContainer(),
                IGenericEvent::class.java.isAssignableFrom(type)
            )
            
            addToListeners(target, type, asm.priority, asm)
        } catch (e: IllegalAccessException) {
            LOGGER.error(EVENT_BUS, "Error registering event handler: $type $f", e)
        } catch (e: InstantiationException) {
            LOGGER.error(EVENT_BUS, "Error registering event handler: $type $f", e)
        } catch (e: NoSuchMethodException) {
            LOGGER.error(EVENT_BUS, "Error registering event handler: $type $f", e)
        } catch (e: InvocationTargetException) {
            LOGGER.error(EVENT_BUS, "Error registering event handler: $type $f", e)
        }
    }
    
    protected open fun addToListeners(
        target: Any,
        eventType: Class<*>,
        priority: EventPriority,
        listener: IEventListener
    ) {
        val ctr = eventType.getConstructor()
        ctr.isAccessible = true
        val event = ctr.newInstance() as Event
        
        val listenerList = event.listenerList
        listenerList.register(busID, priority, listener)
        val others = listeners.computeIfAbsent(target) { Collections.synchronizedList(ArrayList()) }
        others.add(listener)
    }
    
    /**
     * Add a consumer listener with default [EventPriority.NORMAL] and not receiving cancelled events.
     *
     * @param consumer Callback to invoke when a matching event is received
     * @param T The [Event] subclass to listen for
     */
    public fun <T : Event> addListener(consumer: Consumer<T>) {
        addListener(EventPriority.NORMAL, consumer)
    }
    
    /**
     * Add a consumer listener with the specified [EventPriority] and not receiving cancelled events.
     *
     * @param priority [EventPriority] for this listener
     * @param consumer Callback to invoke when a matching event is received
     * @param T The [Event] subclass to listen for
     */
    public fun <T : Event> addListener(priority: EventPriority, consumer: Consumer<T>) {
        addListener(priority, false, consumer)
    }
    
    /**
     * Add a consumer listener with the specified [EventPriority] and potentially cancelled events.
     *
     * @param priority [EventPriority] for this listener
     * @param receiveCancelled Indicate if this listener should receive events that have been [Cancelable] cancelled
     * @param consumer Callback to invoke when a matching event is received
     * @param T The [Event] subclass to listen for
     */
    public fun <T : Event> addListener(priority: EventPriority, receiveCancelled: Boolean, consumer: Consumer<T>) {
        addListener(priority, passCancelled(receiveCancelled), consumer)
    }
    
    /**
     * Add a consumer listener with the specified [EventPriority] and potentially cancelled events.
     *
     * Use this method when one of the other methods fails to determine the concrete [Event] subclass that is
     * intended to be subscribed to.
     *
     * @param priority [EventPriority] for this listener
     * @param receiveCancelled Indicate if this listener should receive events that have been [Cancelable] cancelled
     * @param eventType The concrete [Event] subclass to subscribe to
     * @param consumer Callback to invoke when a matching event is received
     * @param T The [Event] subclass to listen for
     */
    public fun <T : Event> addListener(
        priority: EventPriority,
        receiveCancelled: Boolean,
        eventType: Class<T>,
        consumer: Consumer<T>
    ) {
        addListener(priority, passCancelled(receiveCancelled), eventType, consumer)
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun <T : Event> addListener(
        priority: EventPriority,
        filter: (T) -> Boolean,
        eventType: Class<T>,
        consumer: Consumer<T>
    ) {
        addToListeners(consumer, eventType, priority) { e ->
            if (filter(e as T)) {
                consumer.accept(e)
            }
        }
    }
    
    private fun passCancelled(receiveCancelled: Boolean): (Event) -> Boolean = { event ->
        receiveCancelled || !event.isCancelable || !event.isCanceled
    }
    
    /**
     * Add a consumer listener for a [GenericEvent] subclass with generic type [F].
     * Despite being a new addition in Kotlin for Forge 1.2.x,
     * this function is backwards compatible with Kotlin for Forge 1.1.x and 1.0.x.
     *
     * @param consumer Callback to invoke when a matching event is received
     * @param T The [GenericEvent] subclass to listen for
     * @param F The [Class] to filter the [GenericEvent] for
     */
    public inline fun <T : GenericEvent<out F>, reified F> addGenericListener(consumer: Consumer<T>) {
        addGenericListener(F::class.java, consumer)
    }
    
    /**
     * Add a consumer listener with the specified [EventPriority] and not receiving cancelled events,
     * for a [GenericEvent] subclass, filtered to only be called for the specified
     * filter [Class].
     *
     * @param priority [EventPriority] for this listener
     * @param consumer Callback to invoke when a matching event is received
     * @param T The [GenericEvent] subclass to listen for
     * @param F The [Class] to filter the [GenericEvent] for
     */
    public inline fun <T : GenericEvent<out F>, reified F> addGenericListener(
        priority: EventPriority,
        consumer: Consumer<T>
    ) {
        addGenericListener(F::class.java, priority, false, consumer)
    }
    
    /**
     * Add a consumer listener with the specified [EventPriority] and potentially cancelled events,
     * for a [GenericEvent] subclass, filtered to only be called for the specified
     * filter [Class].
     *
     * @param priority [EventPriority] for this listener
     * @param receiveCancelled Indicate if this listener should receive events that have been [Cancelable] cancelled
     * @param consumer Callback to invoke when a matching event is received
     * @param T The [GenericEvent] subclass to listen for
     * @param F The [Class] to filter the [GenericEvent] for
     */
    public inline fun <T : GenericEvent<out F>, reified F> addGenericListener(
        priority: EventPriority,
        receiveCancelled: Boolean,
        consumer: Consumer<T>
    ) {
        addGenericListener(F::class.java, priority, receiveCancelled, consumer)
    }
    
    /**
     * Add a consumer listener for a [GenericEvent] subclass, filtered to only be called for the specified
     * filter [Class].
     *
     * @param genericClassFilter A [Class] which the [GenericEvent] should be filtered for
     * @param consumer Callback to invoke when a matching event is received
     * @param T The [GenericEvent] subclass to listen for
     * @param F The [Class] to filter the [GenericEvent] for
     */
    public fun <T : GenericEvent<out F>, F> addGenericListener(genericClassFilter: Class<F>, consumer: Consumer<T>) {
        addGenericListener(genericClassFilter, EventPriority.NORMAL, consumer)
    }
    
    /**
     * Add a consumer listener with the specified [EventPriority] and not receiving cancelled events,
     * for a [GenericEvent] subclass, filtered to only be called for the specified
     * filter [Class].
     *
     * @param genericClassFilter A [Class] which the [GenericEvent] should be filtered for
     * @param priority [EventPriority] for this listener
     * @param consumer Callback to invoke when a matching event is received
     * @param T The [GenericEvent] subclass to listen for
     * @param F The [Class] to filter the [GenericEvent] for
     */
    public fun <T : GenericEvent<out F>, F> addGenericListener(
        genericClassFilter: Class<F>,
        priority: EventPriority,
        consumer: Consumer<T>
    ) {
        addGenericListener(genericClassFilter, priority, false, consumer)
    }
    
    /**
     * Add a consumer listener with the specified [EventPriority] and potentially cancelled events,
     * for a [GenericEvent] subclass, filtered to only be called for the specified
     * filter [Class].
     *
     * @param genericClassFilter A [Class] which the [GenericEvent] should be filtered for
     * @param priority [EventPriority] for this listener
     * @param receiveCancelled Indicate if this listener should receive events that have been [Cancelable] cancelled
     * @param consumer Callback to invoke when a matching event is received
     * @param T The [GenericEvent] subclass to listen for
     * @param F The [Class] to filter the [GenericEvent] for
     */
    public fun <T : GenericEvent<out F>, F> addGenericListener(
        genericClassFilter: Class<F>,
        priority: EventPriority,
        receiveCancelled: Boolean,
        consumer: Consumer<T>
    ) {
        addListener(priority, passGenericCancelled(genericClassFilter, receiveCancelled), consumer)
    }
    
    @Suppress("UNCHECKED_CAST")
    private fun <T : Event> addListener(priority: EventPriority, filter: (T) -> Boolean, consumer: Consumer<T>) {
        val eventType = reflectKotlinSAM(consumer) as Class<T>?
        
        if (eventType == null) {
            LOGGER.error(EVENT_BUS, "Failed to resolve handler for \"$consumer\"")
            throw IllegalStateException("Failed to resolve KFunction event type: $consumer")
        }
        if (eventType == Event::class.java) {
            LOGGER.warn(
                EVENT_BUS, """
                Attempting to add a Lambda listener with computed generic type of Event. 
                Are you sure this is what you meant? NOTE : there are complex lambda forms where 
                the generic type information is erased and cannot be recovered at runtime.
            """.trimIndent()
            )
        }
        
        addListener(priority, filter, eventType, consumer)
    }
    
    /**
     * Fixes issue that crashes when trying to register Kotlin SAM interface
     * for a [Consumer] using the Java [KotlinEventBus.addListener] method
     */
    private fun reflectKotlinSAM(consumer: Consumer<*>): Class<*>? {
        val clazz = consumer.javaClass
        val forgeType = TypeResolver.resolveRawArgument(Consumer::class.java, consumer.javaClass)
        
        if (clazz.simpleName.contains("\$sam$")) {
            try {
                val functionField = clazz.getDeclaredField("function")
                functionField.isAccessible = true
                val function = functionField[consumer]
                
                // Function should have two type parameters (parameter type and return type)
                return TypeResolver.resolveRawArguments(
                    kotlin.jvm.functions.Function1::class.java,
                    function.javaClass
                )[0]
            } catch (e: NoSuchFieldException) {
                // Kotlin SAM interfaces compile to classes with a "function" field
                LOGGER.log(Level.FATAL, "Tried to register invalid Kotlin SAM interface: Missing 'function' field")
                throw e
            }
        } else {
            // Kotlin 1.4 seems to have fixed some of its lambda problems
            return forgeType
        }
    }
    
    private fun <T : GenericEvent<out F>, F> passGenericCancelled(
        genericClassFilter: Class<F>,
        receiveCancelled: Boolean
    ): (T) -> Boolean = { event ->
        event.genericType == genericClassFilter && (receiveCancelled || !event.isCancelable || !event.isCanceled)
    }
    
    /**
     * Add a consumer listener with the specified [EventPriority] and potentially cancelled events,
     * for a [GenericEvent] subclass, filtered to only be called for the specified
     * filter [Class].
     *
     * Use this method when one of the other methods fails to determine the concrete [GenericEvent] subclass that is
     * intended to be subscribed to.
     *
     * @param genericClassFilter A [Class] which the [GenericEvent] should be filtered for
     * @param priority [EventPriority] for this listener
     * @param receiveCancelled Indicate if this listener should receive events that have been [Cancelable] cancelled
     * @param eventType The concrete [GenericEvent] subclass to subscribe to
     * @param consumer Callback to invoke when a matching event is received
     * @param T The [GenericEvent] subclass to listen for
     * @param F The [Class] to filter the [GenericEvent] for
     */
    public fun <T : GenericEvent<out F>, F> addGenericListener(
        genericClassFilter: Class<F>,
        priority: EventPriority,
        receiveCancelled: Boolean,
        eventType: Class<T>,
        consumer: Consumer<T>
    ) {
        addListener(priority, passGenericCancelled(genericClassFilter, receiveCancelled), eventType, consumer)
    }
    
    /**
     * Removes the specified
     */
    override fun unregister(any: Any?) {
        val list = listeners.remove(any) ?: return
        
        for (listener in list) {
            ListenerList.unregisterAll(busID, listener)
        }
    }
    
    override fun post(event: Event): Boolean {
        return post(IEventListener::invoke, event)
    }
    
    private fun post(wrapper: (IEventListener, Event) -> Unit, event: Event): Boolean {
        if (shutdown) return false
        
        val listeners = event.listenerList.getListeners(busID)
        
        for (index in listeners.indices) {
            try {
                wrapper.invoke(listeners[index], event)
            } catch (throwable: Throwable) {
                exceptionHandler.handleException(this, event, listeners, index, throwable)
                throw throwable
            }
        }
        
        return event.isCancelable && event.isCanceled
    }
    
    override fun handleException(
        bus: EventBus,
        event: Event,
        listeners: Array<out IEventListener>,
        index: Int,
        throwable: Throwable
    ) {
        LOGGER.error(EVENT_BUS)
    }
    
    override fun shutdown() {
        LOGGER.fatal(
            EVENT_BUS,
            "KotlinEventBus $busID shutting down - future events will not be posted.",
            Exception("stacktrace")
        )
    }
    
    public fun start() {
        shutdown = false
    }
    
    private companion object {
        private val LOGGER = LogManager.getLogger()
        private val EVENT_BUS = MarkerManager.getMarker("EVENTBUS")
        private var MAX_ID: Int
        private val RESIZE_LISTENER_LIST: (Int) -> Unit
        
        init {
            val maxIDField = EventBus::class.java.getDeclaredField("maxID")
            maxIDField.isAccessible = true
            MAX_ID = maxIDField.get(null) as Int
            val resizeMethod = ListenerList::class.java.getDeclaredMethod("resize", Int::class.java)
            resizeMethod.isAccessible = true
            RESIZE_LISTENER_LIST = { max -> resizeMethod.invoke(null, max) }
        }
    }
}