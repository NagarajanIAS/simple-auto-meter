package com.nammameter.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nammameter.app.tracking.TripSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.liveTripStore by preferencesDataStore(name = "live_trip")

/** Holds the in-progress trip so it survives a process kill. */
class LiveTripStore(context: Context) {
    private val store = context.liveTripStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val snapshot: Flow<TripSnapshot?> = store.data.map { prefs ->
        prefs[KEY]?.let { runCatching { json.decodeFromString(TripSnapshot.serializer(), it) }.getOrNull() }
    }

    suspend fun load(): TripSnapshot? = snapshot.first()

    suspend fun save(snapshot: TripSnapshot) {
        store.edit { it[KEY] = json.encodeToString(TripSnapshot.serializer(), snapshot) }
    }

    suspend fun clear() {
        store.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("snapshot")
    }
}
