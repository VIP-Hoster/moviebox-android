package com.viphoster.moviebox.data
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

        @POST("/api/admin/sync")
    suspend fun adminSync(@Header("Authorization") bearer: String): Map<String, Any>

    @POST("/api/admin/media/public-all")
    suspend fun adminPublicAll(@Header("Authorization") bearer: String): Map<String, Any>

    @POST("/api/admin/media/private-all")
    suspend fun adminPrivateAll(@Header("Authorization") bearer: String): Map<String, Any>

    @POST("/api/admin/media/{fileId}/visibility")
    suspend fun setMediaVisibility(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Body body: Map<String, Boolean>
    ): Map<String, Any>

    @POST("/api/admin/media/{fileId}/adult")
    suspend fun setMediaAdult(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Body body: Map<String, Boolean>
    ): Map<String, Any>

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
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MovieBoxApi::class.java)
}
