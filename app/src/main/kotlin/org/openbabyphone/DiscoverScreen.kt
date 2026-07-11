package org.openbabyphone

import org.openbabyphone.ui.theme.Spacing
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.openbabyphone.viewmodel.DeviceTrustStatus
import org.openbabyphone.viewmodel.DiscoveredDevice
import org.openbabyphone.viewmodel.DiscoverViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddressInput: () -> Unit,
    onNavigateToListen: (String, Int, String, String) -> Unit,
    onNavigateToWifiDirect: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = viewModel(),
    autoStartDiscovery: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectLabel = stringResource(R.string.connect)
    val stopLabel = stringResource(R.string.discovery_stopped)
    val invalidQrFeedback = stringResource(R.string.invalid_qr_code_feedback)
    val childAddedLabel = stringResource(R.string.child_added_to_known)
    val feedbackGuidance = stringResource(R.string.move_parent_away_before_listening)
    val scanPrompt = stringResource(R.string.scan_qr_code_prompt)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }
    var scanSuccessMessage by remember { mutableStateOf<String?>(null) }
    var forgetChildId by remember { mutableStateOf<String?>(null) }
    var pendingPairingDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }

    DisposableEffect(autoStartDiscovery) {
        if (autoStartDiscovery) {
            viewModel.startDiscovery()
        }
        onDispose {
            if (autoStartDiscovery) {
                viewModel.stopDiscovery()
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val success = viewModel.handleQrScan(result.contents)
            if (success) {
                scanErrorMessage = null
                val parsed = PairingQrCode.parse(result.contents)
                scanSuccessMessage = if (parsed is PairingQrCode.ParsedQrCode.Structured) {
                    "$childAddedLabel\n\n$feedbackGuidance"
                } else {
                    null
                }
            } else {
                scanErrorMessage = invalidQrFeedback
                scanSuccessMessage = null
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.parent_device),
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
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.trustedChildren.isNotEmpty()) {
                    OdSectionHeader(
                        title = stringResource(R.string.known_children_title),
                        helper = stringResource(R.string.known_children_helper),
                        modifier = Modifier.padding(bottom = Spacing.space8)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("known_children_list"),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space8)
                    ) {
                        uiState.trustedChildren.forEach { child ->
                            val isOnline = uiState.devices.any { it.childId == child.childId }
                            val device = uiState.devices.firstOrNull { it.childId == child.childId }
                            val hasLastKnown = child.lastKnownAddress != null && child.lastKnownPort != null
                            OdOutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("known_child_${child.childId}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.space16),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                child.displayName.ifEmpty { stringResource(R.string.unknown_device) },
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                if (isOnline) child.childId else stringResource(R.string.trusted_child_not_found),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!isOnline && hasLastKnown) {
                                                Text(
                                                    stringResource(R.string.last_known_address, child.lastKnownAddress ?: ""),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        OdSignalBars(strength = if (isOnline) 4 else 0)
                                        Spacer(modifier = Modifier.height(Spacing.space8))
                                        Button(
                                            onClick = {
                                            if (device != null) {
                                                val code = viewModel.pairingCodeFor(device)
                                                onNavigateToListen(
                                                    device.address,
                                                    device.port,
                                                    device.visibleName,
                                                    code
                                                )
                                            } else if (hasLastKnown) {
                                                onNavigateToListen(
                                                    child.lastKnownAddress!!,
                                                    child.lastKnownPort!!,
                                                    child.displayName,
                                                    child.pairingCode
                                                )
                                            }
                                        },
                                        enabled = isOnline || hasLastKnown,
                                        modifier = Modifier.testTag("connect_known_${child.childId}")
                                    ) {
                                        Text(
                                            if (isOnline) stringResource(R.string.connect_to_child)
                                            else stringResource(R.string.try_last_address)
                                        )
                                    }
                                        OutlinedButton(
                                        onClick = { forgetChildId = child.childId },
                                        modifier = Modifier.testTag("forget_${child.childId}")
                                    ) {
                                        Text(stringResource(R.string.forget_child))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.space24))
                }

                OdSectionHeader(
                    title = stringResource(R.string.nearby_child_devices),
                    helper = stringResource(R.string.nearby_child_devices_description),
                    modifier = Modifier.padding(bottom = Spacing.space12)
                )

                OdOutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discovery_status_card")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.space12)
                    ) {
                        if (uiState.isDiscovering) {
                            CircularProgressIndicator()
                            Text(
                                stringResource(R.string.searching_for_child_devices),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                uiState.error ?: stringResource(R.string.discovery_no_devices),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = if (uiState.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OdOutlinedActionButton(
                            text = if (uiState.isDiscovering) stopLabel else stringResource(R.string.refresh_search),
                            onClick = {
                                if (uiState.isDiscovering) viewModel.stopDiscovery() else viewModel.startDiscovery()
                            },
                            modifier = Modifier.testTag("discover_button")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.space12))

                OdOutlinedActionButton(
                    text = stringResource(R.string.scan_qr_code_instead),
                    onClick = {
                        scanErrorMessage = null
                        scanSuccessMessage = null
                        scanLauncher.launch(
                            ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt(scanPrompt)
                                setBeepEnabled(false)
                                setCaptureActivity(PortraitCaptureActivity::class.java)
                                setOrientationLocked(true)
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_qr_button")
                )

                scanErrorMessage?.let { message ->
                    Spacer(modifier = Modifier.height(Spacing.space8))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                scanSuccessMessage?.let { message ->
                    Spacer(modifier = Modifier.height(Spacing.space8))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.space16))

                val selectableDevices = uiState.devices.filter { device ->
                    val key = "${device.address}:${device.port}"
                    val trustStatus = uiState.trustedChildStatuses[key]
                    trustStatus != DeviceTrustStatus.Trusted || uiState.trustedChildren.none { it.childId == device.childId }
                }
                if (selectableDevices.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("device_list"),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space8)
                    ) {
                        selectableDevices.forEach { device ->
                            val key = "${device.address}:${device.port}"
                            val trustStatus = uiState.trustedChildStatuses[key]
                            OdOutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("device_${device.address}")
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(device.visibleName, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                "${device.address}:${device.port}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        OdSignalBars()
                                    }
                                    if (trustStatus == DeviceTrustStatus.PairingReset) {
                                        Text(
                                            stringResource(R.string.pairing_reset_notice),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    OdPrimaryButton(
                                        text = if (trustStatus == DeviceTrustStatus.Trusted) connectLabel else stringResource(R.string.pair_and_connect),
                                        onClick = {
                                            if (trustStatus == DeviceTrustStatus.Trusted) {
                                                val code = viewModel.pairingCodeFor(device)
                                                onNavigateToListen(device.address, device.port, device.visibleName, code)
                                            } else {
                                                pendingPairingDevice = device
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.space32))

                OdOutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("advanced_section")
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space12)
                    ) {
                        OdCardTitle(stringResource(R.string.manual_connection))
                        OdCardBody(stringResource(R.string.advanced_section_description))
                        OdOutlinedActionButton(
                            text = stringResource(R.string.enter_address_title),
                            onClick = onNavigateToAddressInput,
                            modifier = Modifier.testTag("address_input_button")
                        )
                        OdOutlinedActionButton(
                            text = stringResource(R.string.wifi_direct_connection),
                            onClick = onNavigateToWifiDirect,
                            modifier = Modifier.testTag("wifi_direct_button")
                        )
                    }
                }
            }
        }
    }

    forgetChildId?.let { childId ->
        AlertDialog(
            onDismissRequest = { forgetChildId = null },
            title = { Text(stringResource(R.string.forget_child)) },
            text = { Text(stringResource(R.string.forget_child)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.forgetChild(childId)
                    forgetChildId = null
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { forgetChildId = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingPairingDevice?.let { device ->
        val key = "${device.address}:${device.port}"
        PairChildDeviceDialog(
            device = device,
            isPairingReset = uiState.trustedChildStatuses[key] == DeviceTrustStatus.PairingReset,
            onDismiss = { pendingPairingDevice = null },
            onPairAndConnect = { pairingCode ->
                val code = viewModel.trustAndPair(device, pairingCode) ?: return@PairChildDeviceDialog false
                pendingPairingDevice = null
                onNavigateToListen(device.address, device.port, device.visibleName, code)
                true
            }
        )
    }
}

@Composable
private fun PairChildDeviceDialog(
    device: DiscoveredDevice,
    isPairingReset: Boolean,
    onDismiss: () -> Unit,
    onPairAndConnect: (String) -> Boolean
) {
    var pairingCode by remember { mutableStateOf("") }
    var showInvalidCode by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pair_child_device_title, device.visibleName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                Text(
                    if (isPairingReset) stringResource(R.string.pairing_reset_notice)
                    else stringResource(R.string.pair_child_device_instructions),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = {
                        pairingCode = it
                        showInvalidCode = false
                    },
                    label = { Text(stringResource(R.string.pairing_code_title)) },
                    placeholder = { Text(stringResource(R.string.example_pairing_code)) },
                    singleLine = true,
                    isError = showInvalidCode,
                    supportingText = {
                        if (showInvalidCode) {
                            Text(stringResource(R.string.invalid_pairing_code_feedback))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pair_child_code_field")
                )
                Text(
                    stringResource(R.string.move_parent_away_before_listening),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showInvalidCode = !onPairAndConnect(pairingCode)
                },
                modifier = Modifier.testTag("pair_child_connect_button")
            ) {
                Text(stringResource(R.string.connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
