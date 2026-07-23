package org.openbabyphone

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.openbabyphone.ui.theme.Spacing
import org.openbabyphone.viewmodel.DiscoverUiState
import org.openbabyphone.viewmodel.DiscoverViewModel
import org.openbabyphone.viewmodel.DiscoveredDevice
import org.openbabyphone.viewmodel.KnownChildStatus
import org.openbabyphone.viewmodel.KnownConnectionResult
import org.openbabyphone.viewmodel.ListenRequest
import org.openbabyphone.viewmodel.PairingFlowState
import org.openbabyphone.viewmodel.QrScanResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onNavigateBack: () -> Unit,
    onNavigateToListen: (requestId: String, childId: String, pairingId: String) -> Unit,
    onConnectionHelp: (requestId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = viewModel(),
    autoStartDiscovery: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()
    val scanPrompt = stringResource(R.string.scan_qr_code_prompt)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showCodePairing by remember { mutableStateOf(false) }
    var fallbackCode by remember { mutableStateOf("") }

    DisposableEffect(autoStartDiscovery) {
        if (autoStartDiscovery) viewModel.activate() else viewModel.refreshTrustedChildren()
        onDispose {
            if (autoStartDiscovery) viewModel.stopDiscovery()
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { contents ->
            when (val scan = viewModel.handleQrScan(contents)) {
                QrScanResult.Invalid -> Unit
                is QrScanResult.Structured -> Unit
                is QrScanResult.Legacy -> {
                    fallbackCode = scan.pairingCode
                    showCodePairing = true
                }
            }
        }
    }
    val launchScan = {
        viewModel.cancelPairingSearch()
        scanLauncher.launch(
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt(scanPrompt)
                setBeepEnabled(false)
                setCaptureActivity(PortraitCaptureActivity::class.java)
                setOrientationLocked(true)
            }
        )
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
            ParentHomeContent(
                uiState = uiState,
                onScanQr = launchScan,
                onCannotScan = {
                    fallbackCode = ""
                    showCodePairing = true
                },
                onKnownChildAction = { childId, status ->
                    if (status == KnownChildStatus.PairAgain) {
                        launchScan()
                    } else {
                        when (val result = viewModel.prepareKnownConnection(childId)) {
                            is KnownConnectionResult.Ready -> onNavigateToListen(
                                result.request.requestId,
                                result.request.childId,
                                result.request.pairingId
                            )
                            KnownConnectionResult.PairAgain -> launchScan()
                            KnownConnectionResult.NotFound -> Unit
                        }
                    }
                },
                onRetry = viewModel::retryPairingSearch,
                onStartListening = {
                    onNavigateToListen(it.requestId, it.childId, it.pairingId)
                },
                 onConnectionHelp = onConnectionHelp,
                modifier = modifier
            )
        }
    }

    if (showCodePairing) {
        CodePairingDialog(
            devices = uiState.fallbackDevices,
            initialCode = fallbackCode,
            onDismiss = {
                fallbackCode = ""
                showCodePairing = false
            },
            onPair = { device, code ->
                if (viewModel.prepareCodePairing(device, code)) {
                    fallbackCode = ""
                    showCodePairing = false
                    true
                } else {
                    false
                }
            },
            onConnectionHelp = {
                fallbackCode = ""
                showCodePairing = false
                onConnectionHelp(null)
            }
        )
    }
}

@Composable
internal fun ParentHomeContent(
    uiState: DiscoverUiState,
    onScanQr: () -> Unit,
    onCannotScan: () -> Unit,
    onKnownChildAction: (String, KnownChildStatus) -> Unit,
    onRetry: () -> Unit,
    onStartListening: (ListenRequest) -> Unit,
    onConnectionHelp: (requestId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
            .padding(Spacing.space16)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val flow = uiState.pairingFlow) {
            PairingFlowState.Idle -> ParentHomeIdle(
                uiState = uiState,
                onScanQr = onScanQr,
                onCannotScan = onCannotScan,
                onKnownChildAction = onKnownChildAction,
                onConnectionHelp = { onConnectionHelp(null) }
            )
            PairingFlowState.InvalidQr -> FocusedMessage(
                title = stringResource(R.string.invalid_child_qr_title),
                body = stringResource(R.string.invalid_qr_code_feedback),
                live = true
            ) {
                OdPrimaryButton(
                    text = stringResource(R.string.scan_again),
                    onClick = onScanQr,
                    modifier = Modifier.testTag("scan_again_button")
                )
                OdTextButton(stringResource(R.string.cannot_scan_code), onCannotScan)
                 OdTextButton(
                     text = stringResource(R.string.connection_help),
                     onClick = { onConnectionHelp(null) }
                 )
            }
            is PairingFlowState.LookingForChild -> FocusedMessage(
                title = stringResource(R.string.looking_for_child, flow.childName),
                body = stringResource(R.string.looking_for_child_guidance),
                live = true
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("pairing_progress"))
            }
            is PairingFlowState.ChildNotFound -> FocusedMessage(
                title = stringResource(R.string.child_not_found),
                body = stringResource(R.string.child_not_found_guidance, flow.childName),
                live = true
            ) {
                OdPrimaryButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    modifier = Modifier
                        .testTag("retry_pairing_button")
                        .semantics { traversalIndex = 1f }
                )
                OdOutlinedActionButton(
                    text = stringResource(R.string.scan_again),
                    onClick = onScanQr,
                    modifier = Modifier
                        .testTag("scan_again_button")
                        .semantics { traversalIndex = 2f }
                )
                 OdTextButton(
                     text = stringResource(R.string.connection_help),
                     onClick = { onConnectionHelp(flow.requestId) }
                 )
            }
            is PairingFlowState.Ready -> FocusedMessage(
                title = stringResource(R.string.ready_to_listen),
                body = flow.childName,
                live = true
            ) {
                Text(
                    text = stringResource(R.string.move_parent_away_before_listening),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                OdPrimaryButton(
                    text = stringResource(R.string.start_listening),
                    onClick = { onStartListening(flow.request) },
                    modifier = Modifier
                        .testTag("start_listening_button")
                        .semantics { traversalIndex = 1f }
                )
            }
        }
    }
}

