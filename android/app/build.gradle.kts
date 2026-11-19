import java.util.Properties

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

// Versión: versionName a mano (semver) y versionCode = número de commits (sube solo con cada commit).
val appVersionName = "0.2.0"
val gitCommitCount: Int = providers.exec { commandLine("git", "rev-list", "--count", "HEAD"); isIgnoreExitValue = true }
    .standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.getOrElse(1)
val gitShortSha: String = providers.exec { commandLine("git", "rev-parse", "--short", "HEAD"); isIgnoreExitValue = true }
    .standardOutput.asText.map { it.trim().ifEmpty { "local" } }.getOrElse("local")

// Firma de la variante staging (APK de pruebas del QR). Las credenciales NUNCA van al repo: se leen de
// android/keystore.properties (gitignored) o de ~/.gradle/gradle.properties. Ver android/AGENTS.md › «APK de pruebas».
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingProp(name: String): String? = keystoreProps.getProperty(name) ?: (project.findProperty(name) as String?)
val testStoreFile = signingProp("oncargoTestStoreFile")?.let { file(it) }?.takeIf { it.exists() }
// ABIs del APK de pruebas: los celulares reales son ARM. Para el emulador x86_64: -PstagingAbis=x86_64
val stagingAbis = ((project.findProperty("stagingAbis") as String?) ?: "arm64-v8a,armeabi-v7a")
    .split(",").map { it.trim() }.filter { it.isNotEmpty() }

android {
    namespace = "pe.logyx.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "pe.logyx.app"
        minSdk = 29
        targetSdk = 36
        versionCode = gitCommitCount
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (testStoreFile != null) {
            create("oncargoTest") {
                storeFile = testStoreFile
                storePassword = signingProp("oncargoTestStorePassword")
                keyAlias = signingProp("oncargoTestKeyAlias") ?: "oncargo-test"
                keyPassword = signingProp("oncargoTestKeyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "WEB_BASE_URL", "\"$webBaseUrl\"")
            buildConfigField("boolean", "DEMO_ACCOUNTS", "true")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // En release la API debe ser HTTPS; configurable con -PapiBaseUrl.
            val prodApi = (project.findProperty("apiBaseUrl") as String?) ?: "https://api.oncargo.tech"
            val prodWeb = (project.findProperty("webBaseUrl") as String?) ?: "https://oncargo.tech"
            buildConfigField("String", "API_BASE_URL", "\"$prodApi\"")
            buildConfigField("String", "WEB_BASE_URL", "\"$prodWeb\"")
            buildConfigField("boolean", "DEMO_ACCOUNTS", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // APK de pruebas que se publica en https://oncargo.tech/app/ (QR): como release (R8 + recorte de recursos),
        // contra producción y firmado con el keystore de pruebas para instalar encima sin desinstalar.
        create("staging") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-$gitShortSha"
            buildConfigField("String", "API_BASE_URL", "\"https://api.oncargo.tech\"")
            buildConfigField("String", "WEB_BASE_URL", "\"https://oncargo.tech\"")
            // La API de pruebas tiene las cuentas demo: chips de acceso rápido en el login.
            buildConfigField("boolean", "DEMO_ACCOUNTS", "true")
            ndk { abiFilters += stagingAbis }
            signingConfig = signingConfigs.findByName("oncargoTest")
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
    implementation(project(":feature:trips"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
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

// APK de pruebas: libs nativas (MapLibre) comprimidas dentro del APK. Pesa menos para bajarlo por el QR
// (~la mitad); a cambio Android las extrae al instalar. Debug y release siguen sin comprimir.
androidComponents {
    onVariants(selector().withBuildType("staging")) { variant ->
        variant.packaging.jniLibs.useLegacyPackaging.set(true)
    }
}
