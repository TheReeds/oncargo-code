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
./gradlew assembleDebug testDebugUnitTest :core:network:test :core:model:test   # verificación obligatoria
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
./gradlew connectedDebugAndroidTest      # app/src/androidTest: RoleHomeTest, AccountStatusTest (rol Lector y banner de verificación; sin ejecutar aún en emulador), BidFlowTest, DriverAdvanceTest (con evidencia), ChatTest (RN07), TripsFlowTest (PymeBookingFlowTest, CarrierTripBookingsFlowTest, PublishTripFlowTest, RejectBookingReasonFlowTest, SmartLoadFlowTest; fakes en `FakeTrips.kt`: `FakeTrips`, `FakeSmartLoads`, `SmartData`)
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
:core:data            Android lib. Repositorios (interfaces + Default*), AuthRepository/SessionStore (DataStore + AES-GCM con Keystore) y renovación de sesión, FieldErrors/accessBlock, dataModule()
:core:designsystem    Android lib. Tema (`LogyxTheme`), Geist Sans/Mono (res/font, OFL), componentes y mapa MapLibre
:feature:auth         Login (+ olvidé mi contraseña), Cuenta (cambiar contraseña, cerrar sesión en todos los dispositivos, verificación)
:feature:loads        Transportista: Home "Tu actividad", feed de LoadCard + chips, detalle con mapa + bottom sheet de oferta, mis ofertas
:feature:requests     PYME: mis solicitudes, detalle con comparación de ofertas y aceptar/cancelar
:feature:shipments    Envíos (PYME y transportista): lista, seguimiento con mapa + StopTimeline, asignar conductor
:feature:driver       Modo conductor: viaje activo, siguiente parada, botón único; evidencia de entrega (foto/firma, cola de subida) y bloqueo RN11
:feature:negotiation  Chat por solicitud (envío optimista, RN07 bajo el campo, tiempo real SSE con respaldo 30 s) y lista de hilos (PYME)
:feature:incidents    Reportar incidencia (categoría + descripción + foto opcional), lista y detalle con comentarios
:feature:reputation   Perfil público de organización (Trust Score) y calificar tras la entrega (`canReview`)
:feature:documents    Documentos/evidencias del envío: miniaturas, visor a pantalla completa, PDF por intent externo
:feature:trips        Fase 4. Transportista: Mis viajes, Publicar viaje, Detalle con reservas (confirmar/rechazar), Cargas de retorno (oferta con el `vehicleId` sugerido), Sugerencias Smart Load. PYME: pestaña "Viajes" (buscar con filtros, reservar espacio, Mis reservas con cancelar). Datos: `TripsRepository`/`SmartLoadsRepository`
:feature:notifications Centro de notificaciones por persona (V16: «Tuyo», filtro «Solo míos») + `UnreadCounter`/`rememberUnreadCount` (badge por tiempo real `notification.created`, respaldo 60 s)
```
Contra-ofertas: la PYME propone desde la comparación de ofertas (`:feature:requests`) y el transportista acepta/rechaza en "Mis ofertas" (`:feature:loads`).
**Evidencia de entrega (RN06):** `EvidenceRepository` (`:core:data`) comprime a JPEG <= 2 MB, guarda en `filesDir/evidence` (cola persistente) y sube por `POST /shipments/{id}/documents`; "Entregado" se habilita con >= 1 `DELIVERY_PHOTO` subida (o en cola por falta de señal: el cierre se envía después de la foto, ver "Conductor sin señal"). Captura con `TakePicture` (sin permiso CAMERA). Un único `FileProvider` vive en `:app` (`${applicationId}.fileprovider`, `res/xml/file_paths.xml` con los cache-path de driver/incidents/documents): no declares otro provider en un feature (choca al fusionar manifiestos).
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
Un 401 intenta **una** renovación con el refresh token (ver "Sesión del móvil"); solo si no se puede, cierra sesión (`dataModule`).

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

## Sesión del móvil (F24, ADR-0009)
- **Login con `refresh = true`**: access token de 1 h + `refreshToken` (30 días, opaco y **rotativo**: cada `POST /auth/refresh` entrega uno nuevo y el anterior queda usado; reutilizarlo revoca toda la sesión del dispositivo, `LOGYX-AUTH-009`).
- **Dónde vive**: `DefaultAuthRepository` (`:core:data`) implementa `AuthRepository` **y** `SessionRefresher` (`:core:network`). `LogyxApi` (parámetro `refresher`) y `RealtimeClient` le piden `refreshAfterUnauthorized(tokenRechazado)` ante un 401:
  un `Mutex` serializa las renovaciones y quien llega tarde ve que el token ya cambió (`RETRY` sin gastar otro refresh); resultados `RETRY` (repite la petición original **una** vez), `SESSION_ENDED` (sin refresh, `401/400` del refresh → `clearLocal` + `onUnauthorized`, como antes) y `UNAVAILABLE` (sin red/5xx/429: se conserva la sesión y la llamada falla con error de red). Si tras renovar sigue el 401, se cierra sesión (no hay bucle). Login, refresh, logout y forgot-password van con `auth = false` (no renuevan).
- **SSE**: ante 401 o `stream.expired` (vence el JWT) renueva y reconecta **de inmediato** con el token nuevo; si el token recién renovado también da 401 cierra sesión (`freshToken`).
- **Almacenamiento del refresh token** (decisión): DataStore (`SessionStore`) con el valor **cifrado con AES-256-GCM** (`KeystoreSecretCipher`, clave no exportable del **Android Keystore**, IV aleatorio por cifrado, `iv‖ciphertext` en Base64). Sin dependencias nuevas (no Tink ni `security-crypto`, deprecada): `javax.crypto` + Keystore hacen lo mismo que ellas por debajo. Si no se puede descifrar (clave invalidada, datos corruptos) se trata como ausente. El access token (1 h) y el `UserInfo` van en claro como antes; `allowBackup=false`, así que el cifrado no viaja a otro dispositivo. `SessionStorage`/`SecretCipher` son interfaces: los tests usan `MemoryStorage`.
- **Logout**: borra lo local primero y revoca el refresh con `POST /auth/logout {refreshToken}` a mejor esfuerzo (5 s, sin bloquear). `logoutAll()` (`POST /auth/logout-all`) cierra todos los dispositivos. `changePassword()` guarda la sesión nueva (access + refresh) que devuelve el servidor. `forgotPassword()` devuelve el mensaje idéntico del servidor.
- `refreshUser()` (`GET /auth/me`): al iniciar, al abrir Cuenta y con cada `notification.created` (p. ej. `ORGANIZATION_VERIFICATION`) para que el banner de verificación y los permisos se actualicen sin reiniciar. `AuthRepository.sessionUser` da el usuario actual.
- UI (`:feature:auth`): Login → "¿Olvidaste tu contraseña?" abre `ForgotPasswordSheet` (email → mensaje idéntico; el enlace del correo abre la web `/restablecer`); Cuenta → "Cambiar contraseña" (`ChangePasswordSheet`, errores `AUTH-006` bajo "Contraseña actual" y `AUTH-010` bajo la nueva), "Cerrar sesión en todos los dispositivos" (confirmación) y estado de verificación.

## Roles, verificación y errores comunes (V13/V14)
- **Modelos al día** con `openapi.yaml` hasta V15 (`core/model`): `UserInfo` (`rucValid`, `verificationStatus/Note`, `permissions`), `RequestView` (`compatibleVehicleTypes`, `cancelReason`, `republishedFrom`, `myBidStanding`, `bestBid*`), `AuctionView.closedAt`, `BidStatus.WITHDRAWN`, `CounterOfferView.proposedByCarrier`/`EXPIRED`, `ShipmentView` (`pickupDate`, `vehicleId`, `loadGroupId`, `cancellation*`), `DriverView.active`, `SmartLoadView.bids`, `IncidentView.shipmentOutcome`, `NotificationType`/`NotificationTarget` nuevos, y los cuerpos de V13 en `V13Requests.kt` (`CancelRequestBody`, `CancelShipmentRequest`, `ChangeVehicleRequest`, `RescheduleRequest`, `AssignDriverRequest.reason`, `AcceptSmartLoadRequest`). **Enums tolerantes:** los de estado traen `UNKNOWN` con default (`RequestStatus`, `AuctionStatus`, `BidStatus`, `ShipmentStatus`, `CounterOfferStatus`, `Role`, `NotificationType`) y los nulables caen en `null`; las listas de enums (`compatibleVehicleTypes`, `permissions`) descartan lo desconocido (`LenientEnumListSerializer`). Un `when` sobre esos enums necesita rama `UNKNOWN`.
- **Permisos** (`core/model/Access.kt`): `UserInfo.canOperate`, `canManageUsers`, `canManageOrganization`, `canManageDrivers`, `isReadOnly` (rol Lector). Si la sesión es anterior a V14 y no trae `permissions`, se derivan del rol (misma tabla que el backend). El conductor tiene solo `READ` pero **no** es lector (`isReadOnly = false`): sus pantallas no se bloquean con `canOperate`.
- **Rol Lector, de forma central** (`:core:designsystem`, `AccountStatus.kt`): `:app` provee `LocalReadOnly` con `user.isReadOnly`, `LocalPermissions` (`permissionsOf(user)`) y `LocalRequestAccess` («Pedir acceso», `AccessRepository` → `POST /organizations/me/access-requests`); `LogyxScreen` pinta la franja "Tu rol Lector solo permite consultar…" con el botón «Pedir acceso», y **`LogyxButton(..., requiresOperate = true)` no se muestra** (V16, roadmap 1.7: ocultar, no deshabilitar; `ChatComposer` tampoco). Para bloques: **`Can(permission = OPERATE) { … }`** / `rememberCan()`. Marca con `requiresOperate = true` todo botón que dispare un POST/PUT/PATCH/DELETE (ofertar, aceptar, contraofertar, asignar, cancelar, publicar, reservar, reportar, calificar…); ya están marcados los de las pantallas existentes. Las acciones de autoservicio (cambiar contraseña, cerrar sesión, leer avisos) no se marcan. El backend manda: ante `AUTH-007/008` el mensaje del servidor llega por `userMessage()`.
- **Verificación (RN01)**: `VerificationBanner(status, note)` (PENDING ámbar; REJECTED/SUSPENDED rojo, con la nota del operador) se muestra bajo la barra superior del **inicio** de PYME (Mis solicitudes) y de transportista (`WithVerificationBanner` en `LogyxApp.kt` provee `LocalScreenBanner`, que `LogyxScreen` pinta) y en Cuenta. `VerifiedBadge()` dice "Verificado por OnCargo" (perfil público con `rucVerified`, `LoadCard`, ofertas). Los avisos `ORGANIZATION_VERIFICATION` abren Cuenta (`NotificationView.destination()`, `core/model/NotificationRouting.kt`, probado).
- **Errores comunes** (`core/data/Results.kt`, `FieldErrors.kt`): `Throwable.accessBlock` (`AccessBlock.NOT_VERIFIED/SUSPENDED/READ_ONLY/ADMIN_ONLY`, por código `LOGYX-ORG-003/004`, `AUTH-007/008`) y `userMessage()`, que para `ORG-003/004` usa el texto de la app (marca OnCargo, "revisa Cuenta") y para el resto el del backend. En ofertar/aceptar/reservar/publicar viaje basta `UiMessage.error(e.userMessage())`, o `e.accessBlock?.let { … }` si se quiere un aviso persistente.
- **RN07 genérico** (`FieldErrors`): `e.fieldErrors("notes", "amount")` devuelve `FieldErrors` solo si `ApiException.field` es uno de esos campos del formulario (`LOGYX-NEG-001` y cualquier 400 con `field`); el estado guarda `errors: FieldErrors`, el campo pinta `errors["notes"]` en `error =` y al editarlo `errors.without("notes")`. Vacío → mostrar `userMessage()` como mensaje. `e.isContactInfoRejected` identifica `NEG-001`.

## Negociación, envíos y solicitudes (V13–V15, android-2)
- **Compatibilidad de flota (RN02, F09/F35)**: una sola función en `core:model/Rules.kt`: `VehicleView.fitFor(request): VehicleFit` (`FITS`/`UNAVAILABLE`/`WEIGHT`/`TYPE`/`VOLUME`, mismo orden que `CAP-002/001/003/004`) con la matriz `CargoType.compatibleVehicleTypes` (espejo de `VehicleCargoCompatibility`) y `RequestView.suitableVehicleTypes` (lista del servidor; si viene vacía, la matriz local). `canCarry` e `isCompatibleWith` (chip "Compatible" del feed y de Home) salen de ahí; no hay lógica de kg suelta. Selector: `VehicleChoiceList` (aptos elegibles, no aptos deshabilitados con su motivo).
- **Endpoints nuevos en `LogyxApi`** (+ repositorios con default `UnsupportedOperationException` para no romper fakes): `withdrawBid` (`POST /bids/{id}/withdraw`), `cancelRequest(id, reason?)`, `reopenRequest`, `republishRequest` (devuelve la solicitud NUEVA), `cancelShipment`, `changeVehicle` (`PUT /shipments/{id}/vehicle`), `assignDriver(id, driverId, reason?)` (relevo), `acceptSmartLoad(id, AcceptSmartLoadRequest)`. `proposeCounterOffer` ya servía al transportista.
- **Contraoferta bidireccional**: `CounterOfferView.isPendingFor(viewerIsCarrier)` decide quién ve Aceptar/Rechazar (quien la propuso solo ve "Esperando respuesta"). Transportista: en `LoadDetail` ("Tu oferta": posición, contraoferta de la PYME, Contraofertar, Retirar con confirmación; solo si `canManageMyBid`). PYME: en la comparación de ofertas de `:feature:requests`. `EXPIRED`/`WITHDRAWN` se muestran como pills.
- **Mis ofertas (B5)**: `RequestView.myBidOutcome` (`BidOutcome`: En subasta, Esperando a la PYME, Ganada, Perdida, Solicitud cancelada, Retirada) + `BidStandingRow` (nunca montos ajenos) + `auctionClosureText` (motivo y fecha de cierre). La API devuelve ahora también las cerradas (F15), con filtro Todas/Activas/Cerradas.
- **Acciones con motivo**: `LogyxReasonSheet` (cancelar solicitud opcional, cancelar envío obligatorio, cambiar vehículo, relevar conductor). Errores de campo: `NEG-001` en `reason`/`notes`/`message`; `CAP-*`/`SHP-007` bajo el selector de vehículo (field `vehicleId`); `SHP-004`/`FLT-003` bajo el conductor (`driverId`); `SHP-006` en `reason`; `SHP-003`/`TRK-005`/`BID-005`/`AUC-003`/`REQ-004` como mensaje. `ShipmentView.isCancellable` (PENDING sin incidencia), `isReassignable`, `needsDriver` ("Sin conductor").
- **Solicitud PYME (A7)**: `canReopenAuction` (cierre `TIMEOUT`) → hoja con fecha opcional; `canRepublish` (CANCELLED) → hoja con fecha y navega a la nueva (`onOpenRequest`); "Republicada de…" con `republishedFrom`; la PYME no publica desde el móvil (F38).
- **Smart Load en lote (F12)**: "Ofertar este plan" con un `MoneyField` por carga (pista del piso RN03 de 75 %; el servidor manda), notas (300, NEG-001 inline), tarjeta "Ofertas enviadas" con `SmartLoadView.bids`; `TRP-011/012` por `userMessage()`.
- **Límites de texto (alineados con los `@Size` del backend)**: notas de oferta/viaje 300, mensaje de contraoferta 500, motivos 500 (`MAX_REASON_LENGTH`), motivo de rechazo de reserva 300, reserva (descripción 300, direcciones 200), incidencia y comentario 2000, reseña 1000, chat 1000.

## Conductor: evidencia (RN06/F14) e historial (F22)
- La evidencia (`DELIVERY_PHOTO`/`SIGNATURE`) solo se ofrece entre la **llegada** y el **cierre** de la parada de entrega (`DriverUiState.evidencePhase`: `OPEN` = panel de foto/firma; `NOT_YET` justo antes de llegar y `CLOSED` con el viaje entregado/cancelado = tarjeta que explica por qué no hay botones).
- `EvidenceRepository`: `LOGYX-DOC-008` (la entrega ya se cerró) y `LOGYX-DOC-007` (ya no eres el conductor asignado) **cierran la subida**: el archivo de la cola se borra, el ítem pasa a `EvidenceState.CLOSED` y nunca se reintenta; la pantalla muestra un aviso ("Estas evidencias no se subieron…", `closedEvidence`) hasta que el conductor lo descarta (`dismissClosedEvidence`). Los demás fallos siguen en `FAILED` y se reintentan cada 20 s. La oficina del transportista ya no sube evidencia: la app no tiene esa opción (los documentos del envío son de solo lectura; la foto de una incidencia se sube como `OTHER`, que sigue permitido).
- "Viajes anteriores": `ShipmentsRepository.driverHistory()` = `GET /driver/shipments?scope=HISTORY` (hasta 10), al final de la pantalla del viaje y en el estado vacío.

## Conductor sin señal, contactos y seguimiento compartido (Ola 1 zona B; roadmap v2 1.3 y 1.11)
- **Modelos**: `StopView` (`arrivedRecordedAt`/`completedRecordedAt` = hora del servidor; `arrivedAt`/`completedAt` = hora del dispositivo; `contactName`, `contactPhone` 9 dígitos, `windowFrom`/`windowTo` `HH:mm`, `instructions`; solo las partes los reciben) y `ShipmentView` (`code` `OC-…`, `customerReference`, `requesterOrgId`, `carrierOrgId`). DTOs nuevos en `core/model/Tracking.kt` (`StopEventRequest`, `CreateShareLinkRequest`, `ShareLinkView`/`ShareLinkCreated`, `TrackingErrors`, `StopView.hasContactInfo`). Tests: `ZoneBDriverTrackingTest`.
- **API**: `arriveAtStop/completeStop(id, stopId, occurredAt?)` (cuerpo `{occurredAt}` solo si viene; idempotentes: repetir = 200) y `createShareLink(id, expiresInDays?)`. `ShipmentsRepository.arrive/complete(…, occurredAt)` tienen default que delega en la versión sin hora (los fakes viejos siguen compilando).
- **`DriverTripCache`** (`:core:data`, `DriverOffline.kt`): `filesDir/driver/active-trip.json` = `{trip, savedAt, ownerId}` (JSON con `LogyxJson`, escritura atómica tmp+rename). Se guarda con cada respuesta del servidor, `save(null)` lo borra (sin viaje activo) y solo lo lee el mismo usuario. Sin red (`ApiException.isNetwork`) el ViewModel muestra lo guardado con «Sin conexión · datos de hh:mm» (hora de Lima con `Fmt`).
- **`DriverActionQueue`** (`filesDir/driver/actions.json`, lista de `PendingStopAction {id, shipmentId, stopId, op, occurredAt, createdAt, needsPhoto, ownerId}`): el botón XL encola con la hora del dispositivo y el viaje en pantalla es `base.withPending(cola)` (PENDING→ARRIVED→COMPLETED; todas cerradas → DELIVERED). `flush()` es FIFO y serializado: 200 → se quita y manda el envío del servidor; 400 `TRK-008` → se repite al momento **sin** `occurredAt` (y se guarda así); red/5xx/429/401 → se detiene (`QueueStall.NETWORK`); error definitivo (`TRK-001/003/004/005/006`, 404, 403) → se descarta esa acción y las siguientes del mismo envío, se recarga y se explica (`TRK-006` → `photoMissingRejected`, `TRK-005` → `incidentRejected`, resto → `actionError`). Reintentos: bucle de ~20 s (el mismo de la evidencia), pull-to-refresh/`onResume`, cada señal de `liveLoop` (incluye `stream.resync` al reconectar) y cuando la foto termina de subir.
- **Entrega y foto (RN06)**: el `complete` de entrega (`needsPhoto`) no sale mientras haya una `DELIVERY_PHOTO` local sin subir (`PendingStopAction.waitsForPhoto`). En la UI, «Entregado» se habilita con una foto subida **o** guardada esperando señal (`EvidenceItem.awaitingSignal`: QUEUED/UPLOADING o FAILED por red, `offline = true`); una foto que falló por el servidor no cuenta. Pendientes: «Se enviará al recuperar señal» (aviso `PendingSyncNotice`, tile de la foto y snackbar `DriverNotice.QUEUED`). El botón muestra carga como máximo 4 s; luego el toque queda en la cola.
- **Contacto por parada**: `StopContactBlock` (`:core:designsystem`) en la siguiente parada del conductor (`large`, con «Llamar» `ACTION_DIAL` y «Cómo llegar» `geo:0,0?q=`, con respaldo web de Google Maps) y en el seguimiento PYME/transportista (tarjeta «Contactos y horarios», sin «Cómo llegar»). Solo pinta lo que viene.
- **Seguimiento (`:feature:shipments`)**: `code` en la tarjeta de la lista, subtítulo e `InfoRow` del detalle; «Ref.» con `customerReference`. PYME con `OPERATE` (`Can { }` + `requiresOperate`): «Compartir seguimiento» → `createShareLink` → `ACTION_SEND` texto con la URL (`ShareTrackingEffect`). `SHL-002/003/004` y `AUTH-007` por `userMessage()`.
- **DI**: `dataModule` registra `DriverTripCache` y `DriverActionQueue` (dueño = `AuthRepository.sessionUser.id`); `driverModule` usa `viewModel { DriverTripViewModel(get() ×7) }` (`viewModelOf` no respeta defaults). En `androidTest`, `FakeKoinRule` los registra en memoria ligados a los fakes.

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
- **Cliente**: `RealtimeClient` (`:core:network`, Kotlin puro) abre `GET /api/v1/events/stream` con el token actual; `SseParser` es incremental (WHATWG). Reconecta con espera exponencial (1 s a 30 s) enviando `Last-Event-ID`; timeout de request infinito y de socket 60 s (el servidor manda `: ping` cada ~20 s). Tras una reconexión (o `stream.resync`) emite `stream.resync` sintético; `stream.ready` y `stream.expired` no llegan a las pantallas. Un 401 o `stream.expired` renuevan la sesión (`SessionRefresher`) y reconectan al instante con el token nuevo; solo si la sesión terminó llama a `onUnauthorized` (cierra sesión). `status: StateFlow<RealtimeStatus>` (CONNECTING, LIVE, RECONNECTING, OFFLINE).
- **Repositorio**: `RealtimeRepository.events` (`SharedFlow`, una sola conexión compartida con `WhileSubscribed(5 s)`: sin suscriptores no hay conexión) y `refreshSignals(types, fallbackMs, match)`: emite por cada evento de `types` que cumpla `match`, por cada `stream.resync` y, **solo si el stream no está LIVE**, un tick de respaldo cada `fallbackMs`.
- **Indicador en vivo**: `LiveIndicator` (`:core:designsystem`) pinta "En vivo" (LIVE) o "Reconectando…" (RECONNECTING) dentro de `LogyxTopBar`; `CONNECTING`/`OFFLINE` no muestran nada (sin conexión abierta no es un fallo). `:app` (`RoleShell`) publica `RealtimeRepository.status` con `ProvideRealtimeStatus`; los features no lo conocen. Leer `status` no abre la conexión.
- **Solo en primer plano**: cada ViewModel expone `suspend fun liveLoop()` (colecta `refreshSignals` y hace un refresco **silencioso**: `load(initial = false, silent = true)`, sin spinner ni error si ya hay datos) y la pantalla lo lanza con `LiveEffect(viewModel) { viewModel.liveLoop() }` (`:core:designsystem`, `repeatOnLifecycle(STARTED)`). Al pasar a segundo plano se cancela y, 5 s después, se cierra la conexión. Filtra por el id mostrado (`requestId`, `shipmentId`, `incidentId`, `tripId`) en `match`.
- **Pantallas y eventos**: badge (`rememberUnreadCount`) y Notificaciones: `notification.created` (`data.unreadCount` se usa directo para el badge; la lista se recarga; respaldo 60 s). Chat: `message.posted` del mismo `requestId` + `carrierOrgId` y `counter_offer.updated` (respaldo 30 s). Conductor: cualquier `shipment.*`/`incident.*` (`driver_assigned` puede traer un envío nuevo). Envíos (lista y seguimiento) e incidencias: `shipment.status_changed`/`stop_updated`/`driver_assigned` + `incident.*` del envío mostrado. Detalle de solicitud (PYME): `bid.placed`, `counter_offer.updated`, `request.matched`, `auction.updated` del `requestId`; lista de solicitudes igual sin filtro. Transportista (Home, feed, detalle de carga, Mis ofertas): `auction.updated`, `bid.accepted`, `bid.rejected`, `counter_offer.updated` (el feed solo recarga si sigue en la primera página). Detalle de incidencia: `incident.*` + refresco lento de 30 s (los comentarios no emiten evento). Viajes: `trip_booking.updated`/`trip.cancelled` (Mis viajes, detalle por `tripId`, Mis reservas) y `smart_load.*`.
- Los eventos son **avisos**: nunca se pinta el payload (salvo `unreadCount`); se vuelve a pedir por REST. Una pantalla nueva en vivo: `liveLoop()` en su ViewModel + `LiveEffect` en el Route; en tests usa un `FakeRealtime` (`MutableSharedFlow` + `MutableStateFlow(LIVE)`; ver `feature/negotiation/src/test/.../FakeRealtime.kt`). En `androidTest`, `FakeKoinRule` registra `OfflineRealtime`.
- `:core:network` es JVM puro: sus tests (`SseParserTest`, `RealtimeClientTest` con `ktor-client-mock`) corren con `./gradlew :core:network:test` (no con `testDebugUnitTest`).

## CI
`.github/workflows/android.yml` corre `./gradlew assembleDebug testDebugUnitTest` (sin emulador) en cada cambio a `android/**` o `docs/api/**`. Los tests instrumentados siguen siendo locales.

## Ola 1 zona C: oferta rápida y «cobras neto» (2026-11-18, agente C)
- **`:core:model`**: `Pricing.kt` (`priceBreakdown(monto, tasa)` en céntimos, espejo de `CommissionPolicy`: S/ 1,180 al 8 % → neto 1,085.60; `BidShortcut` con `amount()` que nunca baja del piso; `VehicleEligibility`/`FleetEligibility`/`CommissionInfo`/`BidWarning`), `Rules.kt` (`fitForBid`/`canBid`: para **ofertar** «en ruta» no bloquea, solo mantenimiento; `fitFor`/`canCarry` siguen pidiendo «disponible ahora» para el cambio de vehículo de un envío).
- **`:core:network`/`:core:data`**: `LogyxApi.vehicleEligibility(requestId)` y `commission()`; `LoadsRepository.eligibility()/commissionRate()`; `BidPreferences` (DataStore `logyx_bid_prefs`: último vehículo, nota y atajo).
- **`:feature:loads`**: `LoadDetailViewModel.openBidSheet()` preselecciona el último vehículo (o el apto de menor capacidad), carga la agenda y la comisión; `bookableVehicles` excluye los ocupados esas fechas; `matchingShortcut`, `netBreakdown`. Hoja de oferta: chips Sugerido/−5 %/−10 %/Piso, `NetBox` («Cobras S/ X neto» + desglose plegable), aviso `CAP-006`, `VehicleChoiceList(blocked, hints)`. Tests: `QuickBidTest`, `PricingTest`.
- **`:feature:trips`**: duplicar viaje (`PublishTrip(from)`, `duplicateForm`, botón «Duplicar viaje» en el detalle).
- Pendiente (Ola 2): desglose del precio adjudicado en el envío (PYME), encadenar ofertas entre cargas (la hoja es por carga), documentos de flota.
