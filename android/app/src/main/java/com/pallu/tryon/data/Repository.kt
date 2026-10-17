package com.pallu.tryon.data

import android.content.Context
import coil.ImageLoader
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** One API connection for a given server + key. Rebuilt by [AppContainer] when settings change. */
class Repository(context: Context, val baseUrl: String, apiKey: String) {

    private val json = Json { ignoreUnknownKeys = true }

    val http: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("X-API-Key", apiKey).build())
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: PalluApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(PalluApi::class.java)

    /** Coil loader that sends the store key with every image request. */
    val imageLoader: ImageLoader = ImageLoader.Builder(context.applicationContext)
        .okHttpClient(http)
        .crossfade(true)
        .build()

    fun url(path: String?): String? = path?.let { baseUrl.trimEnd('/') + "/" + it.trimStart('/') }

    suspend fun uploadPhoto(sessionId: String, jpeg: ByteArray, mode: String, drapeStyle: String): TryOnSession {
        val text = "text/plain".toMediaType()
        val part = MultipartBody.Part.createFormData(
            "photo", "customer.jpg", jpeg.toRequestBody("image/jpeg".toMediaType())
        )
        return api.uploadPhoto(
            sessionId, part,
            "true".toRequestBody(text), mode.toRequestBody(text), drapeStyle.toRequestBody(text),
        )
    }

    /** Turn any failure into one sentence a shop assistant can act on. */
    fun describe(e: Throwable): String = when (e) {
        is HttpException -> {
            val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
            val detail = runCatching {
                val d = json.parseToJsonElement(body).jsonObject["detail"]
                when (d) {
                    is JsonPrimitive -> d.content
                    is JsonObject -> d["message"]?.jsonPrimitive?.content ?: d.toString()
                    else -> d?.toString()
                }
            }.getOrNull()
            when (e.code()) {
                401 -> "The store key was rejected. Check it in Settings."
                404 -> detail ?: "Not found."
                410 -> "This session has ended. Ask the staff for a new QR code."
                else -> detail ?: "Server error ${e.code()}."
            }
        }
        is IOException -> "Can't reach the store server at $baseUrl. Check the Wi-Fi and that the server is running."
        else -> e.message ?: "Something went wrong."
    }
}
