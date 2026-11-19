# OnCargo Android — Sistema de diseño Compose (`:core:designsystem`)

Todo vive en `android/core/designsystem` (paquete `pe.logyx.core.designsystem`). **Los features solo usan estos componentes**:
nunca `OutlinedTextField`, `AlertDialog`, `ModalBottomSheet`, `SnackbarHost`, `FilterChip`, `Button`/`OutlinedButton`, `NavigationBar` ni `TopAppBar` directos.
Si falta un componente, se crea en `:core:designsystem` (con `@Preview` y una entrada en el catálogo), no local en el feature.
Dirección: 70% industrial / 30% "Friendly Mobility" (ver `docs/design/DESIGN.md`). Móvil = visual y rápido; conductor = acciones XL.

## Tokens
| Qué | Dónde | Uso |
|---|---|---|
| Paleta cruda | `LogyxPalette` (Ink900/700/500/400, Steel100/200, Petrol700/50/400, Emerald500/700/50, Amber500/700/50, Red600/700/50) | Solo dentro del tema |
| Esquema M3 | `MaterialTheme.colorScheme` | `primary` = petróleo, `surface`, `onSurfaceVariant`, `outlineVariant`, `error` |
| Extras | `LogyxTheme.colors` | `moneyStrong`/`moneyText` (esmeralda AA para botón/texto), `money` (relleno/icono, **nunca texto pequeño**), `moneyContainer`, `warning*`/`onWarningContainer` (ámbar AA), `dangerText` (rojo 700, AA), `dangerContainer`, `fieldBorder` (3:1), `infoContainer`, `route` |
| Tipografía | `MaterialTheme.typography` (Geist Sans) | `headlineSmall` títulos de sección, `titleMedium` tarjetas, `bodyMedium` cuerpo, `labelLarge` botones/etiquetas |
| Cifras | `LogyxText.moneyLarge/moneyMedium/moneySmall/mono/kpi` (Geist Mono, tabular) | Precios, placas, km, toneladas |
| Formas | `MaterialTheme.shapes` (6/8/10/16/24 dp); campos 12dp; botones 14dp (XL 18dp); chips pill | |
| Formatos | `Fmt.money` `S/ 1,850.00` · `Fmt.dateTime` `28 nov · 08:00` · `Fmt.tons` `2.5 t` · `Fmt.km` `1,010 km` | Nunca formatees a mano |

Regla de contraste: texto blanco solo sobre `primary`, `moneyStrong`, `dangerText`; texto de dinero con `moneyText`; ámbar/rojo como texto con `onWarningContainer`/`dangerText`.

## Catálogo de componentes
Todos con `@Preview`. En builds debug: login → botón **Catálogo UI** (`UiCatalogScreen`) muestra cada variante.

### Acciones
**`LogyxButton(text, onClick, modifier, variant, size, loading, enabled, icon, fullWidth = true)`**
- `variant`: `Primary` (acción principal, petróleo) · `Money` (ofertar/aceptar oferta, esmeralda) · `Secondary` (contorno) · `Ghost` (texto) · `Destructive` (cancelar/rechazar).
- `size`: `Medium` 48dp · `Large` 56dp (default) · `Xl` 72dp (**conductor**).
- `loading = true` muestra spinner, bloquea y anuncia "Procesando…". Un botón por pantalla debe ser `Primary`/`Money`.
```kotlin
LogyxButton("Enviar oferta", onSubmit, variant = ButtonVariant.Money, loading = state.submitting)
LogyxButton("Llegué al recojo", onAdvance, size = ButtonSize.Xl, loading = state.acting)
```

