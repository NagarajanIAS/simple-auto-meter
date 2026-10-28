package com.nammameter.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nammameter.app.BuildConfig
import com.nammameter.app.R
import com.nammameter.app.appContainer
import com.nammameter.app.fare.FarePresets
import com.nammameter.app.tracking.LocationHelper
import com.nammameter.app.ui.components.BackTopBar
import com.nammameter.app.ui.components.SectionTitle
import kotlinx.coroutines.launch

@Composable
fun DisclaimerScreen(onAccepted: () -> Unit) {
    val container = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Default.Warning, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            DisclaimerCard()
            Text(stringResource(R.string.disclaimer_more))
            Text(stringResource(R.string.privacy_summary))
            Button(
                onClick = {
                    scope.launch {
                        container.settings.update { it.copy(disclaimerAccepted = true) }
                        onAccepted()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text(stringResource(R.string.i_understand)) }
        }
    }
}

@Composable
private fun DisclaimerCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Text(
            stringResource(R.string.disclaimer_full),
            Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Prominent disclosure shown before the system location permission dialog. */
@Composable
fun PermissionScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (LocationHelper.hasFineLocation(context)) onDone() else denied = true
    }
    Scaffold(topBar = { BackTopBar(stringResource(R.string.disclosure_title), onDone) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.LocationOn, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.secondary)
            Text(stringResource(R.string.disclosure_heading), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.disclosure_body))
            Text("• " + stringResource(R.string.disclosure_point_1))
            Text("• " + stringResource(R.string.disclosure_point_2))
            Text("• " + stringResource(R.string.disclosure_point_3))
            Text("• " + stringResource(R.string.disclosure_point_4))
            if (denied) {
                Text(stringResource(R.string.permission_denied), color = MaterialTheme.colorScheme.error)
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(stringResource(R.string.open_app_settings)) }
            }
            Button(
                onClick = { launcher.launch(LocationHelper.requestedPermissions()) },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text(stringResource(R.string.continue_)) }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.not_now))
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.about_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            DisclaimerCard()
            Text(stringResource(R.string.disclaimer_more))
            SectionTitle(stringResource(R.string.privacy))
            Text(stringResource(R.string.privacy_summary))
            SectionTitle(stringResource(R.string.tariff_sources))
            FarePresets.all.forEach { Text("• ${it.name} — ${it.source}") }
            Text(stringResource(R.string.tariff_verify))
            SectionTitle(stringResource(R.string.credits))
            Text(stringResource(R.string.credits_text))
            Spacer(Modifier.height(24.dp))
        }
    }
}
