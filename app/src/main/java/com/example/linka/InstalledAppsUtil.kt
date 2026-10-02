package com.example.linka

import android.content.Context
import android.content.Intent

/** Labels of launchable apps on this device, lowercase - used for the Request tab's
 * best-effort "is the app you're asking about actually on my device" check (see
 * requestMatchesInstalledApp below). */
fun installedAppLabels(context: Context): List<String> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.loadLabel(pm).toString().lowercase() }
        .distinct()
}

/** Best-effort, case-insensitive substring match between a request's free-text summary
 * and this device's installed app labels. A DomainRequest has no real package name (see
 * RequestTabScreen.kt) - it's whatever the requester typed - so this can only ever be a
 * guess, never a guarantee; short labels are excluded to cut down on false positives. */
fun requestMatchesInstalledApp(summary: String, installedLabels: List<String>): Boolean {
    val text = summary.lowercase()
    return installedLabels.any { label -> label.length >= 3 && text.contains(label) }
}
