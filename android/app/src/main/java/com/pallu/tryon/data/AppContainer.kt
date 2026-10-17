package com.pallu.tryon.data

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Tiny manual dependency container (no DI framework needed for an app this size). */
class AppContainer(private val context: Context) {
    val settingsStore = SettingsStore(context)

    @Volatile
    private var cached: Repository? = null

    /** Repository for the current server/key; recreated only when they change. */
    fun repository(settings: AppSettings): Repository {
        val url = normalizeServerUrl(settings.serverUrl).takeIf { it.toHttpUrlOrNull() != null } ?: "http://localhost/"
        val existing = cached
        if (existing != null && existing.baseUrl == url && existingKey == settings.apiKey) return existing
        return Repository(context, url, settings.apiKey).also {
            cached = it
            existingKey = settings.apiKey
        }
    }

    @Volatile
    private var existingKey: String = ""

    suspend fun repository(): Repository = repository(settingsStore.current())

    /** Checks the server is reachable AND the key is right. Returns the store name, or an error sentence. */
    suspend fun probe(serverUrl: String, apiKey: String): Result<Health> {
        val url = normalizeServerUrl(serverUrl)
        if (url.toHttpUrlOrNull() == null) {
            return Result.failure(IllegalStateException("That server address doesn't look right. Use something like 192.168.1.20:8000."))
        }
        val repo = Repository(context, url, apiKey.trim())
        return try {
            val health = repo.api.health()
            repo.api.sarees(limit = 1) // 401 here means a wrong key
            Result.success(health)
        } catch (e: Exception) {
            Result.failure(IllegalStateException(repo.describe(e), e))
        }
    }
}