### Formularios
- **`LogyxTextField(value, onValueChange, label, hint, error, placeholder, prefix, suffix, leadingIcon, trailing, optional, singleLine, keyboardOptions, visualTransformation …)`** — etiqueta siempre arriba, `hint` ayuda, `error` = qué pasó + qué hacer (rojo con icono).
- **`MoneyField(value: String, onValueChange, label, hint, error)`** — prefijo `S/`, separador de miles es-PE mientras se escribe; el estado guarda texto crudo `"1850.5"`; usa `parseMoney(text): Double?` y `moneyToInput(Double?)`.
- **`LogyxDropdown(label, options, selected, onSelected, optionLabel, optionSupporting, onClear)`** — ≤ 8 opciones (tipo de carga, vehículo, conductor).
- **`LogyxCombobox(label, options, selected, onSelected(T?), optionLabel, clearable, searchPlaceholder)`** — hoja con búsqueda sin tildes; ciudades y listas largas.
- **`LogyxDateField(label, value: LocalDate?, onValueChange)`** / **`LogyxDateRangeField(label, start, end, onValueChange)`** — campos que abren `LogyxDatePicker` (atajos Hoy / Mañana) y `LogyxDateRangePicker` (Hoy / Próximos 7 días), localizados a es-PE. Usa `LocalDate` en el estado; convierte a ISO al enviar.
- **`LogyxTimeField(label, value: LocalTime?, onValueChange, hint, error)`** / `LogyxTimePicker` — hora 24 h (es-PE) para salidas; úsalo junto a `LogyxDateField` (fecha + hora se combinan en el ViewModel, hora de Lima).
- **`LogyxPickerField(label, valueText, onClick, …)`** — base para cualquier selector propio.
```kotlin
LogyxCombobox("Origen", cities, state.origin, vm::onOrigin, { it.name }, clearable = true)
MoneyField(state.amount, vm::onAmount, "Tu oferta", hint = "Mínimo ${Fmt.money(floor)}", error = state.amountError)
```

### Mensajes y confirmaciones
- **`UiMessage`** (`success/error/info`, `kind`, `actionLabel`) + **`rememberLogyxSnackbarState()`** + **`LogyxSnackbarHost`** (lo pinta `LogyxScreen`) + **`MessageEffect(message, snackbar, onShown)`**.
- **`AlertBanner(text, kind, title, actionLabel, onAction, onDismiss)`** — aviso persistente en pantalla (Success/Error/Warning/Info).
- **`LogyxConfirmDialog(title, message, confirmLabel, onConfirm, onDismiss, dismissLabel, destructive, loading)`** — **obligatorio** para aceptar oferta, cancelar solicitud, avanzar parada, cerrar sesión. Título = pregunta, mensaje = consecuencia, botón = verbo + objeto.
- **`LogyxBottomSheet(onDismiss, title) { … }`** — formularios cortos (ofertar), selección de conductor.

#### Emitir mensajes desde un ViewModel
El estado lleva `message: UiMessage?` y un `onMessageShown()` que lo limpia.
```kotlin
data class XUiState(…, val message: UiMessage? = null)

fun submit() = viewModelScope.launch {
    repo.send(…).fold(
        onSuccess = { _state.update { it.copy(message = UiMessage.success("Oferta enviada")) } },
        onFailure = { e -> _state.update { it.copy(message = UiMessage.error(e.userMessage())) } },
    )
}
fun onMessageShown() = _state.update { it.copy(message = null) }
```
```kotlin
val snackbar = rememberLogyxSnackbarState()
MessageEffect(state.message, snackbar, onMessageShown)
LogyxScreen(title = "Detalle", onBack = onBack, snackbar = snackbar) { padding -> … }
```
Éxito: sustantivo + participio ("Oferta enviada", "Solicitud cancelada"). Error: **qué pasó + qué hacer** ("No pudimos enviar tu oferta. Revisa tu conexión e inténtalo de nuevo."). Errores de carga de pantalla van en `ErrorState`, no en snackbar; errores de campo, en `error` del campo.

