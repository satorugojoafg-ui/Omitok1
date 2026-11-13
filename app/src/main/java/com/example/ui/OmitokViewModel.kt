package com.example.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PersistedSettings
import com.example.data.local.PlaybackPreferences
import com.example.data.local.SessionPreferences
import com.example.data.model.ApiResult
import com.example.data.model.EndpointProbe
import com.example.data.model.ServerHealth
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.data.network.OmitokApiClient
import com.example.data.repository.OmitokRepository
import com.example.data.repository.PickedVideoMetadata
import com.example.i18n.AppLanguage
import com.example.i18n.OmitokStrings
import com.example.i18n.StringsProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

data class OmitokUiState(
    val isBootstrapping: Boolean = true,
    val language: AppLanguage = AppLanguage.FA,
    val baseUrl: String = PersistedSettings.DEFAULT_BASE_URL,
    val playback: PlaybackPreferences = PlaybackPreferences(),
    val serverHealth: ServerHealth? = null,
    val isCheckingServer: Boolean = false,
    val currentUser: UserProfile? = null,
    val authLoading: Boolean = false,
    val authError: String? = null,
    val rateLimitSeconds: Int = 0,
    val videos: List<VideoItem> = emptyList(),
    val isFeedLoading: Boolean = false,
    val feedError: String? = null,
    val viewedProfile: UserProfile? = null,
    val viewedProfileVideos: List<VideoItem> = emptyList(),
    val isProfileLoading: Boolean = false,
    val profileError: String? = null,
    val profileInfoMessage: String? = null,
    val pickedVideo: PickedVideoMetadata? = null,
    val uploadProgress: Float = 0f,
    val isUploading: Boolean = false,
    val uploadError: String? = null,
    val uploadSuccessMessage: String? = null,
    val searchQuery: String = "",
    val recentSearches: List<String> = emptyList(),
    val searchedProfile: UserProfile? = null,
    val isSearchingProfile: Boolean = false,
    val endpointProbes: List<EndpointProbe> = emptyList(),
    val isProbingEndpoints: Boolean = false,
    val snackbarMessage: String? = null
) {
    val strings: OmitokStrings get() = StringsProvider.get(language)
    val isLoggedIn: Boolean get() = currentUser != null
}

class OmitokViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SessionPreferences(application.applicationContext)
    private val apiClient = OmitokApiClient()
    private val repository = OmitokRepository(preferences, apiClient)

    private val _uiState = MutableStateFlow(OmitokUiState())
    val uiState: StateFlow<OmitokUiState> = _uiState.asStateFlow()

    val streamingHttpClient: OkHttpClient
        get() = repository.streamingHttpClient

    private var rateLimitJob: Job? = null

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { settings ->
                apiClient.updateBaseUrl(settings.baseUrl)
                apiClient.updateToken(settings.authToken)
                _uiState.update {
                    it.copy(
                        language = settings.language,
                        baseUrl = settings.baseUrl,
                        playback = settings.playback,
                        recentSearches = settings.recentSearches
                    )
                }
            }
        }

        viewModelScope.launch {
            val initial = repository.initializeFromPreferences()
            checkServerStatus()
            if (!initial.authToken.isNullOrBlank() || !initial.savedUsername.isNullOrBlank()) {
                when (val meResult = repository.restoreCurrentUser()) {
                    is ApiResult.Success -> {
                        _uiState.update { it.copy(currentUser = meResult.data) }
                    }
                    else -> Unit
                }
            }
            loadVideos()
            _uiState.update { it.copy(isBootstrapping = false) }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authError = null) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            repository.setLanguage(language)
        }
    }

    fun updateBaseUrl(url: String) {
        viewModelScope.launch {
            repository.setBaseUrl(url)
            checkServerStatus()
        }
    }

    fun updatePlaybackPreferences(autoplay: Boolean, loop: Boolean, startMuted: Boolean) {
        viewModelScope.launch {
            repository.updatePlaybackPreferences(autoplay, loop, startMuted)
        }
    }

    fun checkServerStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingServer = true) }
            when (val res = repository.checkServerStatus()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(isCheckingServer = false, serverHealth = res.data)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isCheckingServer = false,
                            serverHealth = ServerHealth(
                                isOnline = false,
                                message = res.message,
                                latencyMs = 0L,
                                statusCode = res.code ?: 0
                            )
                        )
                    }
                }
                else -> _uiState.update { it.copy(isCheckingServer = false) }
            }
        }
    }

    fun probeEndpoints() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProbingEndpoints = true) }
            val probes = repository.probeEndpoints()
            _uiState.update { it.copy(isProbingEndpoints = false, endpointProbes = probes) }
        }
    }

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        val strings = _uiState.value.strings
        val cleanUser = username.trim()
        if (!USERNAME_REGEX.matches(cleanUser)) {
            _uiState.update { it.copy(authError = strings.validationUsernameError) }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(authError = strings.validationPasswordError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null) }
            when (val res = repository.login(cleanUser, password)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            currentUser = res.data.user,
                            authError = null
                        )
                    }
                    loadVideos()
                    onSuccess()
                }
                is ApiResult.RateLimited -> {
                    startRateLimitCountdown(res.retryAfterSeconds)
                    _uiState.update {
                        it.copy(authLoading = false, authError = res.message)
                    }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
                is ApiResult.EndpointNotImplemented -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
            }
        }
    }

    fun register(
        username: String,
        password: String,
        confirmPassword: String,
        onSuccess: () -> Unit
    ) {
        val strings = _uiState.value.strings
        val cleanUser = username.trim()
        if (!USERNAME_REGEX.matches(cleanUser)) {
            _uiState.update { it.copy(authError = strings.validationUsernameError) }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(authError = strings.validationPasswordError) }
            return
        }
        if (password != confirmPassword) {
            _uiState.update { it.copy(authError = strings.validationPasswordMatchError) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null) }
            when (val res = repository.register(cleanUser, password)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            currentUser = res.data.user,
                            authError = null
                        )
                    }
                    loadVideos()
                    onSuccess()
                }
                is ApiResult.RateLimited -> {
                    startRateLimitCountdown(res.retryAfterSeconds)
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
                is ApiResult.EndpointNotImplemented -> {
                    _uiState.update { it.copy(authLoading = false, authError = res.message) }
                }
            }
        }
    }

    private fun startRateLimitCountdown(seconds: Int) {
        rateLimitJob?.cancel()
        rateLimitJob = viewModelScope.launch {
            var remaining = seconds.coerceAtLeast(1)
            while (remaining > 0) {
                _uiState.update { it.copy(rateLimitSeconds = remaining) }
                delay(1000L)
                remaining--
            }
            _uiState.update { it.copy(rateLimitSeconds = 0) }
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.logout()
            _uiState.update {
                it.copy(
                    currentUser = null,
                    viewedProfile = null,
                    viewedProfileVideos = emptyList()
                )
            }
            onComplete()
        }
    }

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isFeedLoading = true, feedError = null) }
            when (val res = repository.fetchVideos()) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isFeedLoading = false,
                            videos = res.data,
                            feedError = null
                        )
                    }
                }
                is ApiResult.EndpointNotImplemented -> {
                    _uiState.update {
                        it.copy(
                            isFeedLoading = false,
                            feedError = "${_uiState.value.strings.endpointPendingHint} (${res.path})"
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(isFeedLoading = false, feedError = res.message) }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update { it.copy(isFeedLoading = false, feedError = res.message) }
                }
                is ApiResult.RateLimited -> {
                    _uiState.update { it.copy(isFeedLoading = false, feedError = res.message) }
                }
            }
        }
    }

    fun deleteVideo(videoId: String) {
        viewModelScope.launch {
            when (val res = repository.deleteVideo(videoId)) {
                is ApiResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            videos = state.videos.filterNot { it.id == videoId },
                            viewedProfileVideos = state.viewedProfileVideos.filterNot { it.id == videoId },
                            snackbarMessage = "OK"
                        )
                    }
                }
                is ApiResult.EndpointNotImplemented -> {
                    _uiState.update {
                        it.copy(snackbarMessage = "${it.strings.endpointPendingHint} (${res.path})")
                    }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update { it.copy(snackbarMessage = res.message) }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(snackbarMessage = res.message) }
                }
                else -> Unit
            }
        }
    }

    fun reportVideo(videoId: String, reason: String) {
        viewModelScope.launch {
            when (val res = repository.reportVideo(videoId, reason)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(snackbarMessage = it.strings.reportSubmitted) }
                }
                is ApiResult.EndpointNotImplemented -> {
                    _uiState.update {
                        it.copy(snackbarMessage = "${it.strings.endpointPendingHint} (${res.path})")
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update { it.copy(snackbarMessage = res.message) }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update { it.copy(snackbarMessage = res.message) }
                }
                else -> Unit
            }
        }
    }

    fun onVideoPicked(contentResolver: ContentResolver, uri: Uri) {
        val strings = _uiState.value.strings
        val meta = repository.inspectPickedVideo(contentResolver, uri) ?: return
        if (!repository.isAllowedVideoMime(meta.mimeType)) {
            _uiState.update {
                it.copy(pickedVideo = null, uploadError = strings.invalidVideoFormatError)
            }
            return
        }
        if (meta.sizeBytes > OmitokRepository.MAX_UPLOAD_BYTES) {
            _uiState.update {
                it.copy(pickedVideo = null, uploadError = strings.fileTooLargeError)
            }
            return
        }
        _uiState.update {
            it.copy(
                pickedVideo = meta,
                uploadError = null,
                uploadSuccessMessage = null,
                uploadProgress = 0f
            )
        }
    }

    fun uploadVideo(
        contentResolver: ContentResolver,
        title: String,
        description: String,
        publishImmediately: Boolean,
        onSuccess: () -> Unit
    ) {
        val state = _uiState.value
        val strings = state.strings
        val meta = state.pickedVideo
        if (meta == null) {
            _uiState.update { it.copy(uploadError = strings.pickVideoButton) }
            return
        }
        if (title.trim().isBlank()) {
            _uiState.update { it.copy(uploadError = strings.validationTitleRequired) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    uploadProgress = 0f,
                    uploadError = null,
                    uploadSuccessMessage = null
                )
            }
            val result = repository.uploadVideo(
                contentResolver = contentResolver,
                metadata = meta,
                title = title,
                description = description,
                publishImmediately = publishImmediately,
                onProgress = { progress ->
                    _uiState.update { it.copy(uploadProgress = progress) }
                }
            )
            when (result) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadProgress = 1f,
                            pickedVideo = null,
                            uploadSuccessMessage = result.data.title,
                            videos = listOf(result.data) + it.videos
                        )
                    }
                    onSuccess()
                }
                is ApiResult.EndpointNotImplemented -> {
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadError = "${strings.endpointPendingHint} (${result.path}): ${result.message}"
                        )
                    }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update {
                        it.copy(isUploading = false, uploadError = result.message)
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isUploading = false, uploadError = result.message)
                    }
                }
                is ApiResult.RateLimited -> {
                    _uiState.update {
                        it.copy(isUploading = false, uploadError = result.message)
                    }
                }
            }
        }
    }

    fun loadProfile(targetUsername: String?) {
        val state = _uiState.value
        val usernameToLoad = targetUsername?.takeIf { it.isNotBlank() }
            ?: state.currentUser?.username
            ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProfileLoading = true,
                    profileError = null,
                    profileInfoMessage = null
                )
            }
            val isOwnProfile = state.currentUser?.username.equals(usernameToLoad, ignoreCase = true)

            val profileResult = repository.fetchProfile(usernameToLoad)
            val videosResult = repository.fetchProfileVideos(usernameToLoad)

            val resolvedProfile = when (profileResult) {
                is ApiResult.Success -> profileResult.data
                is ApiResult.EndpointNotImplemented -> {
                    if (isOwnProfile) state.currentUser else UserProfile(
                        id = "",
                        username = usernameToLoad,
                        displayName = usernameToLoad,
                        bio = "",
                        createdAt = ""
                    )
                }
                else -> if (isOwnProfile) state.currentUser else null
            }

            val resolvedVideos = when (videosResult) {
                is ApiResult.Success -> videosResult.data
                else -> state.videos.filter {
                    it.authorUsername.equals(usernameToLoad, ignoreCase = true)
                }
            }

            val pendingNote = when {
                profileResult is ApiResult.EndpointNotImplemented ->
                    "${state.strings.endpointPendingHint} (/profile/$usernameToLoad)"
                videosResult is ApiResult.EndpointNotImplemented ->
                    "${state.strings.endpointPendingHint} (/profile/$usernameToLoad/videos)"
                profileResult is ApiResult.Error && resolvedProfile == null -> profileResult.message
                else -> null
            }

            _uiState.update {
                it.copy(
                    isProfileLoading = false,
                    viewedProfile = resolvedProfile,
                    viewedProfileVideos = resolvedVideos,
                    profileError = if (resolvedProfile == null) pendingNote else null,
                    profileInfoMessage = if (resolvedProfile != null) pendingNote else null
                )
            }
        }
    }

    fun updateOwnProfile(displayName: String, bio: String, onDone: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isProfileLoading = true, profileError = null) }
            when (val res = repository.updateProfile(displayName, bio)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isProfileLoading = false,
                            currentUser = res.data,
                            viewedProfile = res.data,
                            snackbarMessage = "OK"
                        )
                    }
                    onDone()
                }
                is ApiResult.EndpointNotImplemented -> {
                    _uiState.update {
                        it.copy(
                            isProfileLoading = false,
                            snackbarMessage = "${state.strings.endpointPendingHint} (${res.path})"
                        )
                    }
                    onDone()
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(isProfileLoading = false, snackbarMessage = res.message)
                    }
                }
                is ApiResult.Unauthorized -> {
                    _uiState.update {
                        it.copy(isProfileLoading = false, snackbarMessage = res.message)
                    }
                }
                else -> _uiState.update { it.copy(isProfileLoading = false) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, searchedProfile = null) }
    }

    fun submitSearch(query: String) {
        val clean = query.trim().removePrefix("@")
        if (clean.isBlank()) return
        viewModelScope.launch {
            repository.addRecentSearch(clean)
            _uiState.update { it.copy(isSearchingProfile = true) }
            val profile = when (val res = repository.fetchProfile(clean)) {
                is ApiResult.Success -> res.data
                else -> null
            }
            _uiState.update { it.copy(isSearchingProfile = false, searchedProfile = profile) }
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearRecentSearches()
        }
    }

    companion object {
        private val USERNAME_REGEX = Regex("^[a-zA-Z0-9_]{3,24}$")
    }
}
