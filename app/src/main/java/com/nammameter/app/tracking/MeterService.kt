package com.nammameter.app.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.nammameter.app.MainActivity
import com.nammameter.app.R
import com.nammameter.app.appContainer
import com.nammameter.app.fare.FareCalculator
import com.nammameter.app.fare.FareConfig
import com.nammameter.app.ui.formatKm
import com.nammameter.app.ui.formatRupees
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Foreground service (type "location") that runs the meter, so it keeps counting with the
 * screen off. It is only ever started from the visible activity, so background location
 * permission is not needed.
 */
class MeterService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { appContainer }
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var trip: ActiveTrip? = null
    private var tickerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stopping = false

    private class ActiveTrip(
        val startEpochMs: Long,
        val config: FareConfig,
        val tariffName: String,
        val tariffEffective: String?,
        val accumulator: TripAccumulator,
        val track: ArrayList<LatLng> = ArrayList(),
    )

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val active = trip ?: return
            for (location in result.locations) {
                val fix = LocationFix(
                    lat = location.latitude,
                    lng = location.longitude,
                    accuracyM = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
                    timeMs = location.elapsedRealtimeNanos / 1_000_000,
                    speedMps = if (location.hasSpeed()) location.speed else null,
                )
                val r = active.accumulator.onFix(fix)
                if (r.addedMeters > 0 && r.point != null && active.track.size < MAX_TRACK_POINTS) {
                    active.track.add(r.point)
                }
            }
            publish(active)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(resume = false)
            ACTION_RESUME -> begin(resume = true)
            ACTION_TOGGLE_WAIT -> trip?.let {
                it.accumulator.tick(SystemClock.elapsedRealtime())
                it.accumulator.manualWait = !it.accumulator.manualWait
                publish(it)
            }
            ACTION_STOP -> finish()
            else -> if (trip == null) stopSelf()
        }
        // Never auto-restart from the background: a killed trip is resumed from the activity.
        return START_NOT_STICKY
    }

    private fun begin(resume: Boolean) {
        if (trip != null || tickerJob != null) return
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(getString(R.string.notif_starting)),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        tickerJob = scope.launch {
            val settings = container.settings.settings.first()
            val snapshot = if (resume) container.liveTripStore.load() else null
            val now = SystemClock.elapsedRealtime()
            val active = if (snapshot != null) {
                ActiveTrip(
                    startEpochMs = snapshot.startEpochMs,
                    config = snapshot.fareConfig,
                    tariffName = snapshot.tariffName,
                    tariffEffective = snapshot.tariffEffective,
                    accumulator = TripAccumulator(
                        settings.meter, now, snapshot.distanceMeters,
                        snapshot.movingMs, snapshot.waitingMs, snapshot.manualWait,
                    ),
                )
            } else {
                val preset = settings.activePreset
                ActiveTrip(
                    startEpochMs = System.currentTimeMillis(),
                    config = settings.fareConfig,
                    tariffName = preset?.name ?: getString(R.string.tariff_custom),
                    tariffEffective = preset?.effectiveDate?.toString(),
                    accumulator = TripAccumulator(settings.meter, now),
                )
            }
            trip = active
            acquireWakeLock()
            if (!requestLocation()) {
                container.liveMeter.publish(LiveMeter())
                stopEverything()
                return@launch
            }
            var ticks = 0
            while (isActive && !stopping) {
                active.accumulator.tick(SystemClock.elapsedRealtime())
                publish(active)
                if (ticks % NOTIFY_EVERY_TICKS == 0) updateNotification(active)
                if (ticks % PERSIST_EVERY_TICKS == 0) persist(active)
                ticks++
                delay(TICK_MS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocation(): Boolean {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_LOCATION_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()
        return try {
            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun fareFor(active: ActiveTrip) = FareCalculator.calculate(
        active.config,
        active.accumulator.distanceMeters,
        active.accumulator.waitingMs / 1000,
        toLocal(active.startEpochMs),
        LocalDateTime.now(),
    )

    private fun publish(active: ActiveTrip) {
        val acc = active.accumulator
        val now = SystemClock.elapsedRealtime()
        container.liveMeter.publish(
            LiveMeter(
                running = true,
                startEpochMs = active.startEpochMs,
                distanceMeters = acc.distanceMeters,
                movingMs = acc.movingMs,
                waitingMs = acc.waitingMs,
                manualWait = acc.manualWait,
                autoWaiting = !acc.manualWait && acc.isAutoWaiting(now),
                fare = fareFor(active),
                tariffName = active.tariffName,
                tariffEffective = active.tariffEffective,
                gpsOk = acc.lastFixMs?.let { now - it < GPS_STALE_MS } ?: false,
                accuracyM = acc.lastAccuracyM,
                position = acc.lastPoint,
                track = active.track.toList(),
            ),
        )
    }

    private suspend fun persist(active: ActiveTrip) {
        val acc = active.accumulator
        container.liveTripStore.save(
            TripSnapshot(
                startEpochMs = active.startEpochMs,
                distanceMeters = acc.distanceMeters,
                movingMs = acc.movingMs,
                waitingMs = acc.waitingMs,
                manualWait = acc.manualWait,
                tariffName = active.tariffName,
                tariffEffective = active.tariffEffective,
                fareConfig = active.config,
                savedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun finish() {
        val active = trip
        if (active == null) {
            scope.launch { container.liveTripStore.clear() }
            stopEverything()
            return
        }
        if (stopping) return
        stopping = true
        locationClient.removeLocationUpdates(locationCallback)
        active.accumulator.tick(SystemClock.elapsedRealtime())
        val endEpochMs = System.currentTimeMillis()
        val acc = active.accumulator
        val fare = FareCalculator.calculate(
            active.config, acc.distanceMeters, acc.waitingMs / 1000,
            toLocal(active.startEpochMs), toLocal(endEpochMs),
        )
        scope.launch {
            val id = container.trips.save(
                startEpochMs = active.startEpochMs,
                endEpochMs = endEpochMs,
                distanceMeters = acc.distanceMeters,
                movingSeconds = acc.movingMs / 1000,
                waitingSeconds = acc.waitingMs / 1000,
                tariffName = active.tariffName,
                tariffEffective = active.tariffEffective,
                fare = fare,
            )
            container.liveTripStore.clear()
            container.liveMeter.finished(id)
            stopEverything()
        }
    }

    private fun stopEverything() {
        trip = null
        locationClient.removeLocationUpdates(locationCallback)
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationClient.removeLocationUpdates(locationCallback)
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NammaMeter:meter").apply {
            setReferenceCounted(false)
            acquire(WAKE_LOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
    }

    private fun updateNotification(active: ActiveTrip) {
        val fare = fareFor(active)
        val text = getString(
            R.string.notif_running,
            formatRupees(fare.total),
            formatKm(active.accumulator.distanceMeters),
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val toggleWait = PendingIntent.getService(
            this, 1,
            Intent(this, MeterService::class.java).setAction(ACTION_TOGGLE_WAIT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, getString(R.string.wait), toggleWait)
            .build()
    }

    companion object {
        private const val ACTION_START = "com.nammameter.app.START"
        private const val ACTION_RESUME = "com.nammameter.app.RESUME"
        private const val ACTION_STOP = "com.nammameter.app.STOP"
        private const val ACTION_TOGGLE_WAIT = "com.nammameter.app.TOGGLE_WAIT"
        private const val CHANNEL_ID = "meter"
        private const val NOTIFICATION_ID = 42
        private const val TICK_MS = 1000L
        private const val NOTIFY_EVERY_TICKS = 2
        private const val PERSIST_EVERY_TICKS = 5
        private const val LOCATION_INTERVAL_MS = 1000L
        private const val MIN_LOCATION_INTERVAL_MS = 1000L
        private const val GPS_STALE_MS = 10_000L
        private const val MAX_TRACK_POINTS = 20_000
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L

        /** Must be called while the activity is visible. */
        fun start(context: Context) = send(context, ACTION_START, foreground = true)
        fun resume(context: Context) = send(context, ACTION_RESUME, foreground = true)
        fun stop(context: Context) = send(context, ACTION_STOP, foreground = false)
        fun toggleWait(context: Context) = send(context, ACTION_TOGGLE_WAIT, foreground = false)

        private fun send(context: Context, action: String, foreground: Boolean) {
            val intent = Intent(context, MeterService::class.java).setAction(action)
            if (foreground) ContextCompat.startForegroundService(context, intent) else context.startService(intent)
        }

        private fun toLocal(epochMs: Long): LocalDateTime =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
    }
}