### Selección y estado
- **`RadioCardRow(selected, onClick) { … }`** (fila radio en tarjeta con check), **`InitialsAvatar(name, size, description)`**, **`LoadingMoreFooter()`** (pie de scroll infinito), **`UiText`** (texto literal o `@StringRes` para estado de ViewModel; `asString()` en la UI).
- **`LogyxFilterChip(label, selected, onClick, leadingIcon)`** + `ChipRow { }` — filtros (múltiple; una sola fila con scroll horizontal). **`SegmentedControl(options, selected, onSelect, optionLabel)`** — 2–4 opciones excluyentes.
- **`LogyxSwitchRow(label, checked, onCheckedChange, supporting?, enabled)`** — fila con interruptor (pausar/reanudar avisos, activar una opción); toda la fila es el área táctil (>= 48 dp) y se lee como un control. Ejemplo: «Avisos activos» en «Mis búsquedas» (`:feature:loads`).
- **`LogyxBadge(text, tone, icon)`**, **`VerifiedBadge()`**, **`CountBadge(n)`**, `StatusPill(text, tone)` / `StatusPill(status.pill())`, `InfoChip` (dato no interactivo).

### Estructura
- **`LogyxScreen(title, subtitle, onBack, snackbar, actions, bottomBar) { padding -> }`** — Scaffold estándar con `LogyxTopBar` y host de mensajes.
- **`LogyxTopBar`**, **`LogyxBottomBar(items: List<LogyxNavItem>, selectedIndex, onSelect)`** (la usa `:app`).
- **`LogyxCard(onClick?)`**, `KpiTile`, `PriceTag`, `RouteLine`, `LoadCard`, `StopTimeline`, `TruckIllustration`, `AuctionCountdown`, `RouteMap`, `Refreshable`.
- **`LiveIndicator(status)`** + **`ProvideRealtimeStatus(status) { … }`** — "En vivo" (esmeralda, punto que pulsa) / "Reconectando…" (ámbar) discreto en `LogyxTopBar`; `:app` provee `RealtimeRepository.status` una vez, no lo uses en features. Con `CONNECTING`/`OFFLINE` no pinta nada. Región viva + `contentDescription` para TalkBack.
- **`LiveEffect(key) { viewModel.liveLoop() }`** — corre el bloque solo con la pantalla en primer plano (`repeatOnLifecycle(STARTED)`); es el único punto por el que las pantallas se suscriben al tiempo real (sin UI propia).
- Estados: `LoadingState`, **`SkeletonList` / `SkeletonLoadCard` / `SkeletonBox` / `Modifier.shimmer()`** (primer render de listas; reservan `LoadingState` para acciones bloqueantes), `EmptyState(title, message, actionLabel, onAction)` (camión + siguiente acción), `ErrorState(message, onRetry)` (camión averiado + Reintentar).

