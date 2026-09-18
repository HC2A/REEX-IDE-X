package com.reex.idex.core

import android.content.Context

/** Persists the last editor buffer locally so the IDE remains useful without network access. */
class OfflineSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("reex_offline_session", Context.MODE_PRIVATE)

    fun save(fileName: String, source: String) {
        prefs.edit().putString("file_name", fileName).putString("source", source).apply()
    }

    fun fileName(): String = prefs.getString("file_name", "main.dart") ?: "main.dart"
    fun sourceOrNull(): String? = prefs.getString("source", null)
    fun clear() = prefs.edit().clear().apply()
}
