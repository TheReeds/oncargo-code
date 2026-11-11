# LOGYX Android — guía para agentes

App nativa única (Kotlin + Jetpack Compose + Material 3) con flujos por rol: **transportista**, **conductor** y **PYME**.
Contexto global en `../AGENTS.md`; contrato de API en `../docs/api/openapi.yaml`; diseño en `../docs/design/DESIGN.md`.

## Compilar y probar
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk      # el JDK por defecto de la máquina es 27
cd android
./gradlew assembleDebug testDebugUnitTest          # verificación obligatoria
./gradlew installDebug                             # emulador/dispositivo (API en http://10.0.2.2:8080)
```
- `local.properties` (gitignored) debe tener `sdk.dir=/home/<user>/Android/Sdk`.
- Wrapper: Gradle 9.8.2. AGP 9.5.1 (Kotlin integrado: los módulos Android **no** aplican `org.jetbrains.kotlin.android`),
  Kotlin 2.4.21, Compose BOM 2026.09.00, Navigation 2.10.2, Ktor 3.6.1, Koin 4.2.2, MapLibre 13.7.1. Todo en `gradle/libs.versions.toml`.
- `compileSdk = 37` (las versiones androidx 2026 lo exigen; requiere `platforms;android-37.0` en el SDK), `targetSdk = 36`, `minSdk = 29`.
- Cuentas demo (solo builds debug muestran chips en el login): `pyme@logyx.pe`, `carrier@logyx.pe`, `driver@logyx.pe` / `Logyx2026!`.

## Probar en emulador (headless, Linux)
Requiere KVM (`/dev/kvm`). Una sola vez:
```bash
export ANDROID_HOME=~/Android/Sdk JAVA_HOME=/usr/lib/jvm/java-21-openjdk
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "system-images;android-36;google_apis;x86_64"
echo no | $ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd -n logyx -k "system-images;android-36;google_apis;x86_64" --force
printf 'hw.ramSize=3072\nhw.lcd.width=1080\nhw.lcd.height=2400\nhw.lcd.density=420\n' >> ~/.android/avd/logyx.avd/config.ini
```
Cada sesión:
```bash
# 1) Backend (dev, datos demo). El host 10.0.2.2 solo es aceptado con host-validation desactivado:
cd backend && ./mvnw quarkus:dev -Dquarkus.http.port=8280 -Dquarkus.http.host-validation.require-localhost=false
# 2) Emulador sin ventana
~/Android/Sdk/emulator/emulator -avd logyx -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot &
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 5; done
# 3) Instalar apuntando al backend y lanzar
./gradlew installDebug -PapiBaseUrl=http://10.0.2.2:8280
adb shell am start -n pe.logyx.app.debug/pe.logyx.app.MainActivity
```
Inspección: `adb exec-out screencap -p > x.png` · `adb shell uiautomator dump /sdcard/u.xml && adb pull /sdcard/u.xml` (textos y bounds para `input tap`) ·
`adb logcat -b crash -d` · modo oscuro `adb shell cmd uimode night yes|no` · fuente grande `adb shell settings put system font_scale 1.3` ·
reset de sesión `adb shell pm clear pe.logyx.app.debug`. Apagar: `adb emu kill`.
Memoria (máquina de 15 GB): un solo proceso pesado a la vez; `./gradlew --stop` antes de levantar emulador/backend, Gradle con `-Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.daemon.jvmargs=-Xmx1g`, emulador con `-memory 2048`; con poca RAM libre los tests de Compose dan timeout.
Tests instrumentados (Compose, repositorios fake vía Koin, sin backend; el emulador debe estar encendido; con poca RAM añade `-Dorg.gradle.jvmargs=-Xmx4g`):
```bash
./gradlew connectedDebugAndroidTest      # app/src/androidTest: RoleHomeTest, BidFlowTest, DriverAdvanceTest (con evidencia), ChatTest (RN07)
```
Capturas de referencia (claro/oscuro): `docs/screenshots/`.

## Apuntar a otra API
`app/build.gradle.kts` define `BuildConfig.API_BASE_URL` / `WEB_BASE_URL` (se inyectan por Koin como `AppConfig`):
```bash
./gradlew installDebug -PapiBaseUrl=http://192.168.1.50:8080 -PwebBaseUrl=http://192.168.1.50:4321   # dispositivo físico en LAN
```
(o ponlo en `~/.gradle/gradle.properties`). Debug: `http://10.0.2.2:8080`. Release: `https://api.logyx.pe` por defecto.
El tráfico HTTP en claro solo se permite en **debug** (`app/src/debug/res/xml/network_security_config.xml`); la config de `main` lo prohíbe.

