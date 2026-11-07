package com.kanak.app.ai

import android.content.Context
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AiConnectionStore(context: Context) {
    internal class Connection(val endpoint: URI, val accessToken: String)

    private val preferences = context.getSharedPreferences("kanak_ai_connection", Context.MODE_PRIVATE)
    private val connection = MutableStateFlow(
        parse(preferences.getString("endpoint", "").orEmpty(), preferences.getString("token", "").orEmpty())
    )
    private val _endpoint = MutableStateFlow(connection.value?.endpoint?.toString())
    val endpoint = _endpoint.asStateFlow()

    internal fun current() = connection.value

    fun configure(endpoint: String, token: String): Boolean {
        val config = parse(endpoint, token) ?: return false
        preferences.edit().putString("endpoint", config.endpoint.toString()).putString("token", config.accessToken).apply()
        connection.value = config
        _endpoint.value = config.endpoint.toString()
        return true
    }

    fun disconnect() {
        preferences.edit().clear().apply()
        connection.value = null
        _endpoint.value = null
    }

    private fun parse(endpoint: String, token: String): Connection? {
        val uri = runCatching { URI(endpoint.trim()) }.getOrNull() ?: return null
        val accessToken = token.trim()
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
            uri.query != null || uri.fragment != null || uri.path != "/categorize" ||
            !accessToken.matches(Regex("[A-Za-z0-9_-]{32,128}"))
        ) return null
        return Connection(uri, accessToken)
    }
}
