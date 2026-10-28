package com.nammameter.app.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import java.io.IOException
import javax.net.ssl.HttpsURLConnection
import java.net.URL

/**
 * Road distance from openrouteservice.org using the user's own free API key.
 * This is the only part of the app that sends anything over the network, and only the
 * two coordinates of the requested route are sent.
 */
class OpenRouteServiceProvider(private val apiKey: String) : RoutingProvider {
    override val id = RoutingProviderId.OPENROUTESERVICE
    override val isAvailable: Boolean get() = apiKey.isNotBlank()

    override suspend fun route(request: RouteRequest): Result<RouteEstimate> {
        if (!isAvailable) return Result.failure(IllegalStateException("No OpenRouteService API key"))
        val from = request.from ?: return Result.failure(IllegalArgumentException("Start point unknown"))
        val to = request.to ?: return Result.failure(IllegalArgumentException("Destination not chosen"))
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    putJsonArray("coordinates") {
                        addJsonArray { add(from.lng); add(from.lat) }
                        addJsonArray { add(to.lng); add(to.lat) }
                    }
                    put("units", "m")
                }.toString()
                // HttpsURLConnection cast guarantees TLS; no redirects, so the key only ever goes to ENDPOINT.
                val conn = (URL(ENDPOINT).openConnection() as HttpsURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("Authorization", apiKey.trim())
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    conn.outputStream.use { it.write(body.toByteArray()) }
                    if (conn.responseCode !in 200..299) throw IOException("OpenRouteService HTTP ${conn.responseCode}")
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    RouteEstimate(parseDistanceMeters(text) / 1000.0, approximate = false, providerId = id)
                } finally {
                    conn.disconnect()
                }
            }
        }
    }

    companion object {
        private const val ENDPOINT = "https://api.openrouteservice.org/v2/directions/driving-car"

        /** Reads routes[0].summary.distance (metres) from a directions response. */
        fun parseDistanceMeters(json: String): Double =
            Json.parseToJsonElement(json).jsonObject["routes"]!!.jsonArray[0]
                .jsonObject["summary"]!!.jsonObject["distance"]!!.jsonPrimitive.double
    }
}
