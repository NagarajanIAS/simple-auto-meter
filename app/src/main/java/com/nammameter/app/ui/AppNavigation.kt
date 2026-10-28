package com.nammameter.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nammameter.app.appContainer
import com.nammameter.app.tracking.LatLng
import com.nammameter.app.ui.screens.AboutScreen
import com.nammameter.app.ui.screens.DisclaimerScreen
import com.nammameter.app.ui.screens.DriverScreen
import com.nammameter.app.ui.screens.EarningsScreen
import com.nammameter.app.ui.screens.EstimatorScreen
import com.nammameter.app.ui.screens.HistoryScreen
import com.nammameter.app.ui.screens.MapScreen
import com.nammameter.app.ui.screens.MeterScreen
import com.nammameter.app.ui.screens.PermissionScreen
import com.nammameter.app.ui.screens.QrScreen
import com.nammameter.app.ui.screens.SettingsScreen
import com.nammameter.app.ui.screens.SummaryScreen

object Routes {
    const val DISCLAIMER = "disclaimer"
    const val PERMISSION = "permission"
    const val METER = "meter"
    const val SUMMARY = "summary/{tripId}"
    const val HISTORY = "history"
    const val ESTIMATE = "estimate"
    const val MAP = "map?pick={pick}"
    const val SETTINGS = "settings"
    const val DRIVER = "driver"
    const val QR = "qr/{tripId}"
    const val EARNINGS = "earnings"
    const val ABOUT = "about"

    fun summary(tripId: Long) = "summary/$tripId"
    fun map(pick: Boolean) = "map?pick=$pick"
    fun qr(tripId: Long) = "qr/$tripId"
    const val STATIC_QR = -1L

    /** SavedStateHandle key used by the map to hand a picked destination back. */
    const val PICKED_KEY = "picked"
}

@Composable
fun AppNavigation() {
    val container = LocalContext.current.appContainer
    val settings by container.settings.settings.collectAsState(initial = null)
    val loaded = settings ?: run {
        Box(Modifier.fillMaxSize())
        return
    }
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(
        navController = nav,
        startDestination = if (loaded.disclaimerAccepted) Routes.METER else Routes.DISCLAIMER,
    ) {
        composable(Routes.DISCLAIMER) {
            DisclaimerScreen(onAccepted = {
                nav.navigate(Routes.METER) { popUpTo(Routes.DISCLAIMER) { inclusive = true } }
            })
        }
        composable(Routes.PERMISSION) { PermissionScreen(onDone = back) }
        composable(Routes.METER) {
            MeterScreen(
                onNeedPermission = { nav.navigate(Routes.PERMISSION) },
                onTripFinished = { id, showQr ->
                    nav.navigate(Routes.summary(id))
                    if (showQr) nav.navigate(Routes.qr(id))
                },
                onEstimate = { nav.navigate(Routes.ESTIMATE) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onMap = { nav.navigate(Routes.map(false)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SUMMARY, arguments = listOf(navArgument("tripId") { type = NavType.LongType })) {
            val id = it.arguments?.getLong("tripId") ?: 0L
            SummaryScreen(tripId = id, onBack = back, onShowQr = { nav.navigate(Routes.qr(id)) })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = back,
                onOpen = { id -> nav.navigate(Routes.summary(id)) },
                onEarnings = { nav.navigate(Routes.EARNINGS) },
            )
        }
        composable(Routes.ESTIMATE) { entry ->
            val picked by entry.savedStateHandle.getStateFlow<DoubleArray?>(Routes.PICKED_KEY, null).collectAsState()
            EstimatorScreen(
                onBack = back,
                onNeedPermission = { nav.navigate(Routes.PERMISSION) },
                onPickOnMap = { nav.navigate(Routes.map(true)) },
                picked = picked?.let { LatLng(it[0], it[1]) },
                onPickedConsumed = { entry.savedStateHandle[Routes.PICKED_KEY] = null },
            )
        }
        composable(Routes.MAP, arguments = listOf(navArgument("pick") { type = NavType.BoolType; defaultValue = false })) {
            MapScreen(
                pickMode = it.arguments?.getBoolean("pick") ?: false,
                onBack = back,
                onPicked = { p ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(Routes.PICKED_KEY, doubleArrayOf(p.lat, p.lng))
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = back,
                onDriverMode = { nav.navigate(Routes.DRIVER) },
                onAbout = { nav.navigate(Routes.ABOUT) },
                onPermissions = { nav.navigate(Routes.PERMISSION) },
            )
        }
        composable(Routes.DRIVER) {
            DriverScreen(
                onBack = back,
                onStaticQr = { nav.navigate(Routes.qr(Routes.STATIC_QR)) },
                onEarnings = { nav.navigate(Routes.EARNINGS) },
            )
        }
        composable(Routes.QR, arguments = listOf(navArgument("tripId") { type = NavType.LongType })) {
            QrScreen(tripId = it.arguments?.getLong("tripId") ?: Routes.STATIC_QR, onBack = back)
        }
        composable(Routes.EARNINGS) { EarningsScreen(onBack = back) }
        composable(Routes.ABOUT) { AboutScreen(onBack = back) }
    }
}
