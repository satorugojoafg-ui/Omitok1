package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

data class UserProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String,
    val createdAt: String,
    val videoCount: Int = 0,
    val totalViews: Long = 0L
)

data class VideoItem(
    val id: String,
    val ownerId: String,
    val authorUsername: String,
    val title: String,
    val description: String,
    val fileUrl: String,
    val streamUrl: String,
    val publishedAt: String,
    val views: Long,
    val status: String
) {
    fun resolvedStreamUrl(baseUrl: String): String {
        val cleanBase = baseUrl.trimEnd('/')
        val candidate = streamUrl.ifBlank { fileUrl }
        return when {
            candidate.startsWith("http://") || candidate.startsWith("https://") -> candidate
            candidate.startsWith("/") -> "$cleanBase$candidate"
            candidate.isNotBlank() -> "$cleanBase/$candidate"
            else -> "$cleanBase/videos/$id/stream"
        }
    }
}

data class ServerHealth(
    val isOnline: Boolean,
    val message: String,
    val latencyMs: Long,
    val statusCode: Int,
    val rawDetails: String = ""
)

data class AuthSession(
    val token: String?,
    val user: UserProfile,
    val message: String = ""
)

data class EndpointProbe(
    val method: String,
    val path: String,
    val isExistingSpec: Boolean,
    val statusCode: Int?,
    val isReady: Boolean,
    val note: String
)

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class EndpointNotImplemented(val path: String, val message: String) : ApiResult<Nothing>
    data class Unauthorized(val message: String) : ApiResult<Nothing>
    data class RateLimited(val retryAfterSeconds: Int, val message: String) : ApiResult<Nothing>
    data class Error(val code: Int?, val message: String) : ApiResult<Nothing>
}

object OmitokJsonParser {

    fun extractMessage(rawBody: String?, fallback: String): String {
        if (rawBody.isNullOrBlank()) return fallback
        val trimmed = rawBody.trim()
        if (!trimmed.startsWith("{")) return fallback
        return runCatching {
            val obj = JSONObject(trimmed)
            obj.optString("error").ifBlank {
                obj.optString("message").ifBlank {
                    obj.optString("detail").ifBlank { fallback }
                }
            }
        }.getOrDefault(fallback)
    }

    fun parseServerHealth(rawBody: String?, statusCode: Int, latencyMs: Long): ServerHealth {
        val online = statusCode in 200..299
        if (rawBody.isNullOrBlank()) {
            return ServerHealth(
                isOnline = online,
                message = if (online) "OK ($statusCode)" else "HTTP $statusCode",
                latencyMs = latencyMs,
                statusCode = statusCode
            )
        }
        val trimmed = rawBody.trim()
        return if (trimmed.startsWith("{")) {
            runCatching {
                val obj = JSONObject(trimmed)
                val msg = obj.optString("message")
                    .ifBlank { obj.optString("status") }
                    .ifBlank { obj.optString("app") }
                    .ifBlank { "HTTP $statusCode" }
                ServerHealth(
                    isOnline = online,
                    message = msg,
                    latencyMs = latencyMs,
                    statusCode = statusCode,
                    rawDetails = trimmed.take(200)
                )
            }.getOrDefault(
                ServerHealth(online, "HTTP $statusCode", latencyMs, statusCode, trimmed.take(160))
            )
        } else {
            ServerHealth(
                isOnline = online,
                message = if (online) "Server Online ($statusCode)" else "HTTP $statusCode",
                latencyMs = latencyMs,
                statusCode = statusCode,
                rawDetails = trimmed.take(160)
            )
        }
    }

    fun parseAuthSession(rawBody: String, fallbackUsername: String): AuthSession {
        val obj = JSONObject(rawBody)
        val token = obj.optString("token")
            .ifBlank { obj.optString("access_token") }
            .ifBlank { obj.optString("session_token") }
            .ifBlank { null }
        val userObj = obj.optJSONObject("user") ?: obj.optJSONObject("data") ?: obj
        val user = parseUserObject(userObj, fallbackUsername)
        val msg = obj.optString("message")
        return AuthSession(token = token, user = user, message = msg)
    }

