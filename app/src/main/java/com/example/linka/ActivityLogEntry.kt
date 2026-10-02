package com.example.linka

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class SessionRole { CONTROLLER, CONTROLLEE }
enum class ActivityType { LINK_ESTABLISHED, REQUEST_CREATED, REQUEST_ACCEPTED, REQUEST_DENIED }

@Serializable
data class ActivityLogEntry(
    val id: String,
    val type: ActivityType,
    val timestampMs: Long,
    val role: SessionRole? = null,
    val otherCountry: String? = null,
    val requestSummary: String? = null,
)

private const val PREFS = "activity_log_prefs"
private const val KEY_ENTRIES = "entries"
private const val MAX_ENTRIES = 200

/**
 * Unlike chat/domain/groups (deliberately ephemeral), this is meant to be a durable
 * record - persisted as a kotlinx.serialization JSON list in SharedPreferences, capped
 * at MAX_ENTRIES so it can't grow unbounded.
 */
object ActivityLogStore {
    private val json = Json { ignoreUnknownKeys = true }
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(context: Context): List<ActivityLogEntry> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ActivityLogEntry>>(raw).sortedByDescending { it.timestampMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun append(context: Context, entry: ActivityLogEntry) {
        val current = try {
            prefs(context).getString(KEY_ENTRIES, null)?.let { json.decodeFromString<List<ActivityLogEntry>>(it) }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val updated = (current + entry).takeLast(MAX_ENTRIES)
        prefs(context).edit().putString(KEY_ENTRIES, json.encodeToString(updated)).apply()

        // Lifetime counters live separately (ProfileStore) since this log trims to
        // MAX_ENTRIES and the profile's totals need to survive that trim.
        when (entry.type) {
            ActivityType.LINK_ESTABLISHED -> when (entry.role) {
                SessionRole.CONTROLLER -> ProfileStore.incrementTimesAsController(context)
                SessionRole.CONTROLLEE -> ProfileStore.incrementTimesAsControllee(context)
                null -> Unit
            }
            ActivityType.REQUEST_CREATED -> ProfileStore.incrementRequestsCreated(context)
            ActivityType.REQUEST_ACCEPTED -> ProfileStore.incrementRequestsAccepted(context)
            ActivityType.REQUEST_DENIED -> ProfileStore.incrementRequestsDenied(context)
        }
    }

    /** Fills in otherCountry on the most recent matching LINK_ESTABLISHED entry once the
     * other side's LocationInfo arrives asynchronously after the link was first logged. */
    fun updateLastLinkCountry(context: Context, role: SessionRole, country: String) {
        val current = try {
            prefs(context).getString(KEY_ENTRIES, null)?.let { json.decodeFromString<List<ActivityLogEntry>>(it) }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val targetIndex = current.indexOfLast { it.type == ActivityType.LINK_ESTABLISHED && it.role == role }
        if (targetIndex == -1) return
        val updated = current.toMutableList()
        updated[targetIndex] = updated[targetIndex].copy(otherCountry = country)
        prefs(context).edit().putString(KEY_ENTRIES, json.encodeToString(updated)).apply()
    }
}