## Mapa de módulos
```
:app                  Activity, Application (Koin), navegación raíz por rol, BuildConfig → AppConfig
:core:model           Kotlin/JVM PURO. DTOs @Serializable del contrato + reglas (canCarry, nextStop, normalizeCity)
:core:network         Kotlin/JVM PURO. Ktor (OkHttp) + LogyxApi tipado, {data,meta}, ApiException (errores en español)
:core:data            Android lib. Repositorios (interfaces + Default*), AuthRepository/SessionStore (DataStore), dataModule()
:core:designsystem    Android lib. Tema LOGYX, Geist Sans/Mono (res/font, OFL), componentes y mapa MapLibre
:feature:auth         Login, Cuenta
:feature:loads        Transportista: Home "Tu actividad", feed de LoadCard + chips, detalle con mapa + bottom sheet de oferta, mis ofertas
:feature:requests     PYME: mis solicitudes, detalle con comparación de ofertas y aceptar/cancelar
:feature:shipments    Envíos (PYME y transportista): lista, seguimiento con mapa + StopTimeline, asignar conductor
:feature:driver       Modo conductor: viaje activo, siguiente parada, botón único; evidencia de entrega (foto/firma, cola de subida) y bloqueo RN11
:feature:negotiation  Chat por solicitud (envío optimista, RN07 bajo el campo, polling 5 s) y lista de hilos (PYME)
:feature:incidents    Reportar incidencia (categoría + descripción + foto opcional), lista y detalle con comentarios
:feature:reputation   Perfil público de organización (Trust Score) y calificar tras la entrega (`canReview`)
:feature:documents    Documentos/evidencias del envío: miniaturas, visor a pantalla completa, PDF por intent externo
:feature:notifications Centro de notificaciones + `UnreadCounter`/`rememberUnreadCount` (badge, polling 30 s en primer plano)
```
Contra-ofertas: la PYME propone desde la comparación de ofertas (`:feature:requests`) y el transportista acepta/rechaza en "Mis ofertas" (`:feature:loads`).
**Evidencia de entrega (RN06):** `EvidenceRepository` (`:core:data`) comprime a JPEG <= 2 MB, guarda en `filesDir/evidence` (cola persistente) y sube por `POST /shipments/{id}/documents`; "Entregado" se habilita con >= 1 `DELIVERY_PHOTO` subida. Captura con `TakePicture` (sin permiso CAMERA). Un único `FileProvider` vive en `:app` (`${applicationId}.fileprovider`, `res/xml/file_paths.xml` con los cache-path de driver/incidents/documents): no declares otro provider en un feature (choca al fusionar manifiestos).
Los endpoints binarios (`/documents/{id}/file`) deben pedirse con `Accept: */*` (con solo JSON el backend responde 406).
### Reglas de dependencias (no las rompas)
- `core:model` y `core:network` **no pueden** depender de Android ni de Compose (se convertirán a KMP para SwiftUI). Solo kotlinx/Ktor.
- `core:network` → `core:model`. `core:data` → `core:network` (+ `core:model`). `core:designsystem` → `core:model`.
- `feature:*` → `core:designsystem` + `core:data`. **Un feature nunca depende de otro feature.** La navegación entre
  features la cablea `:app` (los `*Route` reciben callbacks `onOpenX`).
- `:app` depende de todo. Los features no leen `BuildConfig`; lo que necesiten llega por parámetros/`AppConfig`.

## Patrón de una feature (UDF)
`XxxViewModel` expone `StateFlow<XxxUiState>` (data class inmutable: `loading`, `refreshing`, `error`, datos) y métodos de intención.
`XxxRoute` (con `koinViewModel()` + `collectAsStateWithLifecycle`) llama al `XxxScreen(state, callbacks)` **sin estado y sin ViewModel**
(fácil de previsualizar/testear). Siempre: estado de carga, vacío (`EmptyState`) y error (`ErrorState` con "Reintentar"),
pull-to-refresh con `Refreshable`, mensajes de la API mostrados tal cual (`Throwable.userMessage()`).

### Agregar una feature, paso a paso
1. `feature/<nombre>/build.gradle.kts`: copia el de `feature/requests` (cambia `namespace`). Registra el módulo en `settings.gradle.kts`.
2. Si necesitas un endpoint nuevo: añade DTOs en `core/model` (según `openapi.yaml`), el método en `LogyxApi`
   (+ un test en `core/network/src/test/.../DtoParsingTest.kt` con JSON de ejemplo del contrato).
3. Añade el método al repositorio (interfaz + `Default*` en `core/data/.../Repositories.kt`, envuelto en `apiCall { }`).
4. Crea `XxxViewModel`, `XxxScreen` + `XxxRoute` y `val xxxModule = module { viewModelOf(::XxxViewModel) }`
   (con argumentos de navegación: `viewModel { (id: String) -> XxxViewModel(id, get()) }` y `koinViewModel { parametersOf(id) }`).
5. En `:app`: dependencia `implementation(project(":feature:xxx"))`, `xxxModule` en `LogyxApplication.startKoin`, ruta `@Serializable`
   en `navigation/Routes.kt` y `composable<...>` (y pestaña si aplica) en `navigation/LogyxApp.kt`.
