# KotlinForForge
Makes Kotlin forge-friendly by doing the following:
- Provides the Kotlin libraries.
- Provides `KotlinLanguageProvider` to allow usage of object declarations as @Mod targets.
- Provides `AutoKotlinEventBusSubscriber` to allow usage of object declarations as @Mod.EventBusSubscriber targets.
- Provides useful utility functions and constants
- Provides its own implementation of the Forge eventbus to work with KCallables and reified type parameters
- ~~Provides sided property delegates and object holder property delegates~~

As of Kotlin for Forge 1.4.0, you must use Gradle 5.3 or newer. To update,
go to the file at `./gradle/wrapper/gradle-wrapper.properties` and change this line:
```properties
# Gradle 5.3 or newer. Works fine with ForgeGradle.
distributionUrl=https\://services.gradle.org/distributions/gradle-5.3-all.zip
```

To implement in an existing project, paste the following into your build.gradle:
```kotlin
repositories {
    maven("https://maven.pkg.github.com/CappCraft-Team/KotlinForForge") {
        credentials {
            username = "1449182174@qq.com"
            password = "bde866e4093346da4af110310e73209198aa0b84"
        }
    }
}

dependencies {
    // Use the latest version of KotlinForForge
    implementation("thedarkcolour.kotlinforforge:kotlinforforge:2.0.0")
}
```
Use KotlinLanguageAdapter
```java
@Mod(
    modid = MOD_ID,
    name = MOD_NAME,
    version = MOD_VERSION,
    modLanguageAdapter = "thedarkcolour.kotlinforforge.KotlinLanguageAdapter"
)
```
