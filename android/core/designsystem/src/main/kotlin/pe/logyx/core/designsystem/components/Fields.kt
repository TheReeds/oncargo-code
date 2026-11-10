package pe.logyx.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pe.logyx.core.designsystem.LogyxText
import pe.logyx.core.designsystem.LogyxTheme
import pe.logyx.core.designsystem.R
import java.text.Normalizer

// ---------------------------------------------------------------------------------------------
// Marco común: etiqueta arriba, control, y debajo ayuda o error (qué pasó + qué hacer).
// ---------------------------------------------------------------------------------------------

@Composable
internal fun FieldFrame(
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    optional: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            if (optional) {
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.ds_required_suffix),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        content()
        if (error != null) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(16.dp).padding(top = 1.dp), tint = LogyxTheme.colors.dangerText)
                Spacer(Modifier.width(6.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = LogyxTheme.colors.dangerText)
            }
        } else if (hint != null) {
            Text(
                hint,
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun logyxFieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    val extra = LogyxTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = scheme.surface,
        unfocusedContainerColor = scheme.surface,
        disabledContainerColor = scheme.surfaceVariant.copy(alpha = 0.5f),
        errorContainerColor = scheme.surface,
        focusedBorderColor = scheme.primary,
        unfocusedBorderColor = extra.fieldBorder,
        errorBorderColor = extra.dangerText,
        disabledBorderColor = scheme.outlineVariant,
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        disabledTextColor = scheme.onSurfaceVariant,
        errorTextColor = scheme.onSurface,
        focusedPlaceholderColor = scheme.onSurfaceVariant,
        unfocusedPlaceholderColor = scheme.onSurfaceVariant,
        focusedPrefixColor = scheme.onSurfaceVariant,
        unfocusedPrefixColor = scheme.onSurfaceVariant,
        errorPrefixColor = scheme.onSurfaceVariant,
        focusedLeadingIconColor = scheme.primary,
        unfocusedLeadingIconColor = scheme.onSurfaceVariant,
        cursorColor = scheme.primary,
        errorCursorColor = extra.dangerText,
    )
}

private val FieldShape = RoundedCornerShape(12.dp)

/** Campo de texto con etiqueta arriba, ayuda y error. Altura táctil >= 52dp. */
@Composable
fun LogyxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    placeholder: String? = null,
    prefix: String? = null,
    suffix: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    optional: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 5,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    FieldFrame(label, modifier, hint, error, optional) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .semantics {
                    contentDescription = label
                    if (error != null) this.error(error)
                },
            enabled = enabled,
            readOnly = readOnly,
            textStyle = textStyle,
            placeholder = placeholder?.let { { Text(it) } },
            prefix = prefix?.let { { Text(it, style = textStyle) } },
            suffix = suffix?.let { { Text(it, style = textStyle) } },
            leadingIcon = leadingIcon?.let { { Icon(it, null) } },
            trailingIcon = trailing,
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            shape = FieldShape,
            colors = logyxFieldColors(),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Dinero
// ---------------------------------------------------------------------------------------------

/** Formatea la parte entera con comas mientras se escribe: `1850.5` -> `1,850.5`. */
object MoneyTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val dot = raw.indexOf('.').let { if (it < 0) raw.length else it }
        val out = StringBuilder()
        // map[i] = posición transformada del cursor tras i caracteres originales.
        val map = IntArray(raw.length + 1)
        var t = 0
        for (i in raw.indices) {
            if (i in 1 until dot && (dot - i) % 3 == 0) { out.append(','); t++ }
            map[i] = t
            out.append(raw[i]); t++
        }
        map[raw.length] = t
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = map[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int): Int {
                var best = 0
                for (i in 0..raw.length) if (map[i] <= offset) best = i
                return best
            }
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}

/** Limpia lo que escribe el usuario: solo dígitos y un punto, máx. 2 decimales. */
fun sanitizeMoneyInput(input: String): String {
    val cleaned = input.replace(',', '.').filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    if (firstDot < 0) return cleaned.trimStart('0').ifEmpty { if (cleaned.isEmpty()) "" else "0" }
    val intPart = cleaned.substring(0, firstDot).trimStart('0').ifEmpty { "0" }
    val dec = cleaned.substring(firstDot + 1).filter { it.isDigit() }.take(2)
    return "$intPart.$dec"
}

/** Convierte el texto del [MoneyField] (`1850.5`) a número, o null si está vacío. */
fun parseMoney(text: String): Double? = text.toDoubleOrNull()

/** Convierte un monto a texto editable sin separadores: `1850.0` -> `1850`. */
fun moneyToInput(amount: Double?): String = when {
    amount == null -> ""
    amount % 1.0 == 0.0 -> amount.toLong().toString()
    else -> "%.2f".format(java.util.Locale.US, amount)
}

/**
 * Campo de monto en soles. [value] es el texto crudo (`1850.5`, sin comas); se muestra `S/ 1,850.5`.
 * Usa [parseMoney] para obtener el número.
 */
@Composable
fun MoneyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    error: String? = null,
    placeholder: String? = "0.00",
    enabled: Boolean = true,
    optional: Boolean = false,
) {
    LogyxTextField(
        value = value,
        onValueChange = { onValueChange(sanitizeMoneyInput(it)) },
        label = label,
        modifier = modifier,
        hint = hint,
        error = error,
        placeholder = placeholder,
        prefix = "S/ ",
        enabled = enabled,
        optional = optional,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        visualTransformation = MoneyTransformation,
        textStyle = LogyxText.moneySmall.copy(color = MaterialTheme.colorScheme.onSurface),
    )
}

