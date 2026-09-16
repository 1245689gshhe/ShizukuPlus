package af.shizuku.manager.devicecontrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import af.shizuku.manager.R
import rikka.shizuku.ShizukuPlusAPI
import timber.log.Timber

private const val STREAM_RING = 2
private const val STREAM_MUSIC = 3
private const val STREAM_ALARM = 4

// Most Android devices top out at index 15; used as slider ceiling when no max API is present.
private const val VOLUME_MAX = 15

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlScreen(onBackClick: () -> Unit) {
    val scope = rememberCoroutineScope()

    // ── Connectivity state ────────────────────────────────────────────────────
    var airplane by remember { mutableStateOf(false) }
    var wifi by remember { mutableStateOf(false) }
    var bluetooth by remember { mutableStateOf(false) }
    var mobileData by remember { mutableStateOf(false) }
    var nfc by remember { mutableStateOf(false) }

    // ── Display state ─────────────────────────────────────────────────────────
    var autoBrightness by remember { mutableStateOf(true) }
    var brightness by remember { mutableIntStateOf(128) }
    var autoRotate by remember { mutableStateOf(false) }

    // ── Audio state ───────────────────────────────────────────────────────────
    var volumeMedia by remember { mutableIntStateOf(8) }
    var volumeRing by remember { mutableIntStateOf(8) }
    var volumeAlarm by remember { mutableIntStateOf(8) }

    // ── System appearance state ───────────────────────────────────────────────
    var animations by remember { mutableStateOf(true) }
    var fontScale by remember { mutableFloatStateOf(1.0f) }

    // ── Power confirmation dialogs ─────────────────────────────────────────────
    var showRebootDialog by remember { mutableStateOf(false) }
    var showShutdownDialog by remember { mutableStateOf(false) }

    // Load initial state from device
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val dc = ShizukuPlusAPI.DeviceControl
                val ds = ShizukuPlusAPI.PrivilegedDataSource

                // Read settings via getSetting
                airplane = dc.getSetting("global", "airplane_mode_on") == "1"
                autoBrightness = dc.getSetting("system", "screen_brightness_mode") == "1"
                brightness = dc.getSetting("system", "screen_brightness")?.toIntOrNull() ?: 128
                autoRotate = dc.getSetting("system", "accelerometer_rotation") == "1"
                animations = dc.getSetting("global", "window_animation_scale") != "0.0"
                fontScale = dc.getSetting("system", "font_scale")?.toFloatOrNull() ?: 1.0f

                volumeMedia = dc.getStreamVolume(STREAM_MUSIC).coerceIn(0, VOLUME_MAX)
                volumeRing = dc.getStreamVolume(STREAM_RING).coerceIn(0, VOLUME_MAX)
                volumeAlarm = dc.getStreamVolume(STREAM_ALARM).coerceIn(0, VOLUME_MAX)
            } catch (e: Exception) {
                Timber.w(e, "DeviceControl: failed to read initial state")
            }
        }
    }

    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            title = { Text(stringResource(R.string.device_control_reboot_title)) },
            text = { Text(stringResource(R.string.device_control_reboot_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showRebootDialog = false
                    scope.launch(Dispatchers.IO) {
                        try { ShizukuPlusAPI.DeviceControl.reboot(null) }
                        catch (e: Exception) { Timber.e(e, "reboot failed") }
                    }
                }) { Text(stringResource(R.string.device_control_reboot)) }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showShutdownDialog) {
        AlertDialog(
            onDismissRequest = { showShutdownDialog = false },
            title = { Text(stringResource(R.string.device_control_shutdown_title)) },
            text = { Text(stringResource(R.string.device_control_shutdown_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showShutdownDialog = false
                    scope.launch(Dispatchers.IO) {
                        try { ShizukuPlusAPI.DeviceControl.shutdown() }
                        catch (e: Exception) { Timber.e(e, "shutdown failed") }
                    }
                }) { Text(stringResource(R.string.device_control_shutdown)) }
            },
            dismissButton = {
                TextButton(onClick = { showShutdownDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_device_control_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painterResource(R.drawable.ic_back_24),
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── Connectivity ──────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.device_control_section_connectivity)) }

            item {
                ControlToggleRow(
                    label = stringResource(R.string.device_control_airplane_mode),
                    checked = airplane,
                    onCheckedChange = { v ->
                        airplane = v
                        scope.launch(Dispatchers.IO) {
                            runCatching { ShizukuPlusAPI.DeviceControl.setAirplaneModeEnabled(v) }
                        }
                    }
                )
            }
            item {
                ControlToggleRow(
                    label = stringResource(R.string.device_control_wifi),
                    checked = wifi,
                    onCheckedChange = { v ->
                        wifi = v
                        scope.launch(Dispatchers.IO) {
                            runCatching { ShizukuPlusAPI.DeviceControl.setWifiEnabled(v) }
                        }
                    }
                )
            }
            item {
                ControlToggleRow(
                    label = stringResource(R.string.device_control_bluetooth),
                    checked = bluetooth,
                    onCheckedChange = { v ->
                        bluetooth = v
                        scope.launch(Dispatchers.IO) {
                            runCatching { ShizukuPlusAPI.DeviceControl.setBluetoothEnabled(v) }
                        }
                    }
                )
            }
            item {
                ControlToggleRow(
                    label = stringResource(R.string.device_control_mobile_data),
                    checked = mobileData,
                    onCheckedChange = { v ->
                        mobileData = v
                        scope.launch(Dispatchers.IO) {
                            runCatching { ShizukuPlusAPI.DeviceControl.setMobileDataEnabled(v) }
                        }
                    }
                )
            }
            item {
                ControlToggleRow(
                    label = stringResource(R.string.device_control_nfc),
                    checked = nfc,
                    onCheckedChange = { v ->
                        nfc = v
                        scope.launch(Dispatchers.IO) {
                            runCatching { ShizukuPlusAPI.DeviceControl.setNfcEnabled(v) }
                        }
                    }
                )
            }

            // ── Display ───────────────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionHeader(stringResource(R.string.device_control_section_display)) }

            item {
                ControlToggleRow(
                    label = stringResource(R.string.device_control_auto_brightness),
                    checked = autoBrightness,
                    onCheckedChange = { v ->
                        autoBrightness = v
                        scope.launch(Dispatchers.IO) {
                            runCatching { ShizukuPlusAPI.DeviceControl.setAutoBrightnessEnabled(v) }
                        }
                    }
                )
            }

            if (!autoBrightness) {
                item {
                    ControlSliderRow(
                        label = stringResource(R.string.device_control_brightness, brightness),
                        value = brightness.toFloat(),
                        valueRange = 0f..255f,
                        onValueChangeFinished = { v ->
                            brightness = v.toInt()
                            scope.launch(Dispatchers.IO) {
                                runCatching { ShizukuPlusAPI.DeviceControl.setScreenBrightness(v.toInt()) }
                            }
                        }
                    )
                }
            }

            item {
                ControlToggleRow(
                    label = stringResource(R.string.device_control_auto_rotate),
                    checked = autoRotate,
                    onCheckedChange = { v ->
                        autoRotate = v
                        scope.launch(Dispatchers.IO) {
                            runCatching { ShizukuPlusAPI.DeviceControl.setAutoRotateEnabled(v) }
                        }
                    }
                )
            }

            // ── Audio ──────────────────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionHeader(stringResource(R.string.device_control_section_audio)) }

            item {
                ControlSliderRow(
                    label = stringResource(R.string.device_control_volume_media, volumeMedia),
                    value = volumeMedia.toFloat(),
                    valueRange = 0f..VOLUME_MAX.toFloat(),
                    steps = VOLUME_MAX - 1,
                    onValueChangeFinished = { v ->
                        volumeMedia = v.toInt()
                        scope.launch(Dispatchers.IO) {
                            runCatching { ShizukuPlusAPI.DeviceControl.setStreamVolume(STREAM_MUSIC, v.toInt()) }
                        }
                    }
                )
            }
            item {
                ControlSliderRow(
                    label = stringResource(R.string.device_control_volume_ring, volumeRing),
                    value = volumeRing.toFloat(),
                    valueRange = 0f..VOLUME_MAX.toFloat(),
                    steps = VOLUME_MAX - 1,
                    onValueChangeFinished = { v ->
                        volumeRing = v.toInt()
                        scope.launch(Dispatchers.IO) {
                            runCatching { ShizukuPlusAPI.DeviceControl.setStreamVolume(STREAM_RING, v.toInt()) }
                        }
                    }
                )
            }
            item {
                ControlSliderRow(
                    label = stringResource(R.string.device_control_volume_alarm, volumeAlarm),
                    value = volumeAlarm.toFloat(),
                    valueRange = 0f..VOLUME_MAX.toFloat(),
                    steps = VOLUME_MAX - 1,
                    onValueChangeFinished = { v ->
                        volumeAlarm = v.toInt()
                        scope.launch(Dispatchers.IO) {
                            runCatching { ShizukuPlusAPI.DeviceControl.setStreamVolume(STREAM_ALARM, v.toInt()) }
                        }
                    }
                )
            }

            // ── System Appearance ─────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionHeader(stringResource(R.string.device_control_section_system)) }

            item {
                ControlToggleRow(
                    label = stringResource(R.string.device_control_animations),
                    checked = animations,
                    onCheckedChange = { v ->
                        animations = v
                        scope.launch(Dispatchers.IO) {
                            runCatching { ShizukuPlusAPI.DeviceControl.setAnimationsEnabled(v) }
                        }
                    }
                )
            }

            item {
                ControlSliderRow(
                    label = stringResource(R.string.device_control_font_scale, "%.2f".format(fontScale)),
                    value = fontScale,
                    valueRange = 0.70f..2.00f,
                    onValueChangeFinished = { v ->
                        fontScale = v
                        scope.launch(Dispatchers.IO) {
                            runCatching { ShizukuPlusAPI.DeviceControl.setFontScale(v) }
                        }
                    }
                )
            }

            // ── Power ─────────────────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }
            item { SectionHeader(stringResource(R.string.device_control_section_power)) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showRebootDialog = true }
                    ) {
                        Text(stringResource(R.string.device_control_reboot))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showShutdownDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.device_control_shutdown))
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ControlToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ControlSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChangeFinished: (Float) -> Unit
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChangeFinished(sliderValue) },
            valueRange = valueRange,
            steps = steps
        )
    }
}
