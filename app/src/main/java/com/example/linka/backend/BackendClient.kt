package com.example.linka.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Real deployed backend (2026-09-14) - was 127.0.0.1/10.0.2.2, which only ever
 * worked against a dev machine on the same box (via `adb reverse`) or the
 * emulator's host alias; a real installed APK on a real user's phone had no way
 * to reach either. Now points at the actual Cloud Run service (see server/,
 * deployed with a real Postgres on Cloud SQL rather than the old local SQLite
 * file). See WS_HOST below - the WebSocket client needs the bare host, not the
 * full https:// URL. */
private const val BASE_URL = "https://linka-server-397196684809.us-central1.run.app"
internal const val WS_HOST = "linka-server-397196684809.us-central1.run.app"

@Serializable
data class BackendUser(
    val id: String,
    val username: String,
    val displayName: String,
    val bio: String,
)

@Serializable
data class AuthResponse(val token: String, val user: BackendUser)

@Serializable
data class BackendRequest(
    val id: String,
    @SerialName("requester_id") val requesterId: String,
    val summary: String,
    val reason: String,
    val status: String,
    @SerialName("responder_id") val responderId: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class BackendGlobalMessage(
    val id: String,
    @SerialName("user_id") val userId: String,
    val username: String,
    val text: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class BackendGroup(
    val id: String,
    val name: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class BackendGroupMessage(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val username: String,
    val text: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class BackendDmMessage(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val text: String,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
private data class ApiError(val error: String)

sealed interface BackendResult<out T> {
    data class Success<T>(val value: T) : BackendResult<T>
    data class Failure(val status: Int, val message: String) : BackendResult<Nothing>
}

/** Thin REST client for the real deployed account/chat backend (see server/) -
 * accounts, the domain request marketplace, global chat, and groups. Pairing/
 * control is a completely separate protocol/server (see :core's
 * KtorControlSocketClient) - this client and that one never share a connection
 * or a base URL. */
object BackendClient {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private suspend inline fun <reified T> call(
        method: HttpMethod,
        path: String,
        token: String? = null,
        body: JsonObject? = null,
    ): BackendResult<T> = try {
        val response: HttpResponse = client.request("$BASE_URL$path") {
            this.method = method
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        if (response.status.isSuccess()) {
            BackendResult.Success(response.body())
        } else {
            val message = runCatching { response.body<ApiError>().error }.getOrDefault("request failed (${response.status.value})")
            BackendResult.Failure(response.status.value, message)
        }
    } catch (e: Exception) {
        BackendResult.Failure(0, e.message ?: "network error - check your connection and try again")
    }

    suspend fun register(username: String, password: String, displayName: String): BackendResult<AuthResponse> =
        call(HttpMethod.Post, "/auth/register", body = buildJsonObject {
            put("username", username); put("password", password); put("displayName", displayName)
        })

    suspend fun login(username: String, password: String): BackendResult<AuthResponse> =
        call(HttpMethod.Post, "/auth/login", body = buildJsonObject {
            put("username", username); put("password", password)
        })

    suspend fun me(token: String): BackendResult<BackendUser> = call(HttpMethod.Get, "/auth/me", token)

    suspend fun updateMe(token: String, displayName: String, bio: String): BackendResult<BackendUser> =
        call(HttpMethod.Patch, "/auth/me", token, buildJsonObject {
            put("displayName", displayName); put("bio", bio)
        })

    suspend fun browseRequests(token: String): BackendResult<List<BackendRequest>> =
        call(HttpMethod.Get, "/requests", token)

    suspend fun myRequests(token: String): BackendResult<List<BackendRequest>> =
        call(HttpMethod.Get, "/requests/mine", token)

    suspend fun acceptedRequests(token: String): BackendResult<List<BackendRequest>> =
        call(HttpMethod.Get, "/requests/accepted", token)

    suspend fun createRequest(token: String, summary: String, reason: String): BackendResult<BackendRequest> =
        call(HttpMethod.Post, "/requests", token, buildJsonObject {
            put("summary", summary); put("reason", reason)
        })

    suspend fun acceptRequest(token: String, id: String): BackendResult<BackendRequest> =
        call(HttpMethod.Post, "/requests/$id/accept", token)

    suspend fun denyRequest(token: String, id: String): BackendResult<BackendRequest> =
        call(HttpMethod.Post, "/requests/$id/deny", token)

    suspend fun cancelRequest(token: String, id: String): BackendResult<BackendRequest> =
        call(HttpMethod.Post, "/requests/$id/cancel", token)

    suspend fun globalMessages(token: String): BackendResult<List<BackendGlobalMessage>> =
        call(HttpMethod.Get, "/global/messages", token)

    suspend fun postGlobalMessage(token: String, text: String): BackendResult<BackendGlobalMessage> =
        call(HttpMethod.Post, "/global/messages", token, buildJsonObject { put("text", text) })

    suspend fun listGroups(token: String): BackendResult<List<BackendGroup>> =
        call(HttpMethod.Get, "/groups", token)

    suspend fun discoverGroups(token: String): BackendResult<List<BackendGroup>> =
        call(HttpMethod.Get, "/groups/discover", token)

    suspend fun createGroup(token: String, name: String): BackendResult<BackendGroup> =
        call(HttpMethod.Post, "/groups", token, buildJsonObject { put("name", name) })

    suspend fun joinGroup(token: String, id: String): BackendResult<BackendGroup> =
        call(HttpMethod.Post, "/groups/$id/join", token)

    suspend fun groupMessages(token: String, id: String): BackendResult<List<BackendGroupMessage>> =
        call(HttpMethod.Get, "/groups/$id/messages", token)

    suspend fun postGroupMessage(token: String, id: String, text: String): BackendResult<BackendGroupMessage> =
        call(HttpMethod.Post, "/groups/$id/messages", token, buildJsonObject { put("text", text) })

    suspend fun createGroupWith(token: String, name: String, withUserId: String): BackendResult<BackendGroup> =
        call(HttpMethod.Post, "/groups", token, buildJsonObject { put("name", name); put("withUserId", withUserId) })

    suspend fun getUser(token: String, id: String): BackendResult<BackendUser> =
        call(HttpMethod.Get, "/users/$id", token)

    suspend fun listBlocks(token: String): BackendResult<List<String>> =
        call(HttpMethod.Get, "/blocks", token)

    suspend fun blockUser(token: String, id: String): BackendResult<Unit> =
        call(HttpMethod.Post, "/blocks/$id", token)

    suspend fun unblockUser(token: String, id: String): BackendResult<Unit> =
        call(HttpMethod.Delete, "/blocks/$id", token)

    suspend fun dmMessages(token: String, userId: String): BackendResult<List<BackendDmMessage>> =
        call(HttpMethod.Get, "/dms/$userId/messages", token)

    suspend fun sendDm(token: String, userId: String, text: String): BackendResult<BackendDmMessage> =
        call(HttpMethod.Post, "/dms/$userId/messages", token, buildJsonObject { put("text", text) })
}
