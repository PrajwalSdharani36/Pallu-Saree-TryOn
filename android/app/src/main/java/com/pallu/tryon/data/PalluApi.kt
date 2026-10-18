package com.pallu.tryon.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface PalluApi {
    @GET("api/health")
    suspend fun health(): Health

    @GET("api/drape-styles")
    suspend fun drapeStyles(): List<DrapeStyle>

    @GET("api/sarees")
    suspend fun sarees(@Query("q") q: String = "", @Query("limit") limit: Int = 200): List<Saree>

    @GET("api/sarees/barcode/{barcode}")
    suspend fun sareeByBarcode(@Path("barcode") barcode: String): Saree

    // ---- staff ----
    @POST("api/sessions")
    suspend fun createSession(@Body body: CreateSessionBody): TryOnSession

    @GET("api/sessions")
    suspend fun recentSessions(@Query("limit") limit: Int = 20): List<TryOnSession>

    @GET("api/sessions/{ref}")
    suspend fun session(@Path("ref") ref: String): TryOnSession

    @POST("api/sessions/{ref}/items")
    suspend fun addItem(@Path("ref") ref: String, @Body body: AddItemBody): TryOnSession

    @DELETE("api/sessions/{ref}/items/{itemId}")
    suspend fun removeItem(@Path("ref") ref: String, @Path("itemId") itemId: String): TryOnSession

    // ---- kiosk ----
    @PATCH("api/sessions/{ref}")
    suspend fun updateSession(@Path("ref") ref: String, @Body body: SessionUpdateBody): TryOnSession

    @GET("api/session-lookup")
    suspend fun lookup(@Query("q") q: String): TryOnSession

    @Multipart
    @POST("api/sessions/{ref}/photo")
    suspend fun uploadPhoto(
        @Path("ref") ref: String,
        @Part photo: MultipartBody.Part,
        @Part("consent") consent: RequestBody,
        @Part("mode") mode: RequestBody,
        @Part("drape_style") drapeStyle: RequestBody,
    ): TryOnSession

    @POST("api/sessions/{ref}/items/{itemId}/retry")
    suspend fun retry(@Path("ref") ref: String, @Path("itemId") itemId: String): TryOnSession

    @POST("api/sessions/{ref}/items/{itemId}/favorite")
    suspend fun favorite(
        @Path("ref") ref: String,
        @Path("itemId") itemId: String,
        @Body body: FavoriteBody,
    ): TryOnSession

    @POST("api/sessions/{ref}/finish")
    suspend fun finish(@Path("ref") ref: String): TryOnSession

    @DELETE("api/sessions/{ref}/photo")
    suspend fun deletePhoto(@Path("ref") ref: String): TryOnSession
}
