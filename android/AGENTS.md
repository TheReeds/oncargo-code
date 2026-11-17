# OnCargo Android — guía para agentes (producto antes LOGYX)

App nativa única (Kotlin + Jetpack Compose + Material 3) con flujos por rol: **transportista**, **conductor** y **PYME**.
Contexto global en `../AGENTS.md`; contrato de API en `../docs/api/openapi.yaml`; diseño en `../docs/design/DESIGN.md`.

## Marca OnCargo
Nombre visible: **OnCargo** (`@string/app_name`, `android:label`). Los identificadores técnicos siguen en `logyx` (paquetes `pe.logyx.*`, `applicationId`, `Logyx*`, `LOGYX-XXX-NNN`); ver `../docs/brand.md`.
- Ícono: adaptativo (`mipmap-anydpi-v26/ic_launcher*.xml`) con fondo `@color/ic_launcher_background` (#081F46), foreground y monocromático (Android 13+) PNG en `mipmap-*dpi`, más `ic_launcher(.round).png` heredados. Generados con ImageMagick desde `media/logos/OnCargo-icono-app.png` (símbolo ~47% del lienzo, dentro de la zona segura 66/108 dp).
- Logotipo en la app: `R.drawable.oncargo_logo` (`:core:designsystem`, `drawable-nodpi` claro y `drawable-night-nodpi` oscuro) y `oncargo_logo_on_dark` (siempre la versión para fondo oscuro; cabecera del login). Úsalos con `Image(painterResource(...))`.
- Textos de UI: di "OnCargo", nunca "LOGYX".

## Compilar y probar
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk      # el JDK por defecto de la máquina es 27
cd android
./gradlew assembleDebug testDebugUnitTest :core:network:test   # verificación obligatoria
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
./gradlew connectedDebugAndroidTest      # app/src/androidTest: RoleHomeTest, BidFlowTest, DriverAdvanceTest (con evidencia), ChatTest (RN07), TripsFlowTest (PymeBookingFlowTest, CarrierTripBookingsFlowTest, PublishTripFlowTest, RejectBookingReasonFlowTest, SmartLoadFlowTest; fakes en `FakeTrips.kt`: `FakeTrips`, `FakeSmartLoads`, `SmartData`)
```
Antes de `connectedDebugAndroidTest` limpia la sesión de la app de debug si la usaste a mano: `adb shell pm clear pe.logyx.app.debug` (con una sesión guardada los tests dan timeout).
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
:core:designsystem    Android lib. Tema (`LogyxTheme`), Geist Sans/Mono (res/font, OFL), componentes y mapa MapLibre
:feature:auth         Login, Cuenta
:feature:loads        Transportista: Home "Tu actividad", feed de LoadCard + chips, detalle con mapa + bottom sheet de oferta, mis ofertas
:feature:requests     PYME: mis solicitudes, detalle con comparación de ofertas y aceptar/cancelar
:feature:shipments    Envíos (PYME y transportista): lista, seguimiento con mapa + StopTimeline, asignar conductor
:feature:driver       Modo conductor: viaje activo, siguiente parada, botón único; evidencia de entrega (foto/firma, cola de subida) y bloqueo RN11
:feature:negotiation  Chat por solicitud (envío optimista, RN07 bajo el campo, tiempo real SSE con respaldo 30 s) y lista de hilos (PYME)
:feature:incidents    Reportar incidencia (categoría + descripción + foto opcional), lista y detalle con comentarios
:feature:reputation   Perfil público de organización (Trust Score) y calificar tras la entrega (`canReview`)
:feature:documents    Documentos/evidencias del envío: miniaturas, visor a pantalla completa, PDF por intent externo
:feature:trips        Fase 4. Transportista: Mis viajes, Publicar viaje, Detalle con reservas (confirmar/rechazar), Cargas de retorno (oferta con el `vehicleId` sugerido), Sugerencias Smart Load. PYME: pestaña "Viajes" (buscar con filtros, reservar espacio, Mis reservas con cancelar). Datos: `TripsRepository`/`SmartLoadsRepository`
:feature:notifications Centro de notificaciones + `UnreadCounter`/`rememberUnreadCount` (badge por tiempo real `notification.created`, respaldo 60 s)
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
  `EmptyState`/`ErrorState`/`LoadingState`, `LiveIndicator` (+ `ProvideRealtimeStatus`), `TruckIllustration`, `AuctionCountdown`, `RouteMap`, `Refreshable`. Etiquetas de enums en `Labels.kt` (`@Composable`, desde `strings.xml`; en lambdas no composables precalcula un mapa). Selección/avatar/pie: `RadioCardRow`, `InitialsAvatar`, `LoadingMoreFooter`.
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

## Viajes, reservas, retornos y Smart Load (fase 4)
- Contrato: `docs/api/CHANGELOG.md` "Fase 4". DTOs en `core/model/.../Trips.kt` (`TripView`, `TripBookingView`, `ReturnLoadView`, `SmartLoadView`, `TripFilter`; `TripView.priceFor(kg)` espeja `TripPricingPolicy` y da el precio estimado de la reserva).
- `LogyxJson` usa `coerceInputValues`: un valor de enum desconocido en `NotificationType` cae en `UNKNOWN` y en `NotificationTarget` en `null` (no rompe la lista de avisos).
- La salida del viaje se envía en hora de Lima: `LocalDateTime.atOffset(-05:00)` con `ISO_OFFSET_DATE_TIME`. Errores de campo del API (`capacityKg`, `departureAt`, `weightKg`, `cargoType`, `amount`) se muestran bajo el campo; el resto, en snackbar.
- Cargas de retorno: se oferta con `LoadsRepository.placeBid(requestId, item.vehicleId, ...)`. Smart Load: aceptar solo avisa a las PYMEs, no oferta.
- Navegación: el Home del transportista abre `CarrierTrips`, `ReturnLoads` y `SmartLoads` (no hay pestañas nuevas para no pasar de 6); la PYME tiene la pestaña `PymeTrips`. Avisos en `openNotification`: `TRIP` (`relatedId` = id del viaje) abre `CarrierTripDetail` (transportista) o `PymeTripDetail` (PYME: el viaje + sus reservas en él, `PymeTripDetailViewModel`); `SMART_LOAD` abre `SmartLoads`.
- Rechazar una reserva (transportista) abre `RejectBookingSheet` (`LogyxBottomSheet` + `LogyxTextField` opcional, máx. 300) y envía `reason` (vacío = `null`); la PYME lo ve como `decisionNote` en su reserva.
- Home de la PYME = pestaña Solicitudes: `RequestsViewModel` pide también `dashboard()` y muestra `activeTripBookings` (reservas PENDING/CONFIRMED) en un `KpiTile`; si el dashboard falla, la lista carga igual y el tile no aparece.
- Tests instrumentados con campos de texto: el botón puede quedar tapado por el teclado dentro de una hoja; usa `performSemanticsAction(SemanticsActions.OnClick)` en lugar de `performClick`.

## Tiempo real (fase 6, SSE — ADR-0007)
- **Cliente**: `RealtimeClient` (`:core:network`, Kotlin puro) abre `GET /api/v1/events/stream` con el token actual; `SseParser` es incremental (WHATWG). Reconecta con espera exponencial (1 s a 30 s) enviando `Last-Event-ID`; timeout de request infinito y de socket 60 s (el servidor manda `: ping` cada ~20 s). Tras una reconexión (o `stream.resync`) emite `stream.resync` sintético; `stream.ready` y `stream.expired` no llegan a las pantallas. Un 401 llama a `onUnauthorized` (cierra sesión). `status: StateFlow<RealtimeStatus>` (CONNECTING, LIVE, RECONNECTING, OFFLINE).
- **Repositorio**: `RealtimeRepository.events` (`SharedFlow`, una sola conexión compartida con `WhileSubscribed(5 s)`: sin suscriptores no hay conexión) y `refreshSignals(types, fallbackMs, match)`: emite por cada evento de `types` que cumpla `match`, por cada `stream.resync` y, **solo si el stream no está LIVE**, un tick de respaldo cada `fallbackMs`.
- **Indicador en vivo**: `LiveIndicator` (`:core:designsystem`) pinta "En vivo" (LIVE) o "Reconectando…" (RECONNECTING) dentro de `LogyxTopBar`; `CONNECTING`/`OFFLINE` no muestran nada (sin conexión abierta no es un fallo). `:app` (`RoleShell`) publica `RealtimeRepository.status` con `ProvideRealtimeStatus`; los features no lo conocen. Leer `status` no abre la conexión.
- **Solo en primer plano**: cada ViewModel expone `suspend fun liveLoop()` (colecta `refreshSignals` y hace un refresco **silencioso**: `load(initial = false, silent = true)`, sin spinner ni error si ya hay datos) y la pantalla lo lanza con `LiveEffect(viewModel) { viewModel.liveLoop() }` (`:core:designsystem`, `repeatOnLifecycle(STARTED)`). Al pasar a segundo plano se cancela y, 5 s después, se cierra la conexión. Filtra por el id mostrado (`requestId`, `shipmentId`, `incidentId`, `tripId`) en `match`.
- **Pantallas y eventos**: badge (`rememberUnreadCount`) y Notificaciones: `notification.created` (`data.unreadCount` se usa directo para el badge; la lista se recarga; respaldo 60 s). Chat: `message.posted` del mismo `requestId` + `carrierOrgId` y `counter_offer.updated` (respaldo 30 s). Conductor: cualquier `shipment.*`/`incident.*` (`driver_assigned` puede traer un envío nuevo). Envíos (lista y seguimiento) e incidencias: `shipment.status_changed`/`stop_updated`/`driver_assigned` + `incident.*` del envío mostrado. Detalle de solicitud (PYME): `bid.placed`, `counter_offer.updated`, `request.matched`, `auction.updated` del `requestId`; lista de solicitudes igual sin filtro. Transportista (Home, feed, detalle de carga, Mis ofertas): `auction.updated`, `bid.accepted`, `bid.rejected`, `counter_offer.updated` (el feed solo recarga si sigue en la primera página). Detalle de incidencia: `incident.*` + refresco lento de 30 s (los comentarios no emiten evento). Viajes: `trip_booking.updated`/`trip.cancelled` (Mis viajes, detalle por `tripId`, Mis reservas) y `smart_load.*`.
- Los eventos son **avisos**: nunca se pinta el payload (salvo `unreadCount`); se vuelve a pedir por REST. Una pantalla nueva en vivo: `liveLoop()` en su ViewModel + `LiveEffect` en el Route; en tests usa un `FakeRealtime` (`MutableSharedFlow` + `MutableStateFlow(LIVE)`; ver `feature/negotiation/src/test/.../FakeRealtime.kt`). En `androidTest`, `FakeKoinRule` registra `OfflineRealtime`.
- `:core:network` es JVM puro: sus tests (`SseParserTest`, `RealtimeClientTest` con `ktor-client-mock`) corren con `./gradlew :core:network:test` (no con `testDebugUnitTest`).

## CI
`.github/workflows/android.yml` corre `./gradlew assembleDebug testDebugUnitTest` (sin emulador) en cada cambio a `android/**` o `docs/api/**`. Los tests instrumentados siguen siendo locales.
