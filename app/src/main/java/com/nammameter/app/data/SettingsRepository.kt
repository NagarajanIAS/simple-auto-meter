package com.nammameter.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nammameter.app.fare.FareConfig
import com.nammameter.app.routing.RoutingProviderId
import com.nammameter.app.tracking.MeterParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.settingsStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.settingsStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[PRESET_ID] = updated.presetId
            prefs[CUSTOM_FARE] = json.encodeToString(FareConfig.serializer(), updated.customFare)
            prefs[METER] = json.encodeToString(MeterParams.serializer(), updated.meter)
            prefs[ROAD_FACTOR] = updated.roadFactor
            prefs[ROUTING] = updated.routingProvider.name
            prefs[ORS_KEY] = updated.orsApiKey
            prefs[TILE_URL] = updated.tileUrl
            prefs[DRIVER] = json.encodeToString(DriverSettings.serializer(), updated.driver)
            prefs[DISCLAIMER] = updated.disclaimerAccepted
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            presetId = this[PRESET_ID] ?: defaults.presetId,
            customFare = decode(this[CUSTOM_FARE], FareConfig.serializer()) ?: defaults.customFare,
            meter = decode(this[METER], MeterParams.serializer()) ?: defaults.meter,
            roadFactor = this[ROAD_FACTOR] ?: defaults.roadFactor,
            routingProvider = this[ROUTING]
                ?.let { runCatching { RoutingProviderId.valueOf(it) }.getOrNull() }
                ?: defaults.routingProvider,
            orsApiKey = this[ORS_KEY] ?: defaults.orsApiKey,
            tileUrl = this[TILE_URL] ?: defaults.tileUrl,
            driver = decode(this[DRIVER], DriverSettings.serializer()) ?: defaults.driver,
            disclaimerAccepted = this[DISCLAIMER] ?: defaults.disclaimerAccepted,
        )
    }

    private fun <T> decode(raw: String?, serializer: kotlinx.serialization.KSerializer<T>): T? =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    private companion object {
        val PRESET_ID = stringPreferencesKey("preset_id")
        val CUSTOM_FARE = stringPreferencesKey("custom_fare")
        val METER = stringPreferencesKey("meter_params")
        val ROAD_FACTOR = doublePreferencesKey("road_factor")
        val ROUTING = stringPreferencesKey("routing_provider")
        val ORS_KEY = stringPreferencesKey("ors_api_key")
        val TILE_URL = stringPreferencesKey("tile_url")
        val DRIVER = stringPreferencesKey("driver")
        val DISCLAIMER = booleanPreferencesKey("disclaimer_accepted")
    }
}