// ---------------------------------------------------------------------------------------------
// Campo de solo lectura que abre algo (menú, hoja, calendario)
// ---------------------------------------------------------------------------------------------

/** Campo que se ve como un input pero abre un selector al tocarlo. */
@Composable
fun LogyxPickerField(
    label: String,
    valueText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.ds_select),
    hint: String? = null,
    error: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = Icons.Rounded.KeyboardArrowDown,
    onClear: (() -> Unit)? = null,
    enabled: Boolean = true,
    optional: Boolean = false,
) {
    FieldFrame(label, modifier, hint, error, optional) {
        Box {
            OutlinedTextField(
                value = valueText.orEmpty(),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .semantics { contentDescription = "$label. ${valueText ?: placeholder}" },
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = { Text(placeholder) },
                leadingIcon = leadingIcon?.let { { Icon(it, null) } },
                trailingIcon = {
                    if (onClear != null && !valueText.isNullOrEmpty()) {
                        IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, stringResource(R.string.ds_clear)) }
                    } else if (trailingIcon != null) {
                        Icon(trailingIcon, null)
                    }
                },
                isError = error != null,
                singleLine = true,
                shape = FieldShape,
                colors = logyxFieldColors(),
            )
            // Capa clicable por encima (un TextField readOnly no propaga el click).
            Box(
                Modifier
                    .matchParentSize()
                    .padding(end = if (onClear != null && !valueText.isNullOrEmpty()) 52.dp else 0.dp)
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    ),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dropdown estilizado
// ---------------------------------------------------------------------------------------------

/** Lista desplegable para pocas opciones (< 8): tipo de carga, vehículo, conductor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LogyxDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    onSelected: (T) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.ds_select),
    hint: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    optional: Boolean = false,
    optionSupporting: (T) -> String? = { null },
    /** Si no es null y hay una opción elegida, el menú muestra "Quitar selección" al inicio. */
    onClear: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    FieldFrame(label, modifier, hint, error, optional) {
        ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected?.let(optionLabel).orEmpty(),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .semantics { contentDescription = "$label. ${selected?.let(optionLabel) ?: placeholder}" },
                placeholder = { Text(placeholder) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                isError = error != null,
                singleLine = true,
                shape = FieldShape,
                colors = logyxFieldColors(),
            )
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(14.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
            ) {
                if (onClear != null && selected != null) {
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = {
                            Text(
                                stringResource(R.string.ds_clear_selection),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { onClear(); expanded = false },
                    )
                }
                options.forEach { option ->
                    val isSelected = option == selected
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = {
                            Column {
                                Text(
                                    optionLabel(option),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                optionSupporting(option)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        trailingIcon = if (isSelected) {
                            { Icon(Icons.Rounded.Check, stringResource(R.string.ds_selected), tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                        onClick = { onSelected(option); expanded = false },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Combobox con búsqueda (ciudades y listas largas)
// ---------------------------------------------------------------------------------------------

private fun String.fold(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/**
 * Selector con búsqueda en una hoja inferior (mejor que un menú editable en móvil).
 * La búsqueda ignora tildes y mayúsculas. Úsalo para ciudades y listas de > 8 elementos.
 */
@Composable
fun <T> LogyxCombobox(
    label: String,
    options: List<T>,
    selected: T?,
    onSelected: (T?) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.ds_select),
    searchPlaceholder: String = stringResource(R.string.ds_search),
    hint: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    optional: Boolean = false,
    clearable: Boolean = false,
    optionSupporting: (T) -> String? = { null },
) {
    var open by remember { mutableStateOf(false) }
    LogyxPickerField(
        label = label,
        valueText = selected?.let(optionLabel),
        onClick = { open = true },
        modifier = modifier,
        placeholder = placeholder,
        hint = hint,
        error = error,
        enabled = enabled,
        optional = optional,
        onClear = if (clearable) ({ onSelected(null) }) else null,
        trailingIcon = Icons.Rounded.Search,
    )
    if (open) {
        var query by remember { mutableStateOf("") }
        val filtered = remember(query, options) {
            val q = query.fold().trim()
            if (q.isEmpty()) options else options.filter { optionLabel(it).fold().contains(q) }
        }
        LogyxBottomSheet(onDismiss = { open = false }, title = label) {
            LogyxTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.ds_search),
                placeholder = searchPlaceholder,
                leadingIcon = Icons.Rounded.Search,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.size(8.dp))
            if (filtered.isEmpty()) {
                Text(
                    stringResource(R.string.ds_no_results) + ". " + stringResource(R.string.ds_no_results_hint),
                    Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(filtered) { option ->
                        val isSelected = option == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable(role = Role.Button) { onSelected(option); open = false }
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    optionLabel(option),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                optionSupporting(option)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (isSelected) Icon(Icons.Rounded.Check, stringResource(R.string.ds_selected), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FieldsPreview() {
    LogyxTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LogyxTextField("", {}, "Correo", hint = "Usa el correo de tu empresa", placeholder = "tu@empresa.pe")
            LogyxTextField("hola", {}, "Contraseña", error = "La contraseña no es correcta. Revísala e inténtalo de nuevo.")
            MoneyField("1850.5", {}, "Tu oferta", hint = "Monto mínimo S/ 1,200.00")
            LogyxDropdown("Vehículo", listOf("Camión 5 t", "Van"), "Van", {}, { it })
            LogyxPickerField("Ciudad de origen", "Arequipa", {})
        }
    }
}
