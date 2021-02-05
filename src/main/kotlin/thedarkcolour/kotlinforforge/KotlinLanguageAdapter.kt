package thedarkcolour.kotlinforforge

import net.minecraftforge.fml.common.FMLModContainer
import net.minecraftforge.fml.common.ILanguageAdapter
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.ModContainer
import net.minecraftforge.fml.relauncher.Side
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Implementation of [ILanguageAdapter]
 * Set [Mod.modLanguageAdapter] to [thedarkcolour.kotlinforforge.KotlinLanguageAdapter]
 * @author shadowfacts
 */
public class KotlinLanguageAdapter : ILanguageAdapter {
    override fun getNewInstance(
        container: FMLModContainer,
        objectClass: Class<*>,
        classLoader: ClassLoader,
        factoryMarkedAnnotation: Method?
    ): Any {
        LOGGER.debug("KotlinLanguageAdapter loading class:${objectClass.simpleName}")
        return objectClass.kotlin.objectInstance ?: objectClass.newInstance()
    }
    
    override fun supportsStatics(): Boolean {
        return false
    }
    
    override fun setProxy(target: Field, proxyTarget: Class<*>, proxy: Any) {
        LOGGER.debug("Setting proxy: ${target.declaringClass.simpleName}.${target.name} -> $proxy")
    
        // objectInstance is not null if it's a Kotlin object, so set the value on the object
        // if it is null, set the value on the static field
        target.set(proxyTarget.kotlin.objectInstance, proxy)
    }
    
    override fun setInternalProxies(mod: ModContainer?, side: Side?, loader: ClassLoader?) {
        // Nothing to do; FML's got this covered for Kotlin
    }
}