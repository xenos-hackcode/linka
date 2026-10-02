package com.example.phonelinkagent

import android.content.Context

private const val PREFS = "phone_link_allowlist_prefs"
private const val KEY_BLOCKED = "blocked_packages"
private const val KEY_REVIEWED = "reviewed_once"

/**
 * Default-allow, not default-deny: an empty blocked set (the default) means every app
 * is shared, matching the app's existing unrestricted-sharing behavior before this
 * feature existed. The controllee opts specific apps OUT rather than having to opt
 * everything IN - see PLANNING.md §3.1 for why.
 */
object AppAllowlistStore {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun blockedPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BLOCKED, emptySet()) ?: emptySet()

    fun isBlocked(context: Context, packageName: String): Boolean =
        blockedPackages(context).contains(packageName)

    fun setBlocked(context: Context, packageName: String, blocked: Boolean) {
        val current = blockedPackages(context).toMutableSet()
        if (blocked) current.add(packageName) else current.remove(packageName)
        // SharedPreferences requires a fresh Set instance on write - mutating and
        // re-putting the same Set reference silently fails to persist on some
        // implementations, so a copy is made above rather than mutating in place.
        prefs(context).edit().putStringSet(KEY_BLOCKED, current).apply()
    }

    /** True once the controllee has been through the app-selection step at least
     * once (shown as a mandatory onboarding step right before the pairing/QR screen,
     * same pattern as the disclosure step) - after that it's reachable anytime via
     * "MANAGE SHARED APPS" but no longer forced into the flow. */
    fun hasReviewed(context: Context): Boolean = prefs(context).getBoolean(KEY_REVIEWED, false)

    fun markReviewed(context: Context) {
        prefs(context).edit().putBoolean(KEY_REVIEWED, true).apply()
    }
}