### Cuenta: verificación y rol Lector (V13/V14)
- **`VerificationBanner(status, note)`** — aviso del estado de verificación (RN01): `PENDING` ámbar, `REJECTED`/`SUSPENDED` rojo, con la nota del operador; no pinta nada con `VERIFIED`/null. `VerificationStatus?.needsAttention` decide si mostrarlo. Se muestra **bajo la barra superior del inicio** (PYME y transportista) y en Cuenta, no en cada pantalla.
- **`LocalScreenBanner`** (CompositionLocal con un `@Composable`) — `LogyxScreen` lo pinta bajo `LogyxTopBar`; `:app` lo provee solo en las pantallas de inicio (`WithVerificationBanner`). Úsalo para cualquier aviso de cuenta que deba estar pegado a la barra sin tocar cada pantalla.
- **Patrón `Can` (Ola 1 zona D, espejo del `<Can>` web)** — `LocalPermissions` (permisos efectivos, `permissionsOf(user)`, lo provee `:app`) + **`Can(permission = OPERATE, fallback = {}) { … }`** y `rememberCan(p)` (`:core:designsystem/components/AccountStatus.kt`). Úsalo para bloques (formularios, FAB, filas de acciones); la información nunca se oculta.
- **`LocalReadOnly` / `ProvideReadOnly(readOnly) { }`** — rol Lector (`UserInfo.isReadOnly`, lo provee `:app`). **`LogyxButton(…, requiresOperate = true)` no se muestra** con el rol Lector (antes se deshabilitaba) y `ChatComposer` tampoco se pinta. `LogyxScreen` muestra **`ReadOnlyNotice`** ("Tu rol Lector solo permite consultar…") con el botón **«Pedir acceso»** si `:app` provee `LocalRequestAccess` (llama a `POST /organizations/me/access-requests` y avisa con un toast). Regla: todo botón que haga POST/PUT/PATCH/DELETE lleva `requiresOperate = true` (o va dentro de `Can { }`); cambiar contraseña, cerrar sesión y leer avisos no. Don't: deshabilitar en gris para el Lector, repetir el aviso dentro de la pantalla.
- **Avisos por persona (V16)**: `NotificationView.mine` → etiqueta «Tuyo»; filtro `SegmentedControl` Todas / Sin leer / **Solo míos** en `:feature:notifications`.
- `VerifiedBadge()` dice **"Verificado por OnCargo"** (solo con `rucVerified`, es decir, verificada por el operador).
- Errores: `Throwable.userMessage()` (ya trae el texto de `ORG-003/004` y `AUTH-007/008`), `Throwable.accessBlock`, y `FieldErrors` para `NEG-001`/400 con `field` (`:core:data`; ver `android/AGENTS.md`).
- **`StopContactBlock(stop, modifier, large = false, showDirections = true)`** (roadmap 1.3, `components/StopContact.kt`) — ventana horaria («Recibe de 08:00 a 12:00» / «Atiende…» en recojo), contacto («Ana Torres · 987 654 321»), botones «Llamar» (`ACTION_DIAL`) y «Cómo llegar» (`geo:0,0?q=dirección, ciudad`, respaldo Google Maps web) con descripción TalkBack, e «Indicaciones: …». Solo pinta lo que no es null; sin app de teléfono/mapas lo dice debajo. `large = true` en el conductor (56 dp), `showDirections = false` en el seguimiento de oficina. Helpers: `Context.dialPhone(phone)`, `Context.openDirections(query)`, `formatPhone`, `stopWindowText(stop)`, `StopView.directionsQuery()`. No abras intents `tel:`/`geo:` a mano en un feature.
- Conductor: `EvidencePhase` (`OPEN` panel de foto/firma · `NOT_YET`/`CLOSED` tarjeta que explica por qué no se pide) y aviso de evidencia descartada (`ClosedEvidenceNotice`, `AlertBanner` ámbar con "Entendido"); "Viajes anteriores" con `StatusPill`.

