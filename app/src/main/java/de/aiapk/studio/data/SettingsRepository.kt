package de.aiapk.studio.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aiapk_settings")

data class StudioSettings(
    val darkMode: Boolean = false,
    val repairPasses: Int = 5,
    val autoApply: Boolean = true
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val darkMode = booleanPreferencesKey("dark_mode")
        val repairPasses = intPreferencesKey("repair_passes")
        val autoApply = booleanPreferencesKey("auto_apply")
    }

    val settings: Flow<StudioSettings> = context.dataStore.data.map { p ->
        StudioSettings(
            darkMode = p[Keys.darkMode] ?: false,
            repairPasses = p[Keys.repairPasses] ?: 5,
            autoApply = p[Keys.autoApply] ?: true
        )
    }

    suspend fun setDarkMode(value: Boolean) = context.dataStore.edit { it[Keys.darkMode] = value }
    suspend fun setAutoApply(value: Boolean) = context.dataStore.edit { it[Keys.autoApply] = value }
    suspend fun setRepairPasses(value: Int) = context.dataStore.edit { it[Keys.repairPasses] = value.coerceIn(1, 10) }
}
