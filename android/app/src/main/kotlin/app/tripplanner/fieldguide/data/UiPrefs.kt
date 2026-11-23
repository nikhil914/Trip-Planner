package app.tripplanner.fieldguide.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT, SUNLIGHT }

data class UiSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val lockEnabled: Boolean = false,
    /** Minutes in the background before the lock returns. 0 locks at once. */
    val lockAfterMinutes: Int = 5,
    val primerDone: Boolean = false,
)

/**
 * Display, lock and list preferences. These stay on the phone and never sync: the phone only ever
 * writes status, is_checked and start_minute to the planner.
 */
class UiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)
    private val listPrefs = context.getSharedPreferences("list_ticks", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<UiSettings> = _settings.asStateFlow()

    private val _ticks = MutableStateFlow(listPrefs.getStringSet(KEY_TICKS, emptySet()).orEmpty().toSet())

    /** Ticked list lines, as "blockId|lineKey". */
    val ticks: StateFlow<Set<String>> = _ticks.asStateFlow()

    private fun read() = UiSettings(
        theme = runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        lockEnabled = prefs.getBoolean(KEY_LOCK, false),
        lockAfterMinutes = prefs.getInt(KEY_LOCK_AFTER, 5),
        primerDone = prefs.getBoolean(KEY_PRIMER, false),
    )

    private fun write(next: UiSettings) {
        prefs.edit()
            .putString(KEY_THEME, next.theme.name)
            .putBoolean(KEY_LOCK, next.lockEnabled)
            .putInt(KEY_LOCK_AFTER, next.lockAfterMinutes)
            .putBoolean(KEY_PRIMER, next.primerDone)
            .apply()
        _settings.value = next
    }

    fun setTheme(mode: ThemeMode) = write(_settings.value.copy(theme = mode))
    fun setLock(enabled: Boolean) = write(_settings.value.copy(lockEnabled = enabled))
    fun setLockAfter(minutes: Int) = write(_settings.value.copy(lockAfterMinutes = minutes))
    fun setPrimerDone(done: Boolean) = write(_settings.value.copy(primerDone = done))

    fun setTick(key: String, ticked: Boolean) {
        val next = if (ticked) _ticks.value + key else _ticks.value - key
        listPrefs.edit().putStringSet(KEY_TICKS, next).apply()
        _ticks.value = next
    }

    fun clearTicks() {
        listPrefs.edit().clear().apply()
        _ticks.value = emptySet()
    }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_LOCK = "lock_enabled"
        const val KEY_LOCK_AFTER = "lock_after_minutes"
        const val KEY_PRIMER = "primer_done"
        const val KEY_TICKS = "ticks"
    }
}
