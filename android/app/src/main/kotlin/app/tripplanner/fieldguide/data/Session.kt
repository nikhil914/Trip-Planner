package app.tripplanner.fieldguide.data

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionState(
    val serverUrl: String? = null,
    val tripId: String? = null,
    val token: String? = null,
    val rev: Long = 0,
    val wifiOnly: Boolean = false,
    val revoked: Boolean = false,
    val demo: Boolean = false,
) {
    val hasTrip: Boolean get() = tripId != null
    val canSync: Boolean get() = !demo && !revoked && serverUrl != null && token != null && tripId != null
}

/** Pairing and sync cursor, kept in app-private preferences. One active trip per phone. */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SessionState> = _state.asStateFlow()
    val current: SessionState get() = _state.value

    val installId: String by lazy {
        prefs.getString(KEY_INSTALL, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_INSTALL, it).apply() }
    }

    private fun read() = SessionState(
        serverUrl = prefs.getString(KEY_SERVER, null),
        tripId = prefs.getString(KEY_TRIP, null),
        token = prefs.getString(KEY_TOKEN, null),
        rev = prefs.getLong(KEY_REV, 0),
        wifiOnly = prefs.getBoolean(KEY_WIFI, false),
        revoked = prefs.getBoolean(KEY_REVOKED, false),
        demo = prefs.getBoolean(KEY_DEMO, false),
    )

    private fun write(next: SessionState) {
        prefs.edit()
            .putString(KEY_SERVER, next.serverUrl)
            .putString(KEY_TRIP, next.tripId)
            .putString(KEY_TOKEN, next.token)
            .putLong(KEY_REV, next.rev)
            .putBoolean(KEY_WIFI, next.wifiOnly)
            .putBoolean(KEY_REVOKED, next.revoked)
            .putBoolean(KEY_DEMO, next.demo)
            .apply()
        _state.value = next
    }

    fun paired(serverUrl: String, tripId: String, token: String) =
        write(SessionState(serverUrl = serverUrl, tripId = tripId, token = token, wifiOnly = current.wifiOnly))

    fun demo(tripId: String) = write(SessionState(tripId = tripId, demo = true, wifiOnly = current.wifiOnly))

    fun setRev(rev: Long) = write(current.copy(rev = rev))

    fun setWifiOnly(value: Boolean) = write(current.copy(wifiOnly = value))

    fun markRevoked() = write(current.copy(revoked = true))

    fun clear() = write(SessionState(wifiOnly = current.wifiOnly))

    private companion object {
        const val KEY_INSTALL = "install_id"
        const val KEY_SERVER = "server_url"
        const val KEY_TRIP = "trip_id"
        const val KEY_TOKEN = "token"
        const val KEY_REV = "rev"
        const val KEY_WIFI = "wifi_only"
        const val KEY_REVOKED = "revoked"
        const val KEY_DEMO = "demo"
    }
}
