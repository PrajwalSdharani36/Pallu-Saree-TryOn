package com.pallu.tryon.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Health(
    val status: String,
    @SerialName("store_name") val storeName: String,
    val engine: String,
    @SerialName("engine_status") val engineStatus: EngineStatus? = null,
)

/** Whether the server's try-on engine is ready: "ok", "checking", "warning", "error" or "demo". */
@Serializable
data class EngineStatus(val state: String = "ok", val message: String = "")

@Serializable
data class Saree(
    val id: String,
    val barcode: String,
    val name: String,
    val fabric: String = "",
    val color: String = "",
    val price: Int = 0,
    val description: String = "",
    @SerialName("image_url") val imageUrl: String,
    @SerialName("analysis_status") val analysisStatus: String = "pending",
    val active: Boolean = true,
)

@Serializable
data class SessionItem(
    val id: String,
    val position: Int,
    val status: String,
    @SerialName("drape_style") val drapeStyle: String = "nivi",
    val favorite: Boolean,
    val error: String = "",
    @SerialName("result_url") val resultUrl: String? = null,
    val saree: Saree,
) {
    val isWorking get() = status == "queued" || status == "running"
    val isDone get() = status == "done" && resultUrl != null
    val isFailed get() = status == "failed"
}

@Serializable
data class TryOnSession(
    val id: String,
    val code: String,
    @SerialName("qr_payload") val qrPayload: String,
    val status: String,
    val mode: String,
    @SerialName("drape_style") val drapeStyle: String,
    @SerialName("customer_label") val customerLabel: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("has_photo") val hasPhoto: Boolean,
    @SerialName("photo_expires_at") val photoExpiresAt: String? = null,
    @SerialName("photo_ttl_minutes") val photoTtlMinutes: Int = 30,
    val total: Int,
    val done: Int,
    val failed: Int,
    val items: List<SessionItem>,
) {
    val isClosed get() = status == "closed" || status == "expired"
    val isGenerating get() = status == "generating"
}

@Serializable
data class DrapeStyle(val id: String, val description: String)

@Serializable
data class CreateSessionBody(
    val barcodes: List<String> = emptyList(),
    @SerialName("saree_ids") val sareeIds: List<String> = emptyList(),
    @SerialName("drape_style") val drapeStyle: String = "nivi",
    @SerialName("customer_label") val customerLabel: String = "",
)

@Serializable
data class AddItemBody(val barcode: String? = null, @SerialName("saree_id") val sareeId: String? = null)

@Serializable
data class FavoriteBody(val favorite: Boolean)

@Serializable
data class RedrapeBody(@SerialName("drape_style") val drapeStyle: String)

@Serializable
data class SessionUpdateBody(
    @SerialName("drape_style") val drapeStyle: String? = null,
    val mode: String? = null,
)

enum class AppMode { STAFF, KIOSK }

/** Drape styles shown to customers; ids match the backend's DRAPE_STYLES. */
data class DrapeInfo(val id: String, val label: String, val description: String)

val DRAPES = listOf(
    DrapeInfo("nivi", "Nivi", "Classic: neat pleats at the waist, pallu over the left shoulder."),
    DrapeInfo("bengali", "Bengali", "Box pleats, pallu brought round to rest on the right shoulder."),
    DrapeInfo("gujarati", "Gujarati", "Seedha pallu: spread across the front so the whole design shows."),
    DrapeInfo("nauvari", "Nauvari", "Maharashtrian nine-yard drape, tucked at the back like a dhoti."),
    DrapeInfo("modern", "Modern", "Slim pre-pleated drape with a pinned, narrow pallu."),
)

fun drapeLabel(id: String): String = DRAPES.firstOrNull { it.id == id }?.label ?: id.replaceFirstChar { it.uppercase() }
