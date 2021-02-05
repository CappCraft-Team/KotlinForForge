package thedarkcolour.kotlinforforge

import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import thedarkcolour.kotlinforforge.KotlinForForge.MOD_ID
import thedarkcolour.kotlinforforge.KotlinForForge.MOD_NAME
import thedarkcolour.kotlinforforge.KotlinForForge.MOD_VERSION
import thedarkcolour.kotlinforforge.forge.SIDE

/**
 * Set [Mod.modLanguageAdapter] to [thedarkcolour.kotlinforforge.KotlinLanguageAdapter]
 */
@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "thedarkcolour.kotlinforforge.KotlinLanguageAdapter"
)
public object KotlinForForge {
    public const val MOD_ID: String = "kotlinforforge"
    public const val MOD_NAME: String = "Kotlin For Forge"
    public const val MOD_VERSION: String = "2.0.0"
    @Mod.EventHandler
    public fun onPreInitialization(evt: FMLPreInitializationEvent) {
        Loader.instance().modList.forEach {
            AutoKotlinEventBusSubscriber.subscribeAutomatic(it, evt.asmData, SIDE)
        }
    }
}