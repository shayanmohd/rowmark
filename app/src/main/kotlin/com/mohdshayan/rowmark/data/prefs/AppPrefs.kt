package com.mohdshayan.rowmark.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

// One DataStore per process. The delegate must stay top level so every AppPrefs shares it.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class VolumeDownAction { UNDO, MINUS, NOTHING }

/** Every setting at once, so a screen or the feedback layer reads one snapshot. */
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val keepAwake: Boolean = true,
    val haptics: Boolean = true,
    val bell: Boolean = true,
    val alertVibrate: Boolean = true,
    val countWithKeys: Boolean = true,
    val volumeDownAction: VolumeDownAction = VolumeDownAction.UNDO,
    val extraCountKeycode: Int = 0,
    val lastProjectId: Long = 0L,
    val tileProjectId: Long = 0L,
    val sessionsCompleted: Int = 0,
    val unlockCardShown: Boolean = false,
    val reviewPrompted: Boolean = false,
    val keyHintShown: Boolean = false,
)

/** Typed wrapper over Preferences DataStore. Lists live in Room; these are switches and small flags. */
class AppPrefs(private val context: Context) {

    object Keys {
        val THEME = stringPreferencesKey("theme")
        val KEEP_AWAKE = booleanPreferencesKey("keep_awake")
        val HAPTICS = booleanPreferencesKey("haptics")
        val BELL = booleanPreferencesKey("bell")
        val ALERT_VIBRATE = booleanPreferencesKey("alert_vibrate")
        val COUNT_WITH_KEYS = booleanPreferencesKey("count_with_keys")
        val VOLUME_DOWN_ACTION = stringPreferencesKey("volume_down_action")
        val EXTRA_COUNT_KEYCODE = intPreferencesKey("extra_count_keycode")
        val LAST_PROJECT_ID = longPreferencesKey("last_project_id")
        val TILE_PROJECT_ID = longPreferencesKey("tile_project_id")
        val SESSIONS_COMPLETED = intPreferencesKey("sessions_completed")
        val UNLOCK_CARD_SHOWN = booleanPreferencesKey("unlock_card_shown")
        val REVIEW_PROMPTED = booleanPreferencesKey("review_prompted")
        val KEY_HINT_SHOWN = booleanPreferencesKey("key_hint_shown")
    }

    private val data: Flow<Preferences> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val settings: Flow<Settings> = data.map { p ->
        Settings(
            theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            keepAwake = p[Keys.KEEP_AWAKE] ?: true,
            haptics = p[Keys.HAPTICS] ?: true,
            bell = p[Keys.BELL] ?: true,
            alertVibrate = p[Keys.ALERT_VIBRATE] ?: true,
            countWithKeys = p[Keys.COUNT_WITH_KEYS] ?: true,
            volumeDownAction = p[Keys.VOLUME_DOWN_ACTION]?.let { runCatching { VolumeDownAction.valueOf(it) }.getOrNull() }
                ?: VolumeDownAction.UNDO,
            extraCountKeycode = p[Keys.EXTRA_COUNT_KEYCODE] ?: 0,
            lastProjectId = p[Keys.LAST_PROJECT_ID] ?: 0L,
            tileProjectId = p[Keys.TILE_PROJECT_ID] ?: 0L,
            sessionsCompleted = p[Keys.SESSIONS_COMPLETED] ?: 0,
            unlockCardShown = p[Keys.UNLOCK_CARD_SHOWN] ?: false,
            reviewPrompted = p[Keys.REVIEW_PROMPTED] ?: false,
            keyHintShown = p[Keys.KEY_HINT_SHOWN] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.THEME] = mode.name }
    suspend fun setKeepAwake(on: Boolean) = edit { it[Keys.KEEP_AWAKE] = on }
    suspend fun setHaptics(on: Boolean) = edit { it[Keys.HAPTICS] = on }
    suspend fun setBell(on: Boolean) = edit { it[Keys.BELL] = on }
    suspend fun setAlertVibrate(on: Boolean) = edit { it[Keys.ALERT_VIBRATE] = on }
    suspend fun setCountWithKeys(on: Boolean) = edit { it[Keys.COUNT_WITH_KEYS] = on }
    suspend fun setVolumeDownAction(action: VolumeDownAction) = edit { it[Keys.VOLUME_DOWN_ACTION] = action.name }
    suspend fun setExtraCountKeycode(code: Int) = edit { it[Keys.EXTRA_COUNT_KEYCODE] = code }
    suspend fun setLastProjectId(id: Long) = edit { it[Keys.LAST_PROJECT_ID] = id }
    suspend fun setTileProjectId(id: Long) = edit { it[Keys.TILE_PROJECT_ID] = id }
    suspend fun bumpSessionsCompleted() = edit { it[Keys.SESSIONS_COMPLETED] = (it[Keys.SESSIONS_COMPLETED] ?: 0) + 1 }
    suspend fun setUnlockCardShown(shown: Boolean) = edit { it[Keys.UNLOCK_CARD_SHOWN] = shown }
    suspend fun setReviewPrompted(done: Boolean) = edit { it[Keys.REVIEW_PROMPTED] = done }
    suspend fun setKeyHintShown(shown: Boolean) = edit { it[Keys.KEY_HINT_SHOWN] = shown }

    /** The user-facing switches, for the backup file. Flags about prompts and ids stay on this phone. */
    suspend fun exportable(): Map<String, String> {
        val s = current()
        return mapOf(
            "theme" to s.theme.name, "keep_awake" to s.keepAwake.toString(), "haptics" to s.haptics.toString(),
            "bell" to s.bell.toString(), "alert_vibrate" to s.alertVibrate.toString(),
            "count_with_keys" to s.countWithKeys.toString(), "volume_down_action" to s.volumeDownAction.name,
            "extra_count_keycode" to s.extraCountKeycode.toString(),
        )
    }

    suspend fun importSettings(values: Map<String, String>) {
        edit { p ->
            values["theme"]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v }?.let { p[Keys.THEME] = it.name } }
            values["keep_awake"]?.toBooleanStrictOrNull()?.let { p[Keys.KEEP_AWAKE] = it }
            values["haptics"]?.toBooleanStrictOrNull()?.let { p[Keys.HAPTICS] = it }
            values["bell"]?.toBooleanStrictOrNull()?.let { p[Keys.BELL] = it }
            values["alert_vibrate"]?.toBooleanStrictOrNull()?.let { p[Keys.ALERT_VIBRATE] = it }
            values["count_with_keys"]?.toBooleanStrictOrNull()?.let { p[Keys.COUNT_WITH_KEYS] = it }
            values["volume_down_action"]?.let { v -> VolumeDownAction.entries.firstOrNull { it.name == v }?.let { p[Keys.VOLUME_DOWN_ACTION] = it.name } }
            values["extra_count_keycode"]?.toIntOrNull()?.let { p[Keys.EXTRA_COUNT_KEYCODE] = it }
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            context.dataStore.edit { block(it) }
        } catch (e: IOException) {
            // The in-memory state still holds; the next write tries again.
        }
    }
}
