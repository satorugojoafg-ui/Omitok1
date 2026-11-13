package com.example.data.network

import android.content.ContentResolver
import android.net.Uri
import android.os.SystemClock
import com.example.data.model.ApiResult
import com.example.data.model.AuthSession
import com.example.data.model.EndpointProbe
import com.example.data.model.OmitokJsonParser
import com.example.data.model.ServerHealth
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val hostCookies = store.getOrPut(url.host) { mutableListOf() }
        val names = cookies.map { it.name }.toSet()
        hostCookies.removeAll { it.name in names }
        hostCookies.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val list = store[url.host] ?: return emptyList()
        list.removeAll { it.expiresAt < now }
        return list.filter { it.matches(url) }
    }

    fun clear() {
        store.clear()
    }
}

private class ProgressStreamingRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: String,
    private val totalBytes: Long,
    private val onProgress: (Float) -> Unit
) : RequestBody() {

    override fun contentType() = mediaType.toMediaTypeOrNull()

    override fun contentLength(): Long = if (totalBytes > 0) totalBytes else -1L

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var uploaded = 0L
        contentResolver.openInputStream(uri)?.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                sink.write(buffer, 0, read)
                uploaded += read
                if (totalBytes > 0) {
                    onProgress((uploaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                }
            }
        } ?: throw IOException("Unable to read selected video stream")
    }
}

