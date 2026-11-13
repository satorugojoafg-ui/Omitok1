package com.example.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.PersistedSettings
import com.example.data.local.SessionPreferences
import com.example.data.model.ApiResult
import com.example.data.model.AuthSession
import com.example.data.model.EndpointProbe
import com.example.data.model.ServerHealth
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.data.network.OmitokApiClient
import com.example.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

data class PickedVideoMetadata(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long
)

class OmitokRepository(
    private val preferences: SessionPreferences,
    private val apiClient: OmitokApiClient
) {
    companion object {
        const val MAX_UPLOAD_BYTES: Long = 50L * 1024L * 1024L // 50 MB PythonAnywhere free-tier cap
        private val ALLOWED_MIME_TYPES = setOf(
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "video/3gpp",
            "video/x-matroska"
        )
    }

    val settingsFlow: Flow<PersistedSettings> = preferences.settingsFlow

    val streamingHttpClient: OkHttpClient
        get() = apiClient.okHttpClient

    suspend fun initializeFromPreferences(): PersistedSettings {
        val settings = preferences.settingsFlow.first()
        apiClient.updateBaseUrl(settings.baseUrl)
        apiClient.updateToken(settings.authToken)
        return settings
    }

    suspend fun checkServerStatus(): ApiResult<ServerHealth> = apiClient.checkServerStatus()

    suspend fun restoreCurrentUser(): ApiResult<UserProfile> {
        val res = apiClient.getMe()
        if (res is ApiResult.Unauthorized) {
            preferences.clearSession()
            apiClient.clearAuth()
        }
        return res
    }

    suspend fun register(username: String, password: String): ApiResult<AuthSession> {
        val result = apiClient.register(username, password)
        if (result is ApiResult.Success) {
            preferences.saveSession(result.data.token, result.data.user.username)
        }
        return result
    }

    suspend fun login(username: String, password: String): ApiResult<AuthSession> {
        val result = apiClient.login(username, password)
        if (result is ApiResult.Success) {
            preferences.saveSession(result.data.token, result.data.user.username)
        }
        return result
    }

    suspend fun logout(): ApiResult<Unit> {
        val result = apiClient.logout()
        preferences.clearSession()
        return result
    }

    suspend fun fetchVideos(): ApiResult<List<VideoItem>> = apiClient.getVideos()

    suspend fun fetchVideoById(id: String): ApiResult<VideoItem> = apiClient.getVideoById(id)

    suspend fun deleteVideo(id: String): ApiResult<Unit> = apiClient.deleteVideo(id)

    suspend fun reportVideo(id: String, reason: String): ApiResult<Unit> =
        apiClient.reportVideo(id, reason)

    suspend fun fetchProfile(username: String): ApiResult<UserProfile> =
        apiClient.getProfile(username)

    suspend fun fetchProfileVideos(username: String): ApiResult<List<VideoItem>> =
        apiClient.getProfileVideos(username)

    suspend fun updateProfile(displayName: String, bio: String): ApiResult<UserProfile> =
        apiClient.updateProfile(displayName, bio)

    suspend fun uploadVideo(
        contentResolver: ContentResolver,
        metadata: PickedVideoMetadata,
        title: String,
        description: String,
        publishImmediately: Boolean,
        onProgress: (Float) -> Unit
    ): ApiResult<VideoItem> {
        return apiClient.uploadVideo(
            contentResolver = contentResolver,
            videoUri = metadata.uri,
            fileName = metadata.fileName,
            mimeType = metadata.mimeType,
            sizeBytes = metadata.sizeBytes,
            title = title,
            description = description,
            status = if (publishImmediately) "published" else "draft",
            onProgress = onProgress
        )
    }

    suspend fun probeEndpoints(): List<EndpointProbe> = apiClient.probeEndpoints()

    suspend fun setLanguage(language: AppLanguage) {
        preferences.setLanguage(language)
    }

    suspend fun setBaseUrl(url: String) {
        preferences.setBaseUrl(url)
        apiClient.updateBaseUrl(url)
    }

    suspend fun updatePlaybackPreferences(autoplay: Boolean, loop: Boolean, startMuted: Boolean) {
        preferences.updatePlayback(autoplay, loop, startMuted)
    }

    suspend fun addRecentSearch(query: String) {
        preferences.addRecentSearch(query)
    }

    suspend fun clearRecentSearches() {
        preferences.clearRecentSearches()
    }

    fun inspectPickedVideo(contentResolver: ContentResolver, uri: Uri): PickedVideoMetadata? {
        val mime = contentResolver.getType(uri) ?: "video/mp4"
        var fileName = "omitok_video.mp4"
        var size = -1L
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
                if (sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
        return PickedVideoMetadata(
            uri = uri,
            fileName = fileName,
            mimeType = mime,
            sizeBytes = size
        )
    }

    fun isAllowedVideoMime(mimeType: String): Boolean =
        mimeType.lowercase() in ALLOWED_MIME_TYPES || mimeType.lowercase().startsWith("video/")
}