6. Tests unitarios del ViewModel con fakes de los repositorios (ver `feature/driver/src/test`); `Dispatchers.setMain(UnconfinedTestDispatcher())`.
7. `./gradlew assembleDebug testDebugUnitTest` y actualiza `docs/STATUS.md`.

## Navegación por rol (`app/.../navigation/LogyxApp.kt`)
`AuthRepository.state` (`Loading | LoggedOut | LoggedIn(user)`) decide la pantalla. Rol: `role == DRIVER` → conductor;
`organizationType == CARRIER` → transportista; `PYME` → PYME; operador → aviso "usa la web".
Un 401 en cualquier llamada cierra sesión automáticamente (`dataModule`).

## Diseño y tokens (`core/designsystem`) — skill `logyx-design-system` (`.claude/skills/logyx-design-system/references/android.md`)
- `LogyxTheme { }` mapea los tokens de `docs/design/DESIGN.md` a Material 3 (claro y oscuro). `LogyxPalette` = hex del documento.
- Extras fuera de M3 vía `LogyxTheme.colors`: `money` (esmeralda de marca: iconos/relleno), `moneyStrong` (botones, AA con texto blanco),
  `moneyText` (precios como texto), `warning*`, `route`.
- Tipografía: Geist Sans (UI) y Geist Mono (`LogyxText.moneyLarge/mono/kpi`, números tabulares) — fuentes variables en `res/font` con licencia OFL (`OFL-Geist.txt`).
- Formatos en `Fmt`: `Fmt.money(1850.0)` → `S/ 1,850.00`; `Fmt.dateTime(iso)` → `28 nov · 08:00` (hora de Lima); `Fmt.remaining(...)`.
- Componentes (catálogo completo con firmas y ejemplos en `../.claude/skills/logyx-design-system/references/android.md`):
  botones `LogyxButton` (Primary/Money/Secondary/Ghost/Destructive; Medium/Large/Xl; `loading`), formularios `LogyxTextField`, `MoneyField`,
  `LogyxDropdown`, `LogyxCombobox`, `LogyxDateField`/`LogyxDateRangeField` (es-PE), mensajes `UiMessage` + `MessageEffect` + `LogyxSnackbarHost`,
  `AlertBanner`, `LogyxConfirmDialog`, `LogyxBottomSheet`, `LogyxFilterChip`, `SegmentedControl`, badges, skeletons (`SkeletonList`),
  `LogyxScreen`/`LogyxTopBar`/`LogyxBottomBar`, y los de dominio: `LoadCard`, `RouteLine`, `StatusPill`/`InfoChip`, `StopTimeline`, `KpiTile`, `PriceTag`,
  `EmptyState`/`ErrorState`/`LoadingState`, `TruckIllustration`, `AuctionCountdown`, `RouteMap`, `Refreshable`. Etiquetas de enums en `Labels.kt` (`@Composable`, desde `strings.xml`; en lambdas no composables precalcula un mapa). Selección/avatar/pie: `RadioCardRow`, `InitialsAvatar`, `LoadingMoreFooter`.
- **Regla:** los features NO usan controles Material crudos (`OutlinedTextField`, `Button`, `AlertDialog`, `ModalBottomSheet`, `SnackbarHostState`, `FilterChip`…);
  si falta un componente se crea en `:core:designsystem` con `@Preview` y entrada en el catálogo. Acciones irreversibles/con dinero -> `LogyxConfirmDialog`.
- **Mensajes desde ViewModels:** el estado lleva `message: UiMessage?` (`UiMessage.success(R.string.loads_bid_sent)`; literal solo para textos de la API: `UiMessage.error(e.userMessage())`; errores de campo = `UiText`) y `onMessageShown()`;
  la pantalla usa `MessageEffect(state.message, snackbar, onMessageShown)` con `rememberLogyxSnackbarState()` y `LogyxScreen(snackbar = …)`.
- **Catálogo UI** (`UiCatalogScreen`, paquete `designsystem.catalog`): solo accesible en debug desde el login (botón "Catálogo UI"). Agrega ahí todo componente nuevo.
- Textos visibles en `res/values/strings.xml` de cada módulo (prefijo del módulo). Copy: español peruano, "tú", botones verbo+objeto.
- Bottom sheets: `LogyxBottomSheet` ya hace scroll; **no** pongas `verticalScroll` dentro (crash por altura infinita).
- Contraste: no pongas texto blanco sobre `money` (#10B981); usa `moneyStrong`.

## Dependencias con requisitos ocultos
- OkHttp ≥ 5.5 / androidx 2026 exigen `compileSdk 37`. Si bajas el compileSdk, tendrás que fijar versiones antiguas.
- `kotlin("test")` no funciona en módulos Android: usa `libs.kotlin.test`.
