package de.aiapk.studio.data

import de.aiapk.studio.security.SecureKeyStore

class AppRepository(
    private val db: AppDatabase,
    val settings: SettingsRepository,
    private val secureKeyStore: SecureKeyStore
) {
    val dao: AppDao get() = db.dao()

    suspend fun saveProvider(
        id: Long?, name: String, type: String, baseUrl: String, model: String, apiKey: String
    ): Long {
        val aliasValue = if (apiKey.isBlank() && id != null) {
            db.dao().getProvider(id)?.keyAlias.orEmpty()
        } else secureKeyStore.encrypt(apiKey)
        val entity = ProviderEntity(
            id = id ?: 0,
            name = name.trim(), type = type,
            baseUrl = baseUrl.trimEnd('/'), model = model.trim(), keyAlias = aliasValue
        )
        return if (id == null) db.dao().insertProvider(entity) else { db.dao().updateProvider(entity); id }
    }

    fun apiKey(provider: ProviderEntity): String = runCatching {
        secureKeyStore.decrypt(provider.keyAlias)
    }.getOrDefault("")
}