### Negociación, motivos y flota (V13–V15)
- **`LogyxReasonSheet(title, reasonLabel, value, onValueChange, confirmLabel, onConfirm, onDismiss, message, error, optional, destructive, loading, confirmEnabled, maxLength = 500, content)`** — patrón único de **acción con motivo** (cancelar solicitud o envío, cambiar vehículo, relevar conductor): título = pregunta, `message` = consecuencia, `content` (selector) entre el mensaje y el campo "Motivo", contador y aviso de RN07, botón destructivo con `requiresOperate`. El `error` del campo (`NEG-001`, `SHP-006`) se pinta bajo el texto. El motivo en el estado se corta con `.take(MAX_REASON_LENGTH)`.
- **`VehicleChoiceList(choices: List<VehicleChoice(vehicle, fit)>, selectedId, onSelect, weightKg, volumeM3, cargoType, error, currentId)`** — selector de vehículo con RN02 a la vista: los aptos se eligen; los que no quedan **deshabilitados con su motivo** (`VehicleFit.reason`: kg, tipo de carga, m³, en ruta/mantenimiento). Sin aptos avisa con `AlertBanner`; sin flota, vacío. `fit = vehicle.fitFor(request)` viene de `core:model` (única función de compatibilidad, usada también por el chip "Compatible" del feed). `error` = `CAP-001..005`/`SHP-007` (field `vehicleId`).
- **`BidStandingRow(standing: MyBidStanding)`** — "Tu oferta es la mejor" / "A menos de 5 % de la mejor" / "A menos de 15 % de la mejor" / "Más de 15 % sobre la mejor" + "Vas 2.º de 5"; con `vsBest` nulo solo la posición final. Nunca montos ajenos (RN04). Con `standing.position` (`TOP_TWO`/`LOWER_HALF`/`UPPER_HALF`, V22) agrega «Entre las 2 más bajas» / «Por debajo de la mediana» / «Por encima de la mediana» (`BidPosition.label`; no se repite si eres la mejor: `visiblePosition`).
- Etiquetas: `BidOutcome.pill()` (En subasta, Esperando a la PYME, Ganada, Perdida, Solicitud cancelada, Retirada), `CloseReason.label`, `auctionClosureText(auction)` ("Subasta cerrada el 28 nov · 08:00 · Se acabó el tiempo"), `BidStandingBand.label`. `RadioCardRow` acepta `enabled`.

### Reputación, chat y evidencia (fase 3)
- **`TrustScoreBadge(score: Double?, onClick?)`** — Trust Score 0–100 (≥85 esmeralda, ≥70 petróleo, <70 ámbar); en `LoadCard`, comparación de ofertas y perfil. **`StarRating(value, onValueChange?, size)`** (interactivo 44–48 dp o solo lectura) y **`RatingRow(label, average)`**.
- **`ChatBubble(text, mine, time, sender, system, price, state: BubbleState, onRetry)`** (Sent/Sending/Failed; `system` = aviso centrado de contraoferta) y **`ChatComposer(value, onValueChange, onSend, error, sending)`** (error de RN07 bajo el campo; incluye `imePadding`).
- **`SignaturePad(state)`** + `rememberSignaturePadState()` (`toPng()`, `clear()`, `isEmpty`) y **`EvidenceTile(caption, image, pdf, status: TileStatus, progress, onClick)`** (miniatura con estado de subida).
- Etiquetas fase 4: `TripStatus.pill()`, `BookingStatus.pill()`, `SmartLoadStatus.pill()`, `TripPricingMode.label`.
- Etiquetas: `IncidentCategory.label`, `IncidentStatus.pill()`, `DocumentType.label`, `CounterOfferStatus.pill()`.

## Textos (español peruano, "tú")
- Botones: verbo + objeto ("Enviar oferta", "Asignar conductor", "Reintentar"). Nada de "OK", "Aceptar" suelto, "Submit".
- Conductor: frases cortas, imperativo/primera persona ("Llegué al recojo", "Carga recogida").
- Errores: qué pasó + qué hacer. Vacíos: qué falta + siguiente acción. Éxito: breve, sin signos de exclamación.
- Formatos: `S/ 1,850.00`, `28 nov · 08:00`, `2.5 t`, `1,010 km`. Fechas ISO solo en red.
- Textos visibles en `res/values/strings.xml` de cada módulo (`stringResource`/`pluralStringResource`); etiquetas de enums (`CargoType.label`, `status.pill()`…) también (`ds_…`), son `@Composable`. En ViewModels no hay `Context`: usa `UiMessage.success(R.string.x, args…)` o `UiText.of(R.string.x, args…)`; `MessageEffect`/`asString()` los resuelven. Literal solo para mensajes de la API.
- `RouteMap(…, contentDescription)` describe el mapa a TalkBack; usa estilo oscuro con tema oscuro.
- **Ruta por carretera (ADR-0008):** pasa `path = state.route?.points` a `RouteMap`. El ViewModel la carga con
  `RoutesRepository.between(origen, destino)` (`:core:data`, caché en memoria; `RouteGeometry` y `Polyline` en `:core:model`;
  `RoutesApi` = `GET /api/v1/routes`, implementado por `LogyxApi`) y la guarda en `route: RouteGeometry?` del estado. Con `path`:
  línea continua por la vía y la etiqueta "Rutas © OpenStreetMap" (atribución ODbL, abajo a la derecha); sin `path` (cargando, sin
  red o en tests con `RoutesRepository.NONE`): recta punteada. Ruta `#0B4F6C` en claro, `#38A3C9` en oscuro.