    fun parseUser(rawBody: String, fallbackUsername: String = ""): UserProfile {
        val obj = JSONObject(rawBody)
        val userObj = obj.optJSONObject("user") ?: obj.optJSONObject("profile") ?: obj
        return parseUserObject(userObj, fallbackUsername)
    }

    private fun parseUserObject(obj: JSONObject, fallbackUsername: String): UserProfile {
        val id = obj.opt("id")?.toString()?.takeIf { it != "null" }
            ?: obj.opt("user_id")?.toString()?.takeIf { it != "null" }
            ?: ""
        val username = obj.optString("username").ifBlank { fallbackUsername }
        val displayName = obj.optString("display_name")
            .ifBlank { obj.optString("displayName") }
            .ifBlank { username }
        val bio = obj.optString("bio")
        val createdAt = obj.optString("created_at")
            .ifBlank { obj.optString("createdAt") }
        val videoCount = obj.optInt("video_count", obj.optInt("videos_count", 0))
        val totalViews = obj.optLong("total_views", 0L)
        return UserProfile(
            id = id,
            username = username,
            displayName = displayName,
            bio = bio,
            createdAt = createdAt,
            videoCount = videoCount,
            totalViews = totalViews
        )
    }

    fun parseVideoList(rawBody: String, baseUrl: String): List<VideoItem> {
        val trimmed = rawBody.trim()
        val array: JSONArray = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val obj = JSONObject(trimmed)
                obj.optJSONArray("videos")
                    ?: obj.optJSONArray("items")
                    ?: obj.optJSONArray("data")
                    ?: JSONArray()
            }
            else -> JSONArray()
        }
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(parseVideoObject(item, baseUrl))
            }
        }
    }

    fun parseSingleVideo(rawBody: String, baseUrl: String): VideoItem {
        val obj = JSONObject(rawBody)
        val videoObj = obj.optJSONObject("video") ?: obj.optJSONObject("data") ?: obj
        return parseVideoObject(videoObj, baseUrl)
    }

    private fun parseVideoObject(obj: JSONObject, baseUrl: String): VideoItem {
        val id = obj.opt("id")?.toString()?.takeIf { it != "null" }
            ?: obj.opt("video_id")?.toString()?.takeIf { it != "null" }
            ?: ""
        val ownerId = obj.opt("owner_id")?.toString()?.takeIf { it != "null" }
            ?: obj.opt("user_id")?.toString()?.takeIf { it != "null" }
            ?: ""
        val authorObj = obj.optJSONObject("author") ?: obj.optJSONObject("owner")
        val authorUsername = obj.optString("username")
            .ifBlank { obj.optString("author_username") }
            .ifBlank { obj.optString("owner_username") }
            .ifBlank { obj.optString("author") }
            .ifBlank { authorObj?.optString("username").orEmpty() }
            .ifBlank { "omitok_user" }
        val title = obj.optString("title").ifBlank { "Untitled Video" }
        val description = obj.optString("description")
        val fileUrl = obj.optString("file_url")
            .ifBlank { obj.optString("url") }
            .ifBlank { obj.optString("storage_url") }
        val explicitStreamUrl = obj.optString("stream_url")
            .ifBlank {
                if (id.isNotBlank()) "${baseUrl.trimEnd('/')}/videos/$id/stream" else fileUrl
            }
        val publishedAt = obj.optString("published_at")
            .ifBlank { obj.optString("created_at") }
        val views = obj.optLong("views", obj.optLong("view_count", 0L))
        val status = obj.optString("status").ifBlank { "published" }
        return VideoItem(
            id = id,
            ownerId = ownerId,
            authorUsername = authorUsername,
            title = title,
            description = description,
            fileUrl = fileUrl,
            streamUrl = explicitStreamUrl,
            publishedAt = publishedAt,
            views = views,
            status = status
        )
    }
}
