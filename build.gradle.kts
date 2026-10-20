// 根构建脚本：只声明插件，不应用
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Compose 编译器插件。Kotlin 2.0 起必须显式声明，版本与 Kotlin 保持一致
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.25" apply false
}