@Composable
private fun ParentHomeIdle(
    uiState: DiscoverUiState,
    onScanQr: () -> Unit,
    onCannotScan: () -> Unit,
    onKnownChildAction: (String, KnownChildStatus) -> Unit,
    onConnectionHelp: () -> Unit
) {
    if (uiState.knownChildren.isEmpty()) {
        OdSectionHeader(
            title = stringResource(R.string.connect_child_phone),
            helper = stringResource(R.string.scan_child_qr_guidance)
        )
        Spacer(modifier = Modifier.height(Spacing.space16))
        OdPrimaryButton(
            text = stringResource(R.string.scan_child_qr_code),
            onClick = onScanQr,
            modifier = Modifier.testTag("scan_qr_button")
        )
        OdTextButton(
            text = stringResource(R.string.cannot_scan_code),
            onClick = onCannotScan,
            modifier = Modifier.testTag("cannot_scan_button")
        )
        OdTextButton(
            text = stringResource(R.string.connection_help),
            onClick = onConnectionHelp,
            modifier = Modifier.testTag("parent_connection_help")
        )
        return
    }

    OdSectionHeader(
        title = stringResource(R.string.known_children_title),
        helper = stringResource(R.string.known_children_helper)
    )
    Spacer(modifier = Modifier.height(Spacing.space12))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("known_children_list"),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8)
    ) {
        uiState.knownChildren.forEachIndexed { index, row ->
            OdOutlinedCard(modifier = Modifier.testTag("known_child_row_$index")) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.child.displayName.ifBlank {
                                stringResource(R.string.default_child_name)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .testTag("known_child_name_$index")
                                .semantics { traversalIndex = index.toFloat() }
                        )
                        Text(
                            text = when (row.status) {
                                KnownChildStatus.Available -> stringResource(R.string.child_available)
                                KnownChildStatus.NotFound -> stringResource(R.string.child_not_found_status)
                                KnownChildStatus.PairAgain -> stringResource(R.string.pair_again)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (row.status == KnownChildStatus.Available) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    OutlinedButton(
                        onClick = { onKnownChildAction(row.child.childId, row.status) },
                        enabled = row.status != KnownChildStatus.NotFound,
                        modifier = Modifier.testTag("known_child_action_$index")
                    ) {
                        Text(
                            if (row.status == KnownChildStatus.PairAgain) {
                                stringResource(R.string.pair_again)
                            } else {
                                stringResource(R.string.listen_action)
                            }
                        )
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(Spacing.space16))
    OdPrimaryButton(
        text = stringResource(R.string.scan_child_qr_code),
        onClick = onScanQr,
        modifier = Modifier.testTag("scan_qr_button")
    )
    OdTextButton(
        text = stringResource(R.string.cannot_scan_code),
        onClick = onCannotScan,
        modifier = Modifier.testTag("cannot_scan_button")
    )
    OdTextButton(
        text = stringResource(R.string.connection_help),
        onClick = onConnectionHelp,
        modifier = Modifier.testTag("parent_connection_help")
    )
}

@Composable
private fun FocusedMessage(
    title: String,
    body: String,
    live: Boolean,
    actions: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("parent_state_hero")
            .then(
                if (live) Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = title
                    traversalIndex = 0f
                }
                else Modifier
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space16)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        actions()
    }
}

@Composable
private fun CodePairingDialog(
    devices: List<DiscoveredDevice>,
    initialCode: String,
    onDismiss: () -> Unit,
    onPair: (DiscoveredDevice, String) -> Boolean,
    onConnectionHelp: () -> Unit
) {
    var selectedIndex by remember(devices) { mutableStateOf<Int?>(null) }
    var pairingCode by remember(initialCode) { mutableStateOf(initialCode) }
    var showInvalidCode by remember { mutableStateOf(false) }
    val validCode = PairingCode.isValid(pairingCode)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.code_pairing_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                Text(stringResource(R.string.code_pairing_guidance))
                if (devices.isEmpty()) {
                    Text(
                        stringResource(R.string.no_nearby_children),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    devices.forEachIndexed { index, device ->
                        OutlinedButton(
                            onClick = { selectedIndex = index },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("fallback_child_$index")
                        ) {
                            Text(
                                if (selectedIndex == index) {
                                    stringResource(R.string.selected_child, device.visibleName)
                                } else {
                                    device.visibleName
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = {
                        pairingCode = it.take(PairingCode.MAX_LENGTH)
                        showInvalidCode = false
                    },
                    label = { Text(stringResource(R.string.pairing_code_title)) },
                    singleLine = true,
                    isError = showInvalidCode,
                    supportingText = {
                        if (showInvalidCode) Text(stringResource(R.string.invalid_pairing_code_feedback))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fallback_code_field")
                )
                TextButton(onClick = onConnectionHelp) {
                    Text(stringResource(R.string.connection_help))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = selectedIndex?.let(devices::getOrNull)
                    showInvalidCode = !validCode
                    if (selected != null && validCode) onPair(selected, pairingCode)
                },
                enabled = selectedIndex != null && validCode,
                modifier = Modifier.testTag("pair_with_code_button")
            ) {
                Text(stringResource(R.string.continue_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
