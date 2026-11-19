package pe.logyx.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import pe.logyx.core.model.BidShortcut

/** Lo último que usó esta persona al ofertar (oferta rápida, roadmap 1.10). Se guarda en el dispositivo. */
data class BidRecall(val vehicleId: String? = null, val notes: String = "", val shortcut: BidShortcut? = null)

interface BidPreferences {
    suspend fun recall(): BidRecall
    suspend fun remember(recall: BidRecall)

    companion object {
        /** Sin memoria (tests y pantallas sin almacenamiento). */
        val NONE: BidPreferences = object : BidPreferences {
            override suspend fun recall() = BidRecall()
            override suspend fun remember(recall: BidRecall) = Unit
        }
    }
}

private val Context.bidDataStore: DataStore<Preferences> by preferencesDataStore(name = "logyx_bid_prefs")

/**
 * DataStore. Las notas se guardan junto al vehículo y la pantalla solo las usa si ese vehículo sigue en la flota de la sesión
 * actual: no se filtran a otra cuenta que use el mismo dispositivo.
 */
class DataStoreBidPreferences(context: Context) : BidPreferences {
    private val store = context.applicationContext.bidDataStore
    private val vehicleKey = stringPreferencesKey("vehicle")
    private val notesKey = stringPreferencesKey("notes")
    private val shortcutKey = stringPreferencesKey("shortcut")

    override suspend fun recall(): BidRecall {
        val p = store.data.first()
        return BidRecall(p[vehicleKey], p[notesKey].orEmpty(), p[shortcutKey]?.let { s -> BidShortcut.entries.firstOrNull { it.name == s } })
    }

    override suspend fun remember(recall: BidRecall) {
        store.edit {
            if (recall.vehicleId != null) it[vehicleKey] = recall.vehicleId else it.remove(vehicleKey)
            if (recall.notes.isNotBlank()) it[notesKey] = recall.notes.trim() else it.remove(notesKey)
            if (recall.shortcut != null) it[shortcutKey] = recall.shortcut.name else it.remove(shortcutKey)
        }
    }
}
