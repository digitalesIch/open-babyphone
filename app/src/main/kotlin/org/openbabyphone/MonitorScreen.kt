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

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.openbabyphone.service.MonitorSessionState
import org.openbabyphone.service.ServiceConnectionManager
import org.openbabyphone.ui.theme.Spacing
import org.openbabyphone.viewmodel.MonitorUiState
import org.openbabyphone.viewmodel.MonitorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    onNavigateBack: () -> Unit,
    onConnectionHelp: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MonitorViewModel = viewModel(),
    bindMonitorService: (Context) -> ServiceConnectionManager.ServiceBinding =
        ServiceConnectionManager::bindMonitorService,
    startMonitorService: (Context) -> Boolean = ServiceConnectionManager::startMonitorService,
    disposeServiceBinding: (Context, ServiceConnectionManager.ServiceBinding) -> Unit =
        ServiceConnectionManager::disposeServiceBinding,
    unbindAndStopService: (Context, ServiceConnectionManager.ServiceBinding) -> Unit =
        ServiceConnectionManager::unbindAndStopService,
    stopMonitorService: (Context) -> Unit = ServiceConnectionManager::stopMonitorService,
    permissionChecker: (Context, String) -> Boolean = { context, permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    },
    permissionRequester: ((String, (Boolean) -> Unit) -> Unit)? = null,
    notificationWarningChecker: (Context) -> Boolean = MonitorNotificationReadiness::shouldWarn,
    openAppSettings: (Context) -> Unit = { context ->
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        )
    },
    openBatteryOptimizationSettings: (Context) -> Unit = BatteryOptimization::openRequest
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val isMonitoring = uiState.isMonitoring
    var serviceBinding by remember { mutableStateOf<ServiceConnectionManager.ServiceBinding?>(null) }
    var showStopMonitoringDialog by rememberSaveable { mutableStateOf(false) }
    var microphonePermissionDenied by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var notificationWarning by rememberSaveable { mutableStateOf(notificationWarningChecker(context)) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val startMonitoringService: () -> Unit = startMonitoringService@{
        microphonePermissionDenied = false
        viewModel.startMonitoring()
        if (!startMonitorService(context)) return@startMonitoringService
        notificationWarning = notificationWarningChecker(context)
    }

    val handleNotificationPermissionResult: (Boolean) -> Unit = { granted ->
        notificationWarning = !granted || notificationWarningChecker(context)
        startMonitoringService()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> handleNotificationPermissionResult(granted) }

    val requestNotificationThenStart: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !permissionChecker(context, Manifest.permission.POST_NOTIFICATIONS) &&
            !notificationPermissionRequested
        ) {
            notificationPermissionRequested = true
            if (permissionRequester != null) {
                permissionRequester(Manifest.permission.POST_NOTIFICATIONS, handleNotificationPermissionResult)
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startMonitoringService()
        }
    }

    val handleMicrophonePermissionResult: (Boolean) -> Unit = { granted ->
        if (granted) {
            requestNotificationThenStart()
        } else {
            microphonePermissionDenied = true
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> handleMicrophonePermissionResult(granted) }

    val requestStartMonitoring: () -> Unit = {
        if (permissionChecker(context, Manifest.permission.RECORD_AUDIO)) {
            requestNotificationThenStart()
        } else if (permissionRequester != null) {
            permissionRequester(Manifest.permission.RECORD_AUDIO, handleMicrophonePermissionResult)
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val requestStop: () -> Unit = {
        showStopMonitoringDialog = true
    }

    BackHandler(enabled = isMonitoring) {
        requestStop()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBatteryOptimizationStatus()
                if (permissionChecker(context, Manifest.permission.RECORD_AUDIO)) {
                    microphonePermissionDenied = false
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && notificationPermissionRequested) {
                    notificationWarning =
                        !permissionChecker(context, Manifest.permission.POST_NOTIFICATIONS) ||
                        notificationWarningChecker(context)
                } else {
                    notificationWarning = notificationWarningChecker(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(isMonitoring) {
        val binding = if (isMonitoring) bindMonitorService(context) else null
        serviceBinding = binding
        onDispose {
            if (binding != null && serviceBinding === binding) {
                disposeServiceBinding(context, binding)
                serviceBinding = null
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.child_device),
                onNavigateBack = {
                    if (isMonitoring) requestStop() else onNavigateBack()
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("monitor_settings_action")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            MonitorContent(
                uiState = uiState,
                notificationWarning = notificationWarning,
                microphonePermissionDenied = microphonePermissionDenied,
                onStartMonitoring = requestStartMonitoring,
                onStopMonitoring = requestStop,
                onConnectionHelp = onConnectionHelp,
                onOpenWifiSettings = {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                },
                onOpenAppSettings = { openAppSettings(context) },
                onOpenBatteryOptimizationSettings = { openBatteryOptimizationSettings(context) },
                modifier = modifier
            )
        }
    }

    if (showStopMonitoringDialog) {
        AlertDialog(
            onDismissRequest = { showStopMonitoringDialog = false },
            title = { Text(stringResource(R.string.stop_monitoring)) },
            text = { Text(stringResource(R.string.stop_monitoring_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val binding = serviceBinding
                        serviceBinding = null
                        if (binding != null) {
                            unbindAndStopService(context, binding)
                        } else {
                            stopMonitorService(context)
                        }
                        viewModel.stopMonitoring()
                        showStopMonitoringDialog = false
                        onNavigateBack()
                    },
                    modifier = Modifier.testTag("confirm_stop_monitoring")
                ) {
                    Text(stringResource(R.string.stop))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopMonitoringDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
internal fun MonitorContent(
    uiState: MonitorUiState,
    notificationWarning: Boolean,
    microphonePermissionDenied: Boolean,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onConnectionHelp: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
            .padding(Spacing.space16)
            .verticalScroll(rememberScrollState())
    ) {
        if (uiState.isMonitoring) {
            MonitoringSection(
                uiState = uiState,
                notificationWarning = notificationWarning,
                onStopMonitoring = onStopMonitoring,
                onConnectionHelp = onConnectionHelp,
                onOpenWifiSettings = onOpenWifiSettings,
                onOpenAppSettings = onOpenAppSettings,
                onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings
            )
        } else {
            SetupSection(
                microphonePermissionDenied = microphonePermissionDenied,
                terminalErrorReason = uiState.terminalErrorReason,
                onStartMonitoring = onStartMonitoring,
                onOpenAppSettings = onOpenAppSettings
            )
        }
    }
}

@Composable
private fun SetupSection(
    microphonePermissionDenied: Boolean,
    terminalErrorReason: String?,
    onStartMonitoring: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.monitoring_setup_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                start = Spacing.space16,
                end = Spacing.space16,
                top = Spacing.space24,
                bottom = Spacing.space24
            )
        )
        OdPrimaryButton(
            text = stringResource(R.string.start_monitoring),
            onClick = onStartMonitoring,
            modifier = Modifier
                .testTag("start_monitoring_button")
                .semantics { traversalIndex = 1f }
        )
        if (terminalErrorReason != null) {
            Spacer(modifier = Modifier.height(Spacing.space16))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("monitor_terminal_recovery")
            ) {
                Column(modifier = Modifier.padding(Spacing.space16)) {
                    Text(
                        text = stringResource(R.string.monitoring_problem),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(Spacing.space4))
                    Text(text = terminalErrorReason, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (microphonePermissionDenied) {
            Spacer(modifier = Modifier.height(Spacing.space16))
            PermissionRecovery(
                onRetry = onStartMonitoring,
                onOpenAppSettings = onOpenAppSettings
            )
        }
    }
}

@Composable
private fun PermissionRecovery(
    onRetry: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("microphone_permission_recovery")
    ) {
        Column(modifier = Modifier.padding(Spacing.space16)) {
            Text(
                text = stringResource(R.string.microphone_permission_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(Spacing.space4))
            Text(
                text = stringResource(R.string.microphone_permission_description),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(Spacing.space12))
            OdPrimaryButton(
                text = stringResource(R.string.retry),
                onClick = onRetry,
                modifier = Modifier.testTag("retry_microphone_permission")
            )
            OdTextButton(
                text = stringResource(R.string.open_app_settings),
                onClick = onOpenAppSettings,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("open_permission_settings")
            )
        }
    }
}

@Composable
private fun MonitoringSection(
    uiState: MonitorUiState,
    notificationWarning: Boolean,
    onStopMonitoring: () -> Unit,
    onConnectionHelp: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit
) {
    val listeningText = parentListeningText(uiState.connectedClients)
    var showPairingDialog by rememberSaveable { mutableStateOf(false) }
    var pairingDialogAutoOpenHandled by rememberSaveable { mutableStateOf(false) }
    var pairingDialogOpenedAutomatically by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(
        uiState.sessionState,
        uiState.pairingCodeValid,
        uiState.qrPayload,
        uiState.connectedClients
    ) {
        if (PairingDialogPolicy.shouldDismissAutoOpened(
                uiState.connectedClients,
                pairingDialogOpenedAutomatically
            )
        ) {
            showPairingDialog = false
            pairingDialogOpenedAutomatically = false
        } else if (PairingDialogPolicy.shouldAutoOpen(
                uiState.sessionState,
                uiState.pairingCodeValid && uiState.qrPayload.isNotEmpty(),
                pairingDialogAutoOpenHandled
            )
        ) {
            pairingDialogAutoOpenHandled = true
            pairingDialogOpenedAutomatically = true
            showPairingDialog = true
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monitoring_hero")
            .semantics {
                contentDescription = uiState.deviceName
                stateDescription = uiState.status
                traversalIndex = 0f
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.space24),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.monitoring),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(Spacing.space8))
            Text(
                text = listeningText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .testTag("parent_count_text")
                    .semantics {
                        stateDescription = listeningText
                        traversalIndex = 1f
                    }
            )
        }
    }

    Spacer(modifier = Modifier.height(Spacing.space16))

    if (uiState.connectedClients == 0) {
        OdPrimaryButton(
            text = stringResource(R.string.pair_a_parent),
            onClick = {
                pairingDialogAutoOpenHandled = true
                pairingDialogOpenedAutomatically = false
                showPairingDialog = true
            },
            modifier = Modifier
                .testTag("pair_parent_button")
                .semantics { traversalIndex = 1f }
        )
    } else {
        OdOutlinedActionButton(
            text = stringResource(R.string.pair_another_parent),
            onClick = {
                pairingDialogAutoOpenHandled = true
                pairingDialogOpenedAutomatically = false
                showPairingDialog = true
            },
            modifier = Modifier
                .testTag("pair_parent_button")
                .semantics { traversalIndex = 1f }
        )
    }

    prioritizedIssue(uiState, notificationWarning)?.let { issue ->
        Spacer(modifier = Modifier.height(Spacing.space16))
        IssueBanner(
            issue = issue,
            onStopMonitoring = onStopMonitoring,
            onOpenWifiSettings = onOpenWifiSettings,
            onOpenAppSettings = onOpenAppSettings,
            onOpenBatteryOptimizationSettings = onOpenBatteryOptimizationSettings
        )
    }

    Spacer(modifier = Modifier.height(Spacing.space16))

    OdTextButton(
        text = stringResource(R.string.connection_help),
        onClick = onConnectionHelp,
        modifier = Modifier
            .testTag("monitor_connection_help")
            .semantics { traversalIndex = 3f }
    )

    OdSurfaceButton(
        text = stringResource(R.string.stop_monitoring),
        onClick = onStopMonitoring,
        modifier = Modifier
            .testTag("stop_monitoring_button")
            .semantics { traversalIndex = 4f }
    )

    if (showPairingDialog) {
        PairParentDeviceDialog(
            uiState = uiState,
            onDismiss = {
                showPairingDialog = false
                pairingDialogOpenedAutomatically = false
            }
        )
    }
}

@Composable
private fun parentListeningText(parentCount: Int): String {
    return if (parentCount == 0) {
        stringResource(R.string.no_parent_listening)
    } else {
        pluralStringResource(R.plurals.parents_listening, parentCount, parentCount)
    }
}

private sealed interface MonitorIssue {
    data class ServiceError(val reason: String) : MonitorIssue
    data object NoNetwork : MonitorIssue
    data object NotificationsDisabled : MonitorIssue
    data object BatteryRestricted : MonitorIssue
}

private fun prioritizedIssue(
    uiState: MonitorUiState,
    notificationWarning: Boolean
): MonitorIssue? = when {
    uiState.sessionState is MonitorSessionState.Error ->
        MonitorIssue.ServiceError(uiState.sessionState.reason)
    uiState.sessionState is MonitorSessionState.NoNetwork -> MonitorIssue.NoNetwork
    notificationWarning -> MonitorIssue.NotificationsDisabled
    !uiState.batteryOptimizationIgnored -> MonitorIssue.BatteryRestricted
    else -> null
}

@Composable
private fun IssueBanner(
    issue: MonitorIssue,
    onStopMonitoring: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit
) {
    val title: String
    val body: String
    val action: String
    val onAction: () -> Unit
    when (issue) {
        is MonitorIssue.ServiceError -> {
            title = stringResource(R.string.monitoring_problem)
            body = issue.reason
            action = stringResource(R.string.stop_monitoring)
            onAction = onStopMonitoring
        }
        MonitorIssue.NoNetwork -> {
            title = stringResource(R.string.no_wifi_title)
            body = stringResource(R.string.no_wifi_body_child)
            action = stringResource(R.string.open_wifi_settings)
            onAction = onOpenWifiSettings
        }
        MonitorIssue.NotificationsDisabled -> {
            title = stringResource(R.string.notifications_disabled_title)
            body = stringResource(R.string.notifications_disabled_monitoring_description)
            action = stringResource(R.string.open_app_settings)
            onAction = onOpenAppSettings
        }
        MonitorIssue.BatteryRestricted -> {
            title = stringResource(R.string.battery_optimization_title)
            body = stringResource(R.string.battery_optimization_description)
            action = stringResource(R.string.battery_optimization_action)
            onAction = onOpenBatteryOptimizationSettings
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monitor_issue_banner")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = title
                traversalIndex = 2f
            }
    ) {
        Column(modifier = Modifier.padding(Spacing.space16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(Spacing.space12))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(Spacing.space8))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(Spacing.space12))
            OdOutlinedActionButton(text = action, onClick = onAction)
        }
    }
}

@Composable
private fun PairParentDeviceDialog(
    uiState: MonitorUiState,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val pairingCodeDescription = stringResource(R.string.pairing_code_readout, uiState.pairingCode)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pair_parent_device_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.pair_parent_device_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Spacing.space12))
                if (uiState.qrPayload.isNotEmpty()) {
                    QrCode(
                        content = uiState.qrPayload,
                        modifier = Modifier.testTag("pairing_dialog_qr_code")
                    )
                    Spacer(modifier = Modifier.height(Spacing.space12))
                }
                Text(
                    text = stringResource(R.string.pairing_fallback_code),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.space4))
                OdMonoText(
                    text = uiState.pairingCode,
                    modifier = Modifier
                        .testTag("pairing_dialog_pairing_code")
                        .semantics { contentDescription = pairingCodeDescription }
                )
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("Pairing code", uiState.pairingCode))
                            )
                        }
                    },
                    modifier = Modifier.testTag("copy_pairing_code")
                ) {
                    Text(stringResource(R.string.copy_pairing_code))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
