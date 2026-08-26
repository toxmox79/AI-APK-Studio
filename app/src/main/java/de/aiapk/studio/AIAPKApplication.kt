package de.aiapk.studio

import android.app.Application
import de.aiapk.studio.data.AppDatabase
import de.aiapk.studio.data.AppRepository
import de.aiapk.studio.data.SettingsRepository
import de.aiapk.studio.security.SecureKeyStore

class AIAPKApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val secureKeyStore by lazy { SecureKeyStore() }
    val repository by lazy { AppRepository(database, settingsRepository, secureKeyStore) }
}
