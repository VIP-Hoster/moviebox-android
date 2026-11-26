package com.viphoster.moviebox.data

import com.google.gson.annotations.SerializedName

data class LoginRequest(val password: String)
data class LoginResponse(val token: String? = null, val role: String? = null, val detail: String? = null)
data class AuthConfigResponse(@SerializedName("google_client_id") val googleClientId: String? = null)
data class GoogleAuthRequest(val credential: String, val device: String = "Android App")
data class GoogleBindRequest(val credential: String, val code: String, val device: String = "Android App")
data class GoogleAuthResponse(
    val status: String? = null,
    val token: String? = null,
    val role: String? = null,
    val email: String? = null,
    val name: String? = null,
    val detail: String? = null
)
data class MeResponse(
    val authenticated: Boolean = false,
    val role: String? = null,
    val name: String? = null,
    val email: String? = null,
    @SerializedName("allow_18") val allow18: Boolean = false
)
data class EpisodeItem(
    val id: String = "",
    val title: String? = null,
    val name: String? = null,
    val season: Int? = 1,
    val episode: Int? = 1
)
data class MediaItem(
    val id: String = "",
    @SerializedName("file_id") val fileId: String? = null,
    val title: String? = null,
    val name: String? = null,
    val year: String? = null,
    val category: String? = null,
    val overview: String? = null,
    val description: String? = null,
    val poster: String? = null,
    val rating: Double? = null,
    @SerializedName("vote_average") val voteAverage: Double? = null,
    val tags: List<String>? = emptyList(),
    val episodes: List<EpisodeItem>? = emptyList(),
    val public: Boolean? = true,
    @SerializedName("is_adult") val isAdult: Boolean? = false
) {
    fun displayTitle(): String = (title ?: name ?: "Untitled").trim()
    fun displayOverview(): String = (overview ?: description ?: "").trim()
    fun displayRating(): Double = rating ?: voteAverage ?: 0.0
    fun playableFileId(): String {
        val firstEp = episodes?.firstOrNull()?.id
        return when {
            !firstEp.isNullOrBlank() -> firstEp
            !fileId.isNullOrBlank() -> fileId
            else -> id
        }
    }
}
data class CatalogResponse(val items: List<MediaItem> = emptyList())
data class UserProfileData(
    val watching: List<String> = emptyList(),
    val watched: List<String> = emptyList(),
    @SerializedName("plan_to_watch") val planToWatch: List<String> = emptyList()
)
data class ProfileResponse(
    val profile: UserProfileData? = null,
    val watching: List<String>? = null,
    val watched: List<String>? = null,
    @SerializedName("plan_to_watch") val planToWatch: List<String>? = null
) {
    fun resolved(): UserProfileData = profile ?: UserProfileData(
        watching = watching ?: emptyList(),
        watched = watched ?: emptyList(),
        planToWatch = planToWatch ?: emptyList()
    )
}
