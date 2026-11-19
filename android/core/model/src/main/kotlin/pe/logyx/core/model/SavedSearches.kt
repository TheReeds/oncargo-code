package pe.logyx.core.model

import kotlinx.serialization.Serializable

/**
 * Búsqueda guardada del transportista (V22, roadmap 2.3): `GET/POST /saved-searches`, `PUT/DELETE /saved-searches/{id}`.
 * Todos los filtros son opcionales (al menos uno, `LOGYX-ALT-001`). Con `active = false` no hay avisos de cargas nuevas.
 * Fechas `yyyy-MM-dd`; ciudades en código (`LIMA`). Los canales del aviso se eligen en las preferencias de aviso («Alertas de carga»).
 */
@Serializable
data class SavedSearch(
    val id: String,
    val name: String,
    val originCity: String? = null,
    val destinationCity: String? = null,
    val cargoType: CargoType? = null,
    val pickupFrom: String? = null,
    val pickupTo: String? = null,
    val maxWeightKg: Double? = null,
    val vehicleId: String? = null,
    val active: Boolean = true,
    val createdAt: String? = null,
) {
    /** El cuerpo de `PUT` para cambiar solo `active` (el PUT reemplaza nombre y filtros: se reenvían tal cual). */
    fun toRequest(active: Boolean = this.active) = SavedSearchRequest(
        name, originCity, destinationCity, cargoType, pickupFrom, pickupTo, maxWeightKg, vehicleId, active,
    )

    /** true si trae al menos un filtro (el servidor lo exige, `ALT-001`). */
    val hasFilters: Boolean
        get() = originCity != null || destinationCity != null || cargoType != null || pickupFrom != null ||
            pickupTo != null || maxWeightKg != null || vehicleId != null

    /**
     * Resumen del filtro en piezas cortas («Lima → Juliaca», «General», «Hasta 5 t»…), listas para unir con « · ».
     * Los textos (ciudades, tipos, pesos, fechas) los pone quien llama, para usar `strings.xml` y el catálogo de ciudades.
     */
    fun summaryParts(
        city: (String) -> String,
        cargo: (CargoType) -> String,
        weight: (Double) -> String,
        dates: (from: String?, to: String?) -> String,
        vehicle: (String) -> String?,
        anyCity: String,
    ): List<String> = buildList {
        if (originCity != null || destinationCity != null) {
            add("${originCity?.let(city) ?: anyCity} → ${destinationCity?.let(city) ?: anyCity}")
        }
        cargoType?.let { add(cargo(it)) }
        if (pickupFrom != null || pickupTo != null) add(dates(pickupFrom, pickupTo))
        maxWeightKg?.let { add(weight(it)) }
        vehicleId?.let { id -> vehicle(id)?.let(::add) }
    }
}

/** Cuerpo de `POST /saved-searches` y de `PUT /saved-searches/{id}` (`name` obligatorio, máx. 80). Los null no se envían. */
@Serializable
data class SavedSearchRequest(
    val name: String,
    val originCity: String? = null,
    val destinationCity: String? = null,
    val cargoType: CargoType? = null,
    val pickupFrom: String? = null,
    val pickupTo: String? = null,
    val maxWeightKg: Double? = null,
    val vehicleId: String? = null,
    val active: Boolean? = null,
)

/** Largo máximo del nombre de una búsqueda guardada (`@Size` del backend). */
const val MAX_SAVED_SEARCH_NAME = 80
