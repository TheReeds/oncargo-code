# LOGYX Android — Sistema de diseño Compose (`:core:designsystem`)

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
- **`LogyxBadge(text, tone, icon)`**, **`VerifiedBadge()`**, **`CountBadge(n)`**, `StatusPill(text, tone)` / `StatusPill(status.pill())`, `InfoChip` (dato no interactivo).

### Estructura
- **`LogyxScreen(title, subtitle, onBack, snackbar, actions, bottomBar) { padding -> }`** — Scaffold estándar con `LogyxTopBar` y host de mensajes.
- **`LogyxTopBar`**, **`LogyxBottomBar(items: List<LogyxNavItem>, selectedIndex, onSelect)`** (la usa `:app`).
- **`LogyxCard(onClick?)`**, `KpiTile`, `PriceTag`, `RouteLine`, `LoadCard`, `StopTimeline`, `TruckIllustration`, `AuctionCountdown`, `RouteMap`, `Refreshable`.
- Estados: `LoadingState`, **`SkeletonList` / `SkeletonLoadCard` / `SkeletonBox` / `Modifier.shimmer()`** (primer render de listas; reservan `LoadingState` para acciones bloqueantes), `EmptyState(title, message, actionLabel, onAction)` (camión + siguiente acción), `ErrorState(message, onRetry)` (camión averiado + Reintentar).

### Reputación, chat y evidencia (fase 3)
- **`TrustScoreBadge(score: Double?, onClick?)`** — Trust Score 0–100 (≥85 esmeralda, ≥70 petróleo, <70 ámbar); en `LoadCard`, comparación de ofertas y perfil. **`StarRating(value, onValueChange?, size)`** (interactivo 44–48 dp o solo lectura) y **`RatingRow(label, average)`**.
- **`ChatBubble(text, mine, time, sender, system, price, state: BubbleState, onRetry)`** (Sent/Sending/Failed; `system` = aviso centrado de contraoferta) y **`ChatComposer(value, onValueChange, onSend, error, sending)`** (error de RN07 bajo el campo; incluye `imePadding`).
- **`SignaturePad(state)`** + `rememberSignaturePadState()` (`toPng()`, `clear()`, `isEmpty`) y **`EvidenceTile(caption, image, pdf, status: TileStatus, progress, onClick)`** (miniatura con estado de subida).
- Etiquetas: `IncidentCategory.label`, `IncidentStatus.pill()`, `DocumentType.label`, `CounterOfferStatus.pill()`.

## Textos (español peruano, "tú")
- Botones: verbo + objeto ("Enviar oferta", "Asignar conductor", "Reintentar"). Nada de "OK", "Aceptar" suelto, "Submit".
- Conductor: frases cortas, imperativo/primera persona ("Llegué al recojo", "Carga recogida").
- Errores: qué pasó + qué hacer. Vacíos: qué falta + siguiente acción. Éxito: breve, sin signos de exclamación.
- Formatos: `S/ 1,850.00`, `28 nov · 08:00`, `2.5 t`, `1,010 km`. Fechas ISO solo en red.
- Textos visibles en `res/values/strings.xml` de cada módulo (`stringResource`/`pluralStringResource`); etiquetas de enums (`CargoType.label`, `status.pill()`…) también (`ds_…`), son `@Composable`. En ViewModels no hay `Context`: usa `UiMessage.success(R.string.x, args…)` o `UiText.of(R.string.x, args…)`; `MessageEffect`/`asString()` los resuelven. Literal solo para mensajes de la API.
- `RouteMap(…, contentDescription)` describe el mapa a TalkBack; usa estilo oscuro con tema oscuro.

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
