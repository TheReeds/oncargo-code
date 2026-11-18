package pe.logyx.core.data

import pe.logyx.core.network.ApiException

/**
 * Errores de campo devueltos por la API (`error.field` + `error.message`), p. ej. RN07 (`LOGYX-NEG-001`: datos de contacto
 * en un texto libre) o `AUTH-006` (`currentPassword`). El estado de un formulario guarda un [FieldErrors] y cada campo
 * pinta `errors[campo]` bajo sí mismo; al editar el campo se limpia con [without].
 *
 * Uso típico en un ViewModel:
 * ```
 * repo.send(...).onFailure { e ->
 *     val fe = e.fieldErrors("notes", "amount")
 *     if (fe.isEmpty) message = UiMessage.error(e.userMessage()) else state = state.copy(errors = fe)
 * }
 * ```
 */
data class FieldErrors(val byField: Map<String, String> = emptyMap()) {
    val isEmpty: Boolean get() = byField.isEmpty()
    operator fun get(field: String): String? = byField[field]
    fun without(field: String): FieldErrors = if (field in byField) FieldErrors(byField - field) else this
    fun with(field: String, message: String): FieldErrors = FieldErrors(byField + (field to message))

    companion object {
        val NONE = FieldErrors()
    }
}

/** Nombre del campo del contrato al que se refiere el error (`ApiException.field`), si lo hay. */
val Throwable.errorField: String? get() = (this as? ApiException)?.field?.takeIf { it.isNotBlank() }

/**
 * Convierte el error en errores de campo **solo** si `error.field` es uno de [fields] (los que el formulario muestra);
 * si no, vacío y el llamador lo muestra como mensaje (snackbar/banner).
 */
fun Throwable.fieldErrors(vararg fields: String): FieldErrors {
    val field = errorField ?: return FieldErrors.NONE
    return if (field in fields) FieldErrors(mapOf(field to userMessage())) else FieldErrors.NONE
}

/** RN07: el texto contiene teléfono, email, URL o `@usuario`. */
val Throwable.isContactInfoRejected: Boolean get() = apiCode == "LOGYX-NEG-001"
