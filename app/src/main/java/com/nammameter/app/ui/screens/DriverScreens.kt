package com.nammameter.app.ui.screens

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nammameter.app.AppContainer
import com.nammameter.app.R
import com.nammameter.app.data.AppSettings
import com.nammameter.app.data.DriverSettings
import com.nammameter.app.data.TripEntity
import com.nammameter.app.ui.Routes
import com.nammameter.app.ui.appViewModel
import com.nammameter.app.ui.components.BackTopBar
import com.nammameter.app.ui.components.SwitchRow
import com.nammameter.app.ui.formatRupees
import com.nammameter.app.ui.formatRupeesExact
import com.nammameter.app.upi.PinHasher
import com.nammameter.app.upi.QrCode
import com.nammameter.app.upi.Upi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ---------- Driver mode settings (PIN protected) ----------

class DriverViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Unlock lasts only while this screen is on the back stack. */
    var unlocked by mutableStateOf(false)
        private set

    /** Milliseconds until another attempt is allowed, or 0. */
    fun lockRemainingMs(): Long =
        ((settings.value?.driver?.pinLockedUntilEpochMs ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0)

    /** Wrong attempts beyond [FREE_ATTEMPTS] lock the screen for a doubling delay. The counter survives restarts. */
    suspend fun tryUnlock(pin: String): Boolean {
        val d = settings.value?.driver ?: return false
        if (lockRemainingMs() > 0) return false
        val ok = withContext(Dispatchers.Default) { PinHasher.verify(pin, d.pinSalt, d.pinHash) }
        container.settings.update { s ->
            val attempts = if (ok) 0 else s.driver.failedPinAttempts + 1
            val lockMs = if (attempts > FREE_ATTEMPTS) BASE_LOCK_MS shl (attempts - FREE_ATTEMPTS - 1).coerceAtMost(8) else 0L
            s.copy(driver = s.driver.copy(failedPinAttempts = attempts, pinLockedUntilEpochMs = System.currentTimeMillis() + lockMs))
        }
        unlocked = ok
        return ok
    }

    fun createPin(pin: String) {
        setPin(pin)
        unlocked = true
    }

    /** Changing the PIN writes the new hash in one step; there is never a moment with no PIN. */
    fun changePin(newPin: String) = setPin(newPin)

    private fun setPin(pin: String) {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        updateDriver { it.copy(pinHash = hash, pinSalt = salt, failedPinAttempts = 0, pinLockedUntilEpochMs = 0) }
    }

    private companion object {
        const val FREE_ATTEMPTS = 4
        const val BASE_LOCK_MS = 30_000L
    }

    fun updateDriver(transform: (DriverSettings) -> DriverSettings) {
        viewModelScope.launch { container.settings.update { it.copy(driver = transform(it.driver)) } }
    }
}

@Composable
fun DriverScreen(onBack: () -> Unit, onStaticQr: () -> Unit, onEarnings: () -> Unit) {
    val vm = appViewModel { DriverViewModel(it) }
    val s = vm.settings.collectAsState().value

    Scaffold(topBar = { BackTopBar(stringResource(R.string.driver_mode), onBack) }) { padding ->
        if (s == null) return@Scaffold
        val d = s.driver
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            var changingPin by remember { mutableStateOf(false) }
            when {
                !d.hasPin -> CreatePin(onCreated = vm::createPin)
                !vm.unlocked -> EnterPin(onSubmit = vm::tryUnlock, lockRemainingMs = vm::lockRemainingMs)
                changingPin -> CreatePin(onCreated = { vm.changePin(it); changingPin = false })
                else -> DriverSettingsForm(
                    driver = d,
                    onChange = vm::updateDriver,
                    onStaticQr = onStaticQr,
                    onEarnings = onEarnings,
                    onChangePin = { changingPin = true },
                )
            }
        }
    }
}

@Composable
private fun PinField(label: String, value: String, onValue: (String) -> Unit, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= 4 && v.all(Char::isDigit)) onValue(v) },
        label = { Text(label) },
        isError = isError,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun CreatePin(onCreated: (String) -> Unit) {
    // Plain remember: PIN digits must not be written to saved instance state.
    var pin by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val mismatch = again.length == 4 && pin != again
    Text(stringResource(R.string.pin_create_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.pin_create_text))
    PinField(stringResource(R.string.pin_new), pin, { pin = it })
    PinField(stringResource(R.string.pin_repeat), again, { again = it }, isError = mismatch)
    if (mismatch) Text(stringResource(R.string.pin_mismatch), color = MaterialTheme.colorScheme.error)
    Button(
        onClick = { onCreated(pin) },
        enabled = PinHasher.isValidPin(pin) && pin == again,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(stringResource(R.string.pin_save)) }
}

