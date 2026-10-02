package com.example.linka

import android.content.Context

private const val PREFS = "notification_privacy_prefs"

enum class AccessChoice { EVERYONE, NO_ONE }

/** Notification toggles are real and gate the app's one actual notification path
 * (see PhoneLinkAccessibilityService.postPausedNotification()). The two AccessChoice
 * preferences are not enforced anywhere yet - there's no user directory/backend for
 * either paired chat (already gated by needing a pairing token) or Domain requests to
 * check them against. They store intent for when that infrastructure exists. */
object NotificationPrefs {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun notificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean("notifications_enabled", true)
    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("notifications_enabled", enabled).apply()
    }

    fun notifyChatMessages(context: Context): Boolean = prefs(context).getBoolean("notify_chat", true)
    fun setNotifyChatMessages(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notify_chat", value).apply()
    }

    fun notifyRequests(context: Context): Boolean = prefs(context).getBoolean("notify_requests", true)
    fun setNotifyRequests(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notify_requests", value).apply()
    }

    fun notifySessionStatus(context: Context): Boolean = prefs(context).getBoolean("notify_session", true)
    fun setNotifySessionStatus(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notify_session", value).apply()
    }

    fun notifyGlobalChat(context: Context): Boolean = prefs(context).getBoolean("notify_global", true)
    fun setNotifyGlobalChat(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notify_global", value).apply()
    }

    fun notifyGroupChat(context: Context): Boolean = prefs(context).getBoolean("notify_group", true)
    fun setNotifyGroupChat(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("notify_group", value).apply()
    }

    // Split from a single "who can chat me" into one per surface - global and group
    // chat are genuinely different audiences. Paired chat isn't here: it's already
    // gated by needing someone's pairing token, nothing to add.
    fun chatAccessGlobal(context: Context): AccessChoice =
        AccessChoice.valueOf(prefs(context).getString("chat_access_global", AccessChoice.EVERYONE.name)!!)
    fun setChatAccessGlobal(context: Context, value: AccessChoice) {
        prefs(context).edit().putString("chat_access_global", value.name).apply()
    }

    fun chatAccessGroups(context: Context): AccessChoice =
        AccessChoice.valueOf(prefs(context).getString("chat_access_groups", AccessChoice.EVERYONE.name)!!)
    fun setChatAccessGroups(context: Context, value: AccessChoice) {
        prefs(context).edit().putString("chat_access_groups", value.name).apply()
    }

    fun groupAddAccess(context: Context): AccessChoice =
        AccessChoice.valueOf(prefs(context).getString("group_add_access", AccessChoice.EVERYONE.name)!!)
    fun setGroupAddAccess(context: Context, value: AccessChoice) {
        prefs(context).edit().putString("group_add_access", value.name).apply()
    }

    fun requestAccess(context: Context): AccessChoice =
        AccessChoice.valueOf(prefs(context).getString("request_access", AccessChoice.EVERYONE.name)!!)
    fun setRequestAccess(context: Context, value: AccessChoice) {
        prefs(context).edit().putString("request_access", value.name).apply()
    }
}