## Do / Don't
- DO `LogyxButton` con `loading` en vez de deshabilitar sin explicación. DON'T `Button`/`TextButton` de M3.
- DO confirmar acciones irreversibles o con dinero. DON'T ejecutar `accept`/`cancel`/`advance` al primer toque.
- DO `contentDescription` en iconos accionables; área táctil ≥ 48dp (los componentes ya lo cumplen; en `clickable` propios usa `minimumInteractiveComponentSize`).
- DO esmeralda solo para dinero/oportunidad/éxito. DON'T texto blanco sobre `money` (#10B981); usa `moneyStrong`.
- DO `SkeletonList` al cargar feeds. DON'T spinner a pantalla completa para listas.
- DO un `UiMessage` por resultado de acción. DON'T `Toast` ni `SnackbarHostState` crudo.
- DON'T hex ni `Color(0x…)` en features: tokens del tema. DON'T `fontFamily` suelto: `LogyxText`/typography.
- DON'T dependencias entre features; la navegación la cablea `:app`.

## Agregar un componente nuevo
1. Archivo en `core/designsystem/.../components/`, solo con tokens del tema, `@Preview` claro (y oscuro si aplica).
2. Entrada en `catalog/UiCatalog.kt`. 3. Strings en `res/values/strings.xml` (prefijo `ds_`). 4. Documentarlo aquí.

## Marca (OnCargo)
- El producto se llama **OnCargo** (antes LOGYX); el tema/paquetes conservan el nombre técnico `Logyx*`/`pe.logyx`. Ver `docs/brand.md`.
- Logotipo: `pe.logyx.core.designsystem.R.drawable.oncargo_logo` (cambia solo a la variante oscura con `drawable-night-nodpi`) y `oncargo_logo_on_dark` para cabeceras siempre oscuras (login). Símbolo/ícono de app: `mipmap/ic_launcher*` (fondo `#081F46`).
- Copy visible: "OnCargo" (p. ej. "Coordina todo por el chat de OnCargo").

## Oferta rápida y «cobras neto» (Ola 1 zona C, 2026-11-18)
- Hoja de oferta (`:feature:loads` `BidSheet`): chips de **atajos de monto** (`BidShortcut`: Sugerido, −5 %, −10 %, Piso; `amount()` nunca baja del piso) con `LogyxFilterChip`; `NetBox` = «Cobras S/ X neto» (`Text` con `liveRegion = Polite`) y un `TextButton` que despliega el desglose (valor de venta, IGV, comisión, IGV de la comisión). El cálculo es `priceBreakdown()` de `:core:model` (misma aritmética en céntimos que el backend).
- `VehicleChoiceList(blocked = …, hints = …)`: los vehículos que la agenda bloquea para esas fechas salen deshabilitados con el motivo («Ocupado · libre el 23 nov») y los demás con «Libre en esas fechas» / «N ofertas pendientes en esas fechas»; `CAP-006` se muestra con `AlertBanner(MessageKind.Warning)` antes de enviar.
- Lo recordado vive en `BidPreferences` (DataStore `logyx_bid_prefs`): último vehículo, nota y atajo; la nota solo se usa si el vehículo sigue en la flota de la sesión.
- Duplicar viaje: `PublishTrip(from)` + `PublishTripRoute(fromTripId)`; `AlertBanner` informativo «Copia de un viaje anterior».

