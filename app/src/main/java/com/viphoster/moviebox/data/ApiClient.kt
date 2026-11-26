package com.viphoster.moviebox.data
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

private val safeDoubleAdapter = object : TypeAdapter<Double?>() {
    override fun write(out: JsonWriter, value: Double?) { out.value(value) }
    override fun read(reader: JsonReader): Double? {
        if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
        val raw = reader.nextString().trim()
        return raw.toDoubleOrNull()
    }
}

private val safeIntAdapter = object : TypeAdapter<Int?>() {
    override fun write(out: JsonWriter, value: Int?) { out.value(value) }
    override fun read(reader: JsonReader): Int? {
        if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
        val raw = reader.nextString().trim()
        return raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt()
    }
}

private val safeLongAdapter = object : TypeAdapter<Long?>() {
    override fun write(out: JsonWriter, value: Long?) { out.value(value) }
    override fun read(reader: JsonReader): Long? {
        if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
        val raw = reader.nextString().trim()
        return raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong()
    }
}

private val safeGson = GsonBuilder()
    .registerTypeAdapter(Double::class.java, safeDoubleAdapter)
    .registerTypeAdapter(Double::class.javaObjectType, safeDoubleAdapter)
    .registerTypeAdapter(Int::class.java, safeIntAdapter)
    .registerTypeAdapter(Int::class.javaObjectType, safeIntAdapter)
    .registerTypeAdapter(Long::class.java, safeLongAdapter)
    .registerTypeAdapter(Long::class.javaObjectType, safeLongAdapter)
    .create()

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface MovieBoxApi {
    @GET("/api/auth/config")
    suspend fun getAuthConfig(): AuthConfigResponse

    @POST("/api/login")
    suspend fun adminLogin(@Body req: LoginRequest): LoginResponse

    @POST("/api/auth/google")
    suspend fun authGoogle(@Body req: GoogleAuthRequest): GoogleAuthResponse

    @POST("/api/auth/google/bind")
    suspend fun bindGoogleInvite(@Body req: GoogleBindRequest): GoogleAuthResponse

    @GET("/api/me")
    suspend fun getMe(@Header("Authorization") bearer: String): MeResponse

    @GET("/api/catalog")
    suspend fun getCatalog(@Header("Authorization") bearer: String): CatalogResponse

    @GET("/api/user/profile")
    suspend fun getUserProfile(@Header("Authorization") bearer: String): ProfileResponse

    @POST("/api/user/profile")
    suspend fun saveUserProfile(
        @Header("Authorization") bearer: String,
        @Body body: UserProfileData
    ): ProfileResponse
}

object ApiClient {
    const val BASE_URL = "https://moviebox-cloud-1.onrender.com"

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: MovieBoxApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create(safeGson))
        .build()
        .create(MovieBoxApi::class.java)
}
