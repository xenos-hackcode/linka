package com.example.linka

import android.content.Context
import java.io.File

/**
 * Local profile data + lifetime usage counters - there's no account backend (see
 * PLANNING.md), so "profile" here means what this device knows about its own local
 * user. The counters live here (not derived from ActivityLogStore) because that log is
 * capped at MAX_ENTRIES and trims old entries - these totals need to survive that trim,
 * so ActivityLogStore.append() bumps the matching counter at the same time it appends.
 */
object ProfileStore {
    private const val PREFS = "profile_prefs"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun displayName(context: Context): String = prefs(context).getString("display_name", "").orEmpty()
    fun setDisplayName(context: Context, value: String) {
        prefs(context).edit().putString("display_name", value).apply()
    }

    fun bio(context: Context): String = prefs(context).getString("bio", "").orEmpty()
    fun setBio(context: Context, value: String) {
        prefs(context).edit().putString("bio", value).apply()
    }

    /** Set-once, on first read - same pattern as AppAllowlistStore.hasReviewed(). */
    fun joinDateMs(context: Context): Long {
        val existing = prefs(context).getLong("join_date_ms", -1L)
        if (existing >= 0L) return existing
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong("join_date_ms", now).apply()
        return now
    }

    private fun profilePictureFile(context: Context): File = File(context.filesDir, "profile_picture.jpg")

    fun hasProfilePicture(context: Context): Boolean = profilePictureFile(context).exists()

    fun profilePicturePath(context: Context): String = profilePictureFile(context).absolutePath

    /** Copies the picked image's bytes into app-private storage rather than keeping the
     * picker's content:// Uri - that Uri's read grant isn't guaranteed to survive past
     * the picker call, so a copy avoids the picture silently failing to load later. */
    fun setProfilePicture(context: Context, bytes: ByteArray) {
        profilePictureFile(context).writeBytes(bytes)
    }

    fun clearProfilePicture(context: Context) {
        profilePictureFile(context).delete()
    }

    // ---- lifetime usage counters ----

    fun timesAsController(context: Context): Int = prefs(context).getInt("times_controller", 0)
    fun timesAsControllee(context: Context): Int = prefs(context).getInt("times_controllee", 0)
    fun requestsCreated(context: Context): Int = prefs(context).getInt("requests_created", 0)
    fun requestsAccepted(context: Context): Int = prefs(context).getInt("requests_accepted", 0)
    fun requestsDenied(context: Context): Int = prefs(context).getInt("requests_denied", 0)

    internal fun incrementTimesAsController(context: Context) = increment(context, "times_controller")
    internal fun incrementTimesAsControllee(context: Context) = increment(context, "times_controllee")
    internal fun incrementRequestsCreated(context: Context) = increment(context, "requests_created")
    internal fun incrementRequestsAccepted(context: Context) = increment(context, "requests_accepted")
    internal fun incrementRequestsDenied(context: Context) = increment(context, "requests_denied")

    private fun increment(context: Context, key: String) {
        val current = prefs(context).getInt(key, 0)
        prefs(context).edit().putInt(key, current + 1).apply()
    }
}

/**
 * Level/XP is a pure function of the lifetime counters above - no separate storage, one
 * source of truth. Weights and the 100-XP-per-level threshold are placeholder values
 * (there's no backend to balance them against yet); tune freely later.
 */
data class ProfileStats(
    val timesAsController: Int,
    val timesAsControllee: Int,
    val requestsCreated: Int,
    val requestsAccepted: Int,
    val requestsDenied: Int,
    val xp: Int,
    val level: Int,
    val xpIntoLevel: Int,
    val xpPerLevel: Int,
) {
    val totalSessions: Int get() = timesAsController + timesAsControllee
}

fun computeProfileStats(context: Context): ProfileStats {
    val timesAsController = ProfileStore.timesAsController(context)
    val timesAsControllee = ProfileStore.timesAsControllee(context)
    val requestsCreated = ProfileStore.requestsCreated(context)
    val requestsAccepted = ProfileStore.requestsAccepted(context)
    val requestsDenied = ProfileStore.requestsDenied(context)
    val xpPerLevel = 100
    val xp = (timesAsController + timesAsControllee) * 10 +
        requestsCreated * 5 + requestsAccepted * 15 + requestsDenied * 2
    return ProfileStats(
        timesAsController = timesAsController,
        timesAsControllee = timesAsControllee,
        requestsCreated = requestsCreated,
        requestsAccepted = requestsAccepted,
        requestsDenied = requestsDenied,
        xp = xp,
        level = xp / xpPerLevel + 1,
        xpIntoLevel = xp % xpPerLevel,
        xpPerLevel = xpPerLevel,
    )
}