class OmitokApiClient(
    initialBaseUrl: String = "https://omitok07090.pythonanywhere.com"
) {
    @Volatile
    var baseUrl: String = initialBaseUrl.trimEnd('/')
        private set

    @Volatile
    var authToken: String? = null
        private set

    private val cookieJar = InMemoryCookieJar()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val builder = original.newBuilder()
                .header("Accept", "application/json")
            val token = authToken
            if (!token.isNullOrBlank() && original.header("Authorization") == null) {
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        .build()

    fun updateBaseUrl(newUrl: String) {
        val clean = newUrl.trim().trimEnd('/')
        if (clean.startsWith("http://") || clean.startsWith("https://")) {
            baseUrl = clean
        }
    }

    fun updateToken(token: String?) {
        authToken = token?.takeIf { it.isNotBlank() }
    }

    fun clearAuth() {
        authToken = null
        cookieJar.clear()
    }

    private fun url(path: String): String {
        val normalized = if (path.startsWith("/")) path else "/$path"
        return "$baseUrl$normalized"
    }

    suspend fun checkServerStatus(): ApiResult<ServerHealth> = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        runCatching {
            val req = Request.Builder().url(url("/")).get().build()
            okHttpClient.newCall(req).execute().use { res ->
                val elapsed = SystemClock.elapsedRealtime() - started
                val body = res.body?.string()
                val health = OmitokJsonParser.parseServerHealth(body, res.code, elapsed)
                if (res.isSuccessful) {
                    ApiResult.Success(health)
                } else {
                    ApiResult.Error(res.code, health.message)
                }
            }
        }.getOrElse { e ->
            ApiResult.Error(null, e.localizedMessage ?: "Network connection error")
        }
    }

    suspend fun register(username: String, password: String): ApiResult<AuthSession> =
        postAuth("/register", username, password)

    suspend fun login(username: String, password: String): ApiResult<AuthSession> =
        postAuth("/login", username, password)

    private suspend fun postAuth(
        path: String,
        username: String,
        password: String
    ): ApiResult<AuthSession> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("username", username.trim())
            put("password", password)
        }.toString()

        runCatching {
            val req = Request.Builder()
                .url(url(path))
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            okHttpClient.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                when (res.code) {
                    in 200..299 -> {
                        val session = OmitokJsonParser.parseAuthSession(
                            rawBody = raw.ifBlank { "{}" },
                            fallbackUsername = username.trim()
                        )
                        if (!session.token.isNullOrBlank()) {
                            authToken = session.token
                        }
                        ApiResult.Success(session)
                    }
                    401 -> ApiResult.Unauthorized(
                        OmitokJsonParser.extractMessage(raw, "Invalid username or password")
                    )
                    429 -> {
                        val retryAfter = res.header("Retry-After")?.toIntOrNull() ?: 60
                        ApiResult.RateLimited(
                            retryAfterSeconds = retryAfter,
                            message = OmitokJsonParser.extractMessage(
                                raw,
                                "Too many login attempts. Please wait before trying again."
                            )
                        )
                    }
                    else -> ApiResult.Error(
                        res.code,
                        OmitokJsonParser.extractMessage(raw, "HTTP ${res.code}")
                    )
                }
            }
        }.getOrElse { e ->
            ApiResult.Error(null, e.localizedMessage ?: "Network failure")
        }
    }

    suspend fun getMe(): ApiResult<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url("/me")).get().build()
            okHttpClient.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                when (res.code) {
                    in 200..299 -> ApiResult.Success(OmitokJsonParser.parseUser(raw))
                    401, 403 -> ApiResult.Unauthorized(
                        OmitokJsonParser.extractMessage(raw, "Session expired")
                    )
                    else -> ApiResult.Error(
                        res.code,
                        OmitokJsonParser.extractMessage(raw, "HTTP ${res.code}")
                    )
                }
            }
        }.getOrElse { e ->
            ApiResult.Error(null, e.localizedMessage ?: "Network failure")
        }
    }

    suspend fun logout(): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val result = runCatching {
            val req = Request.Builder()
                .url(url("/logout"))
                .post("{}".toRequestBody(JSON_MEDIA_TYPE))
                .build()
            okHttpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful || res.code == 401) {
                    ApiResult.Success(Unit)
                } else {
                    ApiResult.Error(res.code, "HTTP ${res.code}")
                }
            }
        }.getOrElse { e ->
            ApiResult.Error(null, e.localizedMessage ?: "Network failure")
        }
        clearAuth()
        result
    }

    suspend fun getVideos(): ApiResult<List<VideoItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url("/videos")).get().build()
            okHttpClient.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                when (res.code) {
                    in 200..299 -> ApiResult.Success(
                        OmitokJsonParser.parseVideoList(raw, baseUrl)
                    )
                    404 -> ApiResult.EndpointNotImplemented(
                        "/videos",
                        OmitokJsonParser.extractMessage(raw, "Endpoint /videos not found")
                    )
                    else -> ApiResult.Error(
                        res.code,
                        OmitokJsonParser.extractMessage(raw, "HTTP ${res.code}")
                    )
                }
            }
        }.getOrElse { e ->
            ApiResult.Error(null, e.localizedMessage ?: "Network failure")
        }
    }

    suspend fun getVideoById(videoId: String): ApiResult<VideoItem> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url("/videos/$videoId")).get().build()
            okHttpClient.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                when (res.code) {
                    in 200..299 -> ApiResult.Success(OmitokJsonParser.parseSingleVideo(raw, baseUrl))
                    404, 405, 501 -> ApiResult.EndpointNotImplemented(
                        "/videos/$videoId",
                        OmitokJsonParser.extractMessage(raw, "Video detail route not yet deployed")
                    )
                    else -> ApiResult.Error(
                        res.code,
                        OmitokJsonParser.extractMessage(raw, "HTTP ${res.code}")
                    )
                }
            }
        }.getOrElse { e ->
            ApiResult.Error(null, e.localizedMessage ?: "Network failure")
        }
    }

    suspend fun uploadVideo(
        contentResolver: ContentResolver,
        videoUri: Uri,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        title: String,
        description: String,
        status: String,
        onProgress: (Float) -> Unit
    ): ApiResult<VideoItem> = withContext(Dispatchers.IO) {
        runCatching {
            val streamingBody = ProgressStreamingRequestBody(
                contentResolver = contentResolver,
                uri = videoUri,
                mediaType = mimeType,
                totalBytes = sizeBytes,
                onProgress = onProgress
            )
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("title", title.trim())
                .addFormDataPart("description", description.trim())
                .addFormDataPart("status", status)
                .addFormDataPart("video", fileName, streamingBody)
                .build()

            val req = Request.Builder()
                .url(url("/upload"))
                .post(multipart)
                .build()

            okHttpClient.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                when (res.code) {
                    in 200..299 -> ApiResult.Success(
                        OmitokJsonParser.parseSingleVideo(raw, baseUrl)
                    )
                    401, 403 -> ApiResult.Unauthorized(
                        OmitokJsonParser.extractMessage(raw, "Authentication required")
                    )
                    404, 405, 501 -> ApiResult.EndpointNotImplemented(
                        "/upload",
                        OmitokJsonParser.extractMessage(
                            raw,
                            "POST /upload is not active on the server yet"
                        )
                    )
                    else -> ApiResult.Error(
                        res.code,
                        OmitokJsonParser.extractMessage(raw, "Upload failed (HTTP ${res.code})")
                    )
                }
            }
        }.getOrElse { e ->
            ApiResult.Error(null, e.localizedMessage ?: "Upload failed")
        }
    }

    suspend fun deleteVideo(videoId: String): ApiResult<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url("/videos/$videoId")).delete().build()
            okHttpClient.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                when (res.code) {
                    in 200..299 -> ApiResult.Success(Unit)
                    401, 403 -> ApiResult.Unauthorized(
                        OmitokJsonParser.extractMessage(raw, "Not authorized to delete this video")
                    )
                    404, 405, 501 -> ApiResult.EndpointNotImplemented(
                        "/videos/$videoId",
                        OmitokJsonParser.extractMessage(raw, "DELETE /videos/{id} not active yet")
                    )
                    else -> ApiResult.Error(
                        res.code,
                        OmitokJsonParser.extractMessage(raw, "HTTP ${res.code}")
                    )
                }
            }
        }.getOrElse { e ->
            ApiResult.Error(null, e.localizedMessage ?: "Network failure")
        }
    }

    suspend fun getProfile(username: String): ApiResult<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url(url("/profile/${username.trim()}")).get().build()
            okHttpClient.newCall(req).execute().use { res ->
                val raw = res.body?.string().orEmpty()
                when (res.code) {
                    in 200..299 -> ApiResult.Success(OmitokJsonParser.parseUser(raw, username))
                    404, 405, 501 -> ApiResult.EndpointNotImplemented(
                        "/profile/$username",
                        OmitokJsonParser.extractMessage(raw, "Profile endpoint not active yet")
                    )
                    else -> ApiResult.Error(
                        res.code,
                        OmitokJsonParser.extractMessage(raw, "HTTP ${res.code}")
                    )
                }
            }
        }.getOrElse { e ->
            ApiResult.Error(null, e.localizedMessage ?: "Network failure")
        }
    }

    suspend fun getProfileVideos(username: String): ApiResult<List<VideoItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url(url("/profile/${username.trim()}/videos"))
                    .get()
                    .build()
                okHttpClient.newCall(req).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    when (res.code) {
                        in 200..299 -> ApiResult.Success(
                            OmitokJsonParser.parseVideoList(raw, baseUrl)
                        )
                        404, 405, 501 -> ApiResult.EndpointNotImplemented(
                            "/profile/$username/videos",
                            OmitokJsonParser.extractMessage(
                                raw,
                                "Profile videos endpoint not active yet"
                            )
                        )
                        else -> ApiResult.Error(
                            res.code,
                            OmitokJsonParser.extractMessage(raw, "HTTP ${res.code}")
                        )
                    }
                }
            }.getOrElse { e ->
                ApiResult.Error(null, e.localizedMessage ?: "Network failure")
            }
        }

    suspend fun updateProfile(displayName: String, bio: String): ApiResult<UserProfile> =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("display_name", displayName.trim())
                put("bio", bio.trim())
            }.toString()
            runCatching {
                val req = Request.Builder()
                    .url(url("/profile"))
                    .put(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                okHttpClient.newCall(req).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    when (res.code) {
                        in 200..299 -> ApiResult.Success(OmitokJsonParser.parseUser(raw))
                        401, 403 -> ApiResult.Unauthorized(
                            OmitokJsonParser.extractMessage(raw, "Sign in required")
                        )
                        404, 405, 501 -> ApiResult.EndpointNotImplemented(
                            "/profile",
                            OmitokJsonParser.extractMessage(raw, "Profile update route not active yet")
                        )
                        else -> ApiResult.Error(
                            res.code,
                            OmitokJsonParser.extractMessage(raw, "HTTP ${res.code}")
                        )
                    }
                }
            }.getOrElse { e ->
                ApiResult.Error(null, e.localizedMessage ?: "Network failure")
            }
        }

    suspend fun reportVideo(videoId: String, reason: String): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("reason", reason)
            }.toString()
            runCatching {
                val req = Request.Builder()
                    .url(url("/videos/$videoId/report"))
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                okHttpClient.newCall(req).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    when (res.code) {
                        in 200..299 -> ApiResult.Success(Unit)
                        404, 405, 501 -> ApiResult.EndpointNotImplemented(
                            "/videos/$videoId/report",
                            OmitokJsonParser.extractMessage(raw, "Report route not active yet")
                        )
                        else -> ApiResult.Error(
                            res.code,
                            OmitokJsonParser.extractMessage(raw, "HTTP ${res.code}")
                        )
                    }
                }
            }.getOrElse { e ->
                ApiResult.Error(null, e.localizedMessage ?: "Network failure")
            }
        }

    suspend fun probeEndpoints(): List<EndpointProbe> = withContext(Dispatchers.IO) {
        val specs = listOf(
            Triple("GET", "/", true),
            Triple("POST", "/register", true),
            Triple("POST", "/login", true),
            Triple("GET", "/me", true),
            Triple("POST", "/logout", true),
            Triple("GET", "/videos", true),
            Triple("POST", "/upload", false),
            Triple("GET", "/videos/1", false),
            Triple("GET", "/videos/1/stream", false),
            Triple("DELETE", "/videos/1", false),
            Triple("GET", "/profile/demo", false),
            Triple("GET", "/profile/demo/videos", false)
        )

        specs.map { (method, path, isExisting) ->
            val probeResult = runCatching {
                // Use non-destructive OPTIONS/GET/HEAD or minimal probe without mutating data
                val reqBuilder = Request.Builder().url(url(path))
                when (method) {
                    "GET" -> reqBuilder.get()
                    else -> reqBuilder.method("OPTIONS", null)
                }
                okHttpClient.newCall(reqBuilder.build()).execute().use { res ->
                    val code = res.code
                    // On Flask, OPTIONS returns 200/204 for registered routes and 404 for unregistered routes
                    val ready = code != 404 && code != 501 && code != 502 && code != 503
                    EndpointProbe(
                        method = method,
                        path = path,
                        isExistingSpec = isExisting,
                        statusCode = code,
                        isReady = ready,
                        note = if (ready) "HTTP $code" else "Not deployed ($code)"
                    )
                }
            }.getOrElse { e ->
                EndpointProbe(
                    method = method,
                    path = path,
                    isExistingSpec = isExisting,
                    statusCode = null,
                    isReady = false,
                    note = e.localizedMessage ?: "Error"
                )
            }
            probeResult
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
