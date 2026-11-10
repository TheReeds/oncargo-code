// Kotlin/JVM puro: sin dependencias Android (candidato a Kotlin Multiplatform para la app SwiftUI).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(21) }

dependencies {
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
