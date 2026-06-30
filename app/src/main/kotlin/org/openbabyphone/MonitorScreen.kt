/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open Babyphone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open Babyphone. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openbabyphone

import org.openbabyphone.ui.theme.Spacing
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openbabyphone.service.ServiceConnectionManager
import org.openbabyphone.ui.theme.Motion
import org.openbabyphone.viewmodel.MonitorViewModel
import org.openbabyphone.viewmodel.MonitorUiState
import org.openbabyphone.WifiDirectState
import org.openbabyphone.WifiDirectPermissions
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonitorViewModel = viewModel(),
    bindMonitorService: (Context) -> ServiceConnectionManager.ServiceBinding = ServiceConnectionManager::bindMonitorService,
    unbindAndStopService: (Context, ServiceConnectionManager.ServiceBinding) -> Unit = ServiceConnectionManager::unbindAndStopService
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var isMonitoring by rememberSaveable { mutableStateOf(false) }
    val serviceInformationDescription = stringResource(R.string.service_information_content_description)
    val serviceStatusDescription = stringResource(R.string.service_status_content_description, uiState.status)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var wifiDirectPermissionDenied by remember { mutableStateOf(false) }
    val wifiDirectPermission = remember { WifiDirectPermissions.requiredPermission() }
    val wifiDirectPermissions = remember { arrayOf(wifiDirectPermission) }

    val wifiDirectPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = wifiDirectPermissions.all { perm ->
            results[perm] == true || ContextCompat.checkSelfPermission(
                context, perm
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            wifiDirectPermissionDenied = false
            viewModel.startWifiDirect()
        } else {
            wifiDirectPermissionDenied = true
        }
    }

    val onStartWifiDirect: () -> Unit = {
        wifiDirectPermissionDenied = false
        val allGranted = wifiDirectPermissions.all { perm ->
            ContextCompat.checkSelfPermission(
                context, perm
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            viewModel.startWifiDirect()
        } else {
            wifiDirectPermissionLauncher.launch(wifiDirectPermissions)
        }
    }

    var showResetPairingDialog by remember { mutableStateOf(false) }

    DisposableEffect(isMonitoring) {
        val binding = if (isMonitoring) bindMonitorService(context) else null
        onDispose {
            binding?.let { unbindAndStopService(context, it) }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppLargeTopAppBar(
                title = stringResource(R.string.child_device),
                onNavigateBack = onNavigateBack,
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(Spacing.space16)
                    .verticalScroll(rememberScrollState())
            ) {
                if (!isMonitoring) {
                    SetupSection(
                        uiState = uiState,
                        isMonitoring = isMonitoring,
                        onPairingCodeChange = { viewModel.updatePairingCode(it) },
                        onDeviceNameChange = { viewModel.updateDeviceName(it) },
                        onStartMonitoring = { isMonitoring = true },
                        onResetPairing = { showResetPairingDialog = true }
                    )
                } else {
                    MonitoringSection(
                        uiState = uiState,
                        onStopMonitoring = {
                            viewModel.stopWifiDirect()
                            isMonitoring = false
                        },
                        onStartWifiDirect = onStartWifiDirect,
                        onStopWifiDirect = { viewModel.stopWifiDirect() },
                        wifiDirectPermissionDenied = wifiDirectPermissionDenied,
                        serviceInformationDescription = serviceInformationDescription,
                        serviceStatusDescription = serviceStatusDescription
                    )
                }
            }
        }
    }

    if (showResetPairingDialog) {
        AlertDialog(
            onDismissRequest = { showResetPairingDialog = false },
            title = { Text(stringResource(R.string.reset_pairing)) },
            text = { Text(stringResource(R.string.reset_pairing_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetPairing()
                    showResetPairingDialog = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPairingDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupSection(
    uiState: MonitorUiState,
    isMonitoring: Boolean,
    onPairingCodeChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onStartMonitoring: () -> Unit,
    onResetPairing: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_name_card"),
        content = {
            Column(modifier = Modifier.padding(Spacing.space16)) {
                Text(stringResource(R.string.device_name_title), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(Spacing.space8))
                OutlinedTextField(
                    value = uiState.deviceName,
                    onValueChange = onDeviceNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("device_name_field"),
                    enabled = !isMonitoring,
                    placeholder = { Text(stringResource(R.string.device_name_placeholder)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(Spacing.space8))
                Text(
                    stringResource(R.string.device_name_description),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    )

    Spacer(modifier = Modifier.height(Spacing.space16))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pairing_card"),
        content = {
            Column(
                modifier = Modifier.padding(Spacing.space16),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.pairing_code_title), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(Spacing.space8))
                OutlinedTextField(
                    value = uiState.pairingCode,
                    onValueChange = onPairingCodeChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_code_field"),
                    enabled = !isMonitoring,
                    isError = !uiState.pairingCodeValid,
                    supportingText = {
                        if (!uiState.pairingCodeValid) {
                            Text(stringResource(R.string.invalid_pairing_code_feedback))
                        }
                    },
                    placeholder = { Text(stringResource(R.string.example_pairing_code)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(Spacing.space8))
                Text(
                    stringResource(R.string.monitoring_setup_hint),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (uiState.pairingCode.isNotEmpty() && uiState.pairingCodeValid) {
                    Spacer(modifier = Modifier.height(Spacing.space16))
                    QrCode(
                        content = uiState.qrPayload.ifEmpty { uiState.pairingCode },
                        modifier = Modifier.testTag("pairing_qr_code")
                    )
                    Spacer(modifier = Modifier.height(Spacing.space8))
                    Text(
                        stringResource(R.string.qr_code_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Spacing.space8))
                    OutlinedButton(
                        onClick = onResetPairing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_pairing_button"),
                        enabled = !isMonitoring
                    ) {
                        Text(stringResource(R.string.reset_pairing))
                    }
                }
            }
        }
    )

    Spacer(modifier = Modifier.height(Spacing.space16))

    Button(
        onClick = onStartMonitoring,
        enabled = uiState.pairingCodeValid,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monitoring_toggle_button")
    ) {
        Text(stringResource(R.string.start_monitoring))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitoringSection(
    uiState: MonitorUiState,
    onStopMonitoring: () -> Unit,
    onStartWifiDirect: () -> Unit,
    onStopWifiDirect: () -> Unit,
    wifiDirectPermissionDenied: Boolean,
    serviceInformationDescription: String,
    serviceStatusDescription: String
) {
    var showPairingDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(Motion.DurationMedium)) +
            slideInVertically(animationSpec = tween(Motion.DurationMedium)) { it / 8 },
        exit = fadeOut(animationSpec = tween(Motion.DurationShort)) +
            slideOutVertically(animationSpec = tween(Motion.DurationShort)) { -it / 8 }
    ) {
        Column {
            if (uiState.isLoading) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("loading_card"),
                    content = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.space24),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(Spacing.space8))
                                Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                )
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("service_info_card")
                        .semantics { contentDescription = serviceInformationDescription },
                    content = {
                        Column(modifier = Modifier.padding(Spacing.space16)) {
                            Text(stringResource(R.string.service_title), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(Spacing.space8))
                            Text(uiState.serviceName, style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(Spacing.space8))
                            Text(stringResource(R.string.service_description), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(Spacing.space16))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connection_info_card"),
                    content = {
                        Column(modifier = Modifier.padding(Spacing.space16)) {
                            Text(stringResource(R.string.port_title), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(Spacing.space8))
                            Text(uiState.port.toString(), style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(Spacing.space16))
                            Text(stringResource(R.string.address_title), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(Spacing.space8))
                            if (uiState.addresses.isEmpty()) {
                                Text(
                                    stringResource(R.string.not_connected),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                uiState.addresses.forEach { address ->
                                    Text(address, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Spacing.space2))
                                }
                            }
                            Spacer(modifier = Modifier.height(Spacing.space16))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.connected_clients,
                                        uiState.connectedClients,
                                        uiState.connectedClients
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.testTag("client_count_text")
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.space8))
                            Text(
                                uiState.status,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .testTag("status_text")
                                    .semantics { contentDescription = serviceStatusDescription }
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.space16))

            OutlinedButton(
                onClick = { showPairingDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_parent_device_button")
            ) {
                Text(stringResource(R.string.add_parent_device))
            }

            Spacer(modifier = Modifier.height(Spacing.space16))

            WifiDirectCard(
                uiState = uiState,
                onStart = onStartWifiDirect,
                onStop = onStopWifiDirect,
                permissionDenied = wifiDirectPermissionDenied
            )

            Spacer(modifier = Modifier.height(Spacing.space16))

            Button(
                onClick = onStopMonitoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monitoring_toggle_button")
            ) {
                Text(stringResource(R.string.stop_monitoring))
            }
        }
    }

    if (showPairingDialog) {
        AlertDialog(
            onDismissRequest = { showPairingDialog = false },
            title = { Text(stringResource(R.string.pair_parent_device_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.qrPayload.isNotEmpty()) {
                        QrCode(
                            content = uiState.qrPayload,
                            modifier = Modifier.testTag("pairing_dialog_qr_code")
                        )
                        Spacer(modifier = Modifier.height(Spacing.space8))
                    }
                    Text(
                        text = uiState.pairingCode,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("pairing_dialog_pairing_code")
                    )
                    Spacer(modifier = Modifier.height(Spacing.space8))
                    Text(
                        stringResource(R.string.pair_parent_device_instructions),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPairingDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun WifiDirectCard(
    uiState: MonitorUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    permissionDenied: Boolean
) {
    if (!uiState.wifiDirectSupported) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wifi_direct_card"),
        content = {
            Column(modifier = Modifier.padding(Spacing.space16)) {
                Text(
                    stringResource(R.string.wifi_direct_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(Spacing.space8))
                Text(
                    stringResource(R.string.wifi_direct_child_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(Spacing.space12))
                if (permissionDenied) {
                    Text(
                        text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                            stringResource(R.string.wifi_direct_permission_nearby)
                        else stringResource(R.string.wifi_direct_permission_location),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(Spacing.space8))
                }
                when (val s = uiState.wifiDirectState) {
                    WifiDirectState.Idle,
                    is WifiDirectState.Error -> {
                        OutlinedButton(
                            onClick = onStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wifi_direct_start_button")
                        ) {
                            Text(stringResource(R.string.wifi_direct_start))
                        }
                        if (s is WifiDirectState.Error) {
                            Spacer(modifier = Modifier.height(Spacing.space8))
                            Text(
                                text = s.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    WifiDirectState.Starting,
                    WifiDirectState.Advertising -> {
                        OutlinedButton(
                            onClick = onStop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wifi_direct_stop_button")
                        ) {
                            Text(stringResource(R.string.wifi_direct_stop))
                        }
                        Spacer(modifier = Modifier.height(Spacing.space8))
                        Text(
                            if (s is WifiDirectState.Advertising)
                                stringResource(R.string.wifi_direct_advertising)
                            else stringResource(R.string.wifi_direct_starting),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is WifiDirectState.Discovering,
                    is WifiDirectState.Connecting,
                    is WifiDirectState.Connected -> Unit
                }
            }
        }
    )
}
