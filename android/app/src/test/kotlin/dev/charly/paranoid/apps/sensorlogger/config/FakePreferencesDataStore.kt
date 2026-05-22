package dev.charly.paranoid.apps.sensorlogger.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * In-memory `DataStore<Preferences>` for unit tests — no filesystem, no
 * coroutine-test dependency required.
 */
internal class FakePreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}

/**
 * `DataStore<Preferences>` whose `data` flow always fails with [IOException],
 * used to exercise the store's `.catch { }` fallback.
 */
internal class ErroringPreferencesDataStore : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw IOException("simulated read failure") }
    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw UnsupportedOperationException("not used in fallback test")
}