@Composable
private fun EnterPin(onSubmit: suspend (String) -> Boolean, lockRemainingMs: () -> Long) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var lockSeconds by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(wrong, checking) {
        while (true) {
            lockSeconds = (lockRemainingMs() + 999) / 1000
            if (lockSeconds == 0L) break
            delay(1000)
        }
    }
    Text(stringResource(R.string.pin_enter_title), style = MaterialTheme.typography.titleLarge)
    PinField(stringResource(R.string.pin), pin, { pin = it; wrong = false }, isError = wrong)
    if (lockSeconds > 0) {
        Text(stringResource(R.string.pin_locked, lockSeconds), color = MaterialTheme.colorScheme.error)
    } else if (wrong) {
        Text(stringResource(R.string.pin_wrong), color = MaterialTheme.colorScheme.error)
    }
    Button(
        onClick = {
            checking = true
            scope.launch {
                wrong = !onSubmit(pin)
                if (wrong) pin = ""
                checking = false
            }
        },
        enabled = pin.length == 4 && !checking && lockSeconds == 0L,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(stringResource(R.string.unlock)) }
}

@Composable
private fun DriverSettingsForm(
    driver: DriverSettings,
    onChange: ((DriverSettings) -> DriverSettings) -> Unit,
    onStaticQr: () -> Unit,
    onEarnings: () -> Unit,
    onChangePin: () -> Unit,
) {
    var vpa by rememberSaveable { mutableStateOf(driver.vpa) }
    var name by rememberSaveable { mutableStateOf(driver.payeeName) }
    val vpaValid = Upi.isValidVpa(vpa)
    val dirty = vpa.trim() != driver.vpa || name.trim() != driver.payeeName

    SwitchRow(
        stringResource(R.string.driver_mode_enable),
        driver.enabled,
        { on -> onChange { it.copy(enabled = on) } },
        supporting = stringResource(R.string.driver_mode_text),
    )
    OutlinedTextField(
        value = vpa,
        onValueChange = { vpa = it.trim() },
        label = { Text(stringResource(R.string.upi_id)) },
        placeholder = { Text("name@bank") },
        isError = vpa.isNotEmpty() && !vpaValid,
        supportingText = { if (vpa.isNotEmpty() && !vpaValid) Text(stringResource(R.string.upi_id_invalid)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.payee_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onChange { it.copy(vpa = vpa.trim(), payeeName = name.trim()) } },
        enabled = dirty && vpaValid && name.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp),
    ) { Text(stringResource(R.string.save)) }
    Spacer(Modifier.height(16.dp))
    OutlinedButton(
        onClick = onStaticQr,
        enabled = driver.vpa.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(stringResource(R.string.static_qr)) }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onEarnings, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(stringResource(R.string.earnings_title))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(stringResource(R.string.pin_change))
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.upi_confirm_note), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
}

// ---------- Full-screen UPI QR ----------

class QrViewModel(container: AppContainer, tripId: Long) : ViewModel() {
    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val trip: StateFlow<TripEntity?> =
        (if (tripId == Routes.STATIC_QR) flowOf(null) else container.trips.observe(tripId))
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun QrScreen(tripId: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "qr-$tripId") { QrViewModel(it, tripId) }
    val settings by vm.settings.collectAsState()
    val trip by vm.trip.collectAsState()
    val isStatic = tripId == Routes.STATIC_QR

    KeepScreenBright()

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.upi_qr_title), onBack) },
        containerColor = Color.White,
    ) { padding ->
        val driver = settings?.driver ?: return@Scaffold
        val amount = trip?.total
        if (!isStatic && amount == null) return@Scaffold
        val note = if (isStatic) null else stringResource(
            R.string.upi_note,
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")),
        )
        val uri = remember(driver, amount, note) { Upi.payUri(driver.vpa, driver.payeeName, amount, note) }
        val bitmap = remember(uri) { QrCode.bitmap(uri, 800).asImageBitmap() }

        Column(
            Modifier.fillMaxSize().background(Color.White).padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (amount != null) formatRupees(amount) else stringResource(R.string.qr_any_amount),
                fontSize = if (amount != null) 80.sp else 36.sp,
                lineHeight = if (amount != null) 84.sp else 44.sp,
                fontWeight = FontWeight.Black,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
            if (amount != null) Text(formatRupeesExact(amount), color = Color.DarkGray)
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.upi_qr_title),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(vertical = 8.dp),
            )
            Text(driver.payeeName, color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(driver.vpa, color = Color.Black, fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.upi_confirm_note),
                color = Color(0xFFB00020),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Keeps the screen on at full brightness while the QR is visible. */
@Composable
private fun KeepScreenBright() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val previous = window.attributes.screenBrightness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.attributes = window.attributes.apply { screenBrightness = previous }
        }
    }
}
