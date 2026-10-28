package com.nammameter.app

import android.app.Application
import android.content.Context
import com.nammameter.app.data.AppDatabase
import com.nammameter.app.data.LiveTripStore
import com.nammameter.app.data.SettingsRepository
import com.nammameter.app.data.TripRepository
import com.nammameter.app.tracking.LiveMeterRepository

/** Hand-rolled dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val trips = TripRepository(AppDatabase.create(context).tripDao())
    val liveTripStore = LiveTripStore(context)
    val liveMeter = LiveMeterRepository()
}

class NammaMeterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as NammaMeterApp).container
