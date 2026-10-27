package com.pallu.tryon.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pallu_settings")

data class AppSettings(
    val serverUrl: String = "",
    val apiKey: String = "",
    val mode: AppMode? = null,
    val storeName: String = "",
    val kioskIdleSeconds: Int = 90,
    /** Mirror cameras: true = front (selfie) camera, false = back camera. Saved per device. */
    val scanFront: Boolean = true,
    val photoFront: Boolean = true,
    /** Mirror look: false = dark (default), true = light ivory. Saved per device. */
    val kioskLight: Boolean = false,
) {
    val isConfigured get() = serverUrl.isNotBlank() && apiKey.isNotBlank() && mode != null
}

class SettingsStore(private val context: Context) {
    private object Keys {
        val url = stringPreferencesKey("server_url")
        val key = stringPreferencesKey("api_key")
        val mode = stringPreferencesKey("mode")
        val store = stringPreferencesKey("store_name")
        val idle = intPreferencesKey("kiosk_idle_s")
        val scanFront = booleanPreferencesKey("kiosk_scan_front")
        val photoFront = booleanPreferencesKey("kiosk_photo_front")
        val light = booleanPreferencesKey("kiosk_light")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            serverUrl = p[Keys.url] ?: "",
            apiKey = p[Keys.key] ?: "",
            mode = p[Keys.mode]?.let { runCatching { AppMode.valueOf(it) }.getOrNull() },
            storeName = p[Keys.store] ?: "",
            kioskIdleSeconds = p[Keys.idle] ?: 90,
            scanFront = p[Keys.scanFront] ?: true,
            photoFront = p[Keys.photoFront] ?: true,
            kioskLight = p[Keys.light] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.url] = s.serverUrl
            p[Keys.key] = s.apiKey
            if (s.mode != null) p[Keys.mode] = s.mode.name else p.remove(Keys.mode)
            p[Keys.store] = s.storeName
            p[Keys.idle] = s.kioskIdleSeconds
        }
    }

    /** Remembers which camera the mirror uses for the QR code and for the photo. */
    suspend fun setCameras(scanFront: Boolean? = null, photoFront: Boolean? = null) {
        context.dataStore.edit { p ->
            if (scanFront != null) p[Keys.scanFront] = scanFront
            if (photoFront != null) p[Keys.photoFront] = photoFront
        }
    }

    suspend fun setKioskLight(light: Boolean) {
        context.dataStore.edit { it[Keys.light] = light }
    }

    suspend fun clearMode() {
        context.dataStore.edit { it.remove(Keys.mode) }
    }
}

/** "192.168.1.20:8000" -> "http://192.168.1.20:8000/" */
fun normalizeServerUrl(raw: String): String {
    var u = raw.trim()
    if (u.isEmpty()) return u
    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
    if (!u.endsWith("/")) u += "/"
    return u
}
