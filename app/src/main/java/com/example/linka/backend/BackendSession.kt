package com.example.linka.backend

import android.content.Context

private const val PREFS = "backend_session_prefs"

/** Local cache of the signed-in session against the backend (see server/) - separate
 * from every other local store in this app since this is the one place a real,
 * server-issued credential (a JWT) is kept. Link/pairing needs none of this - it's
 * secured by its own per-device pairing tokens, untouched by accounts. */
object BackendSession {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun token(context: Context): String? = prefs(context).getString("token", null)
    fun userId(context: Context): String? = prefs(context).getString("user_id", null)
    fun username(context: Context): String? = prefs(context).getString("username", null)

    fun save(context: Context, token: String, userId: String, username: String) {
        prefs(context).edit()
            .putString("token", token)
            .putString("user_id", userId)
            .putString("username", username)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
