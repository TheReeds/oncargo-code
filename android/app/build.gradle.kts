plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// URL de la API: por defecto emulador -> host (10.0.2.2:8080). Se puede cambiar con
//   ./gradlew assembleDebug -PapiBaseUrl=http://192.168.1.50:8080
// o en ~/.gradle/gradle.properties. Ver android/AGENTS.md.
val apiBaseUrl: String = (project.findProperty("apiBaseUrl") as String?) ?: "http://10.0.2.2:8080"
val webBaseUrl: String = (project.findProperty("webBaseUrl") as String?) ?: "http://10.0.2.2:4321"

android {
    namespace = "pe.logyx.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "pe.logyx.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "WEB_BASE_URL", "\"$webBaseUrl\"")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // En release la API debe ser HTTPS; configurable con -PapiBaseUrl.
            val prodApi = (project.findProperty("apiBaseUrl") as String?) ?: "https://api.logyx.pe"
            val prodWeb = (project.findProperty("webBaseUrl") as String?) ?: "https://logyx.pe"
            buildConfigField("String", "API_BASE_URL", "\"$prodApi\"")
            buildConfigField("String", "WEB_BASE_URL", "\"$prodWeb\"")
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:loads"))
    implementation(project(":feature:requests"))
    implementation(project(":feature:shipments"))
    implementation(project(":feature:driver"))
    implementation(project(":feature:negotiation"))
    implementation(project(":feature:incidents"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:reputation"))
    implementation(project(":feature:documents"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlin.test)
    debugImplementation(platform(libs.compose.bom))
    debugImplementation(libs.compose.ui.test.manifest)
}