## Ola 2 zona P: publicar desde el móvil (2.16)
- **Nueva solicitud** (`:feature:requests`, `NewRequestRoute` → `NewRequestScreen`): formulario en una columna (ruta, carga, fecha, referencia y valor declarado), «Mis direcciones» (`LogyxCombobox` con `optionSupporting` ciudad · dirección, `clearable`) por extremo cuando hay libreta, sección **colapsable «Contactos y horarios»** (`LogyxTextField`, `LogyxTimeField`; se abre sola si un error cae ahí o si la copia trae contactos) y una `LogyxCard` de **cotización en vivo** (precio sugerido con `LogyxText.moneyLarge` y `moneyText`, piso y km) con espera de 400 ms. Publicar = `LogyxButton(Money, requiresOperate)`; los errores de la API con `field` (RN07 en `cargoDescription`/direcciones, ventanas, teléfono) caen bajo su campo y los demás en snackbar. Una `Idempotency-Key` por pantalla (doble toque o reintento de red no duplican).
- **Duplicar**: botón «Duplicar solicitud» (`Secondary`, `requiresOperate`) en el detalle de la solicitud y en el de un envío (PYME); abre `NewRequest(from)` con `AlertBanner` informativo «Solicitud prellenada» y la fecha en el mismo día de la semana siguiente (`RequestView.nextWeekPickup`).
- **Plantillas** (`TemplatesRoute`): lista con la programación en una línea («Todos los lunes · Publica sola · Próximo recojo: 23 nov») y «Publicar ahora» → `LogyxBottomSheet` con `LogyxDateField`. La programación se edita en la web.
- **Subir documento** (`:feature:documents`, `UploadDocumentSheet`): barra inferior «Subir documento» (`requiresOperate`) → hoja con `LogyxDropdown` de tipo (guía de remisión, comprobante, orden de servicio, otro) y dos acciones: **Tomar foto** (cámara del sistema con `TakePicture` + `FileProvider`, sin permiso `CAMERA`) o **Elegir archivo** (`OpenDocument` JPG/PNG/PDF). Valida formato y 10 MB antes de subir; las fotos JPEG se comprimen.
- Cancelar con motivo ya existía (`LogyxReasonSheet`, opcional, RN07).

## Conductor sin señal, recojo y ajustes (Ola 2 zona X, 2026-11-19)
- **`SyncStatusChip(count, state: SyncChipState, onClick)`** (`components/OfflineSync.kt`) — indicador compacto «3 acciones por sincronizar» / «1 acción por sincronizar» (plural `ds_sync_count`) con estado `Queued`/`Sending`/`WaitingSignal` (ámbar)/`Failed` (rojo); con 0 no pinta nada; al tocarlo abre una `LogyxBottomSheet` con la lista y «Reintentar ahora». ≥ 48 dp, descripción TalkBack.
- **`NumberField(value, onValueChange, label, unit, hint, error, optional)`** — número con unidad (kg, m³): teclado decimal, solo dígitos y un separador, sufijo con la unidad, cifra en mono. El estado guarda texto; conviértelo en el ViewModel (`parseWeightKg`).
- **`StopTimeline`** ahora muestra el peso medido (`Fmt.kg`), «Recibió Ana Torres · 45678912» y la insignia ámbar «Registrado por la oficina: motivo» cuando `arrivalOfficeReason`/`completionOfficeReason` vienen.
- Etiquetas nuevas: `IncidentCategory.label` (8 categorías + «Incidencia» para `UNKNOWN`), `IncidentSeverity.pill()` (Baja/Media/Alta/Crítica), `AdjustmentType.label`, `AdjustmentStatus.pill()` (Por responder/Aceptado/Rechazado/Retirado), `DocumentType.PICKUP_PHOTO` = «Foto de la carga». `Fmt.kg(3200.0)` → `3,200 kg`.
