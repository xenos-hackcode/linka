package com.example.linka

import android.content.Context

private const val PREFS = "tutorial_prefs"

/** Gates the one-time auto-shown tutorial (see TutorialScreen.kt) - shown the first
 * time the app is ever opened, then reachable again anytime from Settings > Activity
 * Log > Tutorial. */
object TutorialPrefs {
    fun hasSeenTutorial(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("seen", false)

    fun markTutorialSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("seen", true).apply()
    }
}
