# OnCargo — reglas R8 para release/staging (minify + shrinkResources).
# Las librerías traen sus propias reglas de consumidor (kotlinx.serialization, Compose, Koin, OkHttp, MapLibre,
# WorkManager, Navigation); aquí van solo los huecos conocidos. Si una pantalla falla solo en staging,
# revisa `adb logcat -b crash` y agrega la regla mínima aquí (con un comentario de por qué).

# Trazas legibles en los reportes de los testers (retrace con app/build/outputs/mapping/staging/mapping.txt).
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# ---- kotlinx.serialization -------------------------------------------------------------------------
# DTOs del contrato (:core:model) y rutas de navegación tipadas (@Serializable). La librería ya conserva
# `serializer()` de cada clase anotada; además mantenemos los nombres de enums (se serializan por nombre)
# y los companion/serializers generados de nuestros paquetes.
-keepclassmembers @kotlinx.serialization.Serializable class pe.logyx.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class pe.logyx.**$$serializer { *; }
-keepclassmembers enum pe.logyx.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Ktor (cliente OkHttp) ---------------------------------------------------------------------------
# Ktor carga el motor y algunos plugins por ServiceLoader; referencias opcionales a JMX/slf4j no existen en Android.
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { *; }
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn io.ktor.util.debug.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---- Koin --------------------------------------------------------------------------------------------
# Las definiciones son lambdas (sin reflexión); los ViewModels se crean por constructor referenciado.
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# ---- MapLibre ----------------------------------------------------------------------------------------
# La librería nativa llama por JNI a clases Java por nombre (además de sus reglas de consumidor).
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**
-keep class com.mapbox.geojson.** { *; }
-dontwarn com.google.android.gms.**

# ---- WorkManager ------------------------------------------------------------------------------------
# El worker se instancia por nombre desde la base de datos de WorkManager.
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# ---- Varios -----------------------------------------------------------------------------------------
-dontwarn javax.annotation.**
-dontwarn kotlin.reflect.jvm.internal.**
