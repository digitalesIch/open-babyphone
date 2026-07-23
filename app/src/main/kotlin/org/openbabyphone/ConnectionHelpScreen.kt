package org.openbabyphone

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openbabyphone.navigation.ConnectionHelpMode
import org.openbabyphone.ui.theme.Spacing
import org.openbabyphone.viewmodel.ConnectionHelpUiState
import org.openbabyphone.viewmodel.ConnectionHelpViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionHelpScreen(
    mode: ConnectionHelpMode,
    requestId: String,
    onNavigateBack: () -> Unit,
    onTryLastKnownConnection: (String, String, String) -> Unit,
    onManualAddress: () -> Unit,
    onWifiDirect: () -> Unit,
    onPairAgain: () -> Unit = {},
    onUseRegularWifi: () -> Unit = onNavigateBack,
    modifier: Modifier = Modifier,
    viewModel: ConnectionHelpViewModel = viewModel(),
    permissionChecker: (Context, String) -> Boolean = { context, permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    },
    permissionRequester: ((String, (Boolean) -> Unit) -> Unit)? = null,
    permissionPermanentlyDenied: (Context, String) -> Boolean = { currentContext, currentPermission ->
        (currentContext as? Activity)?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, currentPermission)
        } ?: true
    },
    openAppSettings: (Context) -> Unit = { currentContext ->
        currentContext.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", currentContext.packageName, null)
            )
        )
    }
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val permission = remember { WifiDirectPermissions.requiredPermission() }
    var permissionDenied by remember { mutableStateOf(false) }
    var permissionPermanentlyDeniedState by remember { mutableStateOf(false) }
    var childToForget by remember { mutableStateOf<String?>(null) }
    val credentialState = pendingCredentialState(requestId)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || permissionChecker(context, permission)) {
            permissionDenied = false
            viewModel.startChildWifiDirect()
        } else {
            permissionDenied = true
            permissionPermanentlyDeniedState = permissionPermanentlyDenied(context, permission)
        }
    }
    val startChildWifiDirect: () -> Unit = {
        permissionDenied = false
        if (permissionChecker(context, permission)) {
            viewModel.startChildWifiDirect()
        } else if (permissionRequester != null) {
            permissionRequester(permission) { granted ->
                if (granted || permissionChecker(context, permission)) {
                    viewModel.startChildWifiDirect()
                } else {
                    permissionDenied = true
                    permissionPermanentlyDeniedState = permissionPermanentlyDenied(context, permission)
                }
            }
        } else {
            permissionLauncher.launch(permission)
        }
    }

    DisposableEffect(mode) {
        onDispose {
            if (mode == ConnectionHelpMode.Child) viewModel.stopChildWifiDirect()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.connection_help),
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
            ConnectionHelpContent(
                mode = mode,
                uiState = uiState,
                credentialState = credentialState,
                childPermissionDenied = permissionDenied,
                childPermissionPermanentlyDenied = permissionPermanentlyDeniedState,
                onTryLastKnownConnection = { childId ->
                    viewModel.tryLastKnownConnection(childId)?.let {
                        onTryLastKnownConnection(it.requestId, it.childId, it.pairingId)
                    }
                },
                onForget = { childToForget = it },
                onManualAddress = onManualAddress,
                onWifiDirect = onWifiDirect,
                onPairAgain = onPairAgain,
                onUseRegularWifi = onUseRegularWifi,
                onOpenAppSettings = { openAppSettings(context) },
                onStartChildWifiDirect = startChildWifiDirect,
                onStopChildWifiDirect = viewModel::stopChildWifiDirect,
                modifier = modifier
            )
        }
    }

    val forgottenId = childToForget
    if (forgottenId != null) {
        AlertDialog(
            onDismissRequest = { childToForget = null },
            title = { Text(stringResource(R.string.forget_child_title)) },
            text = { Text(stringResource(R.string.forget_child_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.forget(forgottenId)
                        childToForget = null
                    },
                    modifier = Modifier.testTag("confirm_forget_child")
                ) {
                    Text(stringResource(R.string.forget_child))
                }
            },
            dismissButton = {
                TextButton(onClick = { childToForget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
internal fun ConnectionHelpContent(
    mode: ConnectionHelpMode,
    uiState: ConnectionHelpUiState,
    credentialState: PendingCredentialState,
    childPermissionDenied: Boolean,
    childPermissionPermanentlyDenied: Boolean,
    onTryLastKnownConnection: (String) -> Unit,
    onForget: (String) -> Unit,
    onManualAddress: () -> Unit,
    onWifiDirect: () -> Unit,
    onPairAgain: () -> Unit,
    onUseRegularWifi: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onStartChildWifiDirect: () -> Unit,
    onStopChildWifiDirect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
            .padding(Spacing.space16)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.space16)
    ) {
        if (mode == ConnectionHelpMode.Parent && credentialState == PendingCredentialState.Expired) {
            ExpiredPairingRecovery(onPairAgain)
        }
        HelpCard(
            tag = "help_same_wifi",
            label = stringResource(R.string.connection_help_same_wifi_label),
            title = stringResource(R.string.connection_help_same_wifi_title),
            body = stringResource(R.string.connection_help_same_wifi_body)
        )
        HelpCard(
            tag = "help_hotspot",
            label = stringResource(R.string.recommended),
            title = stringResource(R.string.connection_help_hotspot_title),
            body = stringResource(R.string.connection_help_hotspot_body)
        )
        if (credentialState != PendingCredentialState.Expired) {
            OdOutlinedCard(modifier = Modifier.testTag("help_wifi_direct")) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                    Text(
                        stringResource(R.string.experimental),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(stringResource(R.string.wifi_direct_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (mode == ConnectionHelpMode.Parent) {
                            stringResource(R.string.wifi_direct_parent_description)
                        } else {
                            stringResource(R.string.wifi_direct_child_description)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (mode == ConnectionHelpMode.Parent) {
                        OdOutlinedActionButton(
                            text = stringResource(R.string.try_wifi_direct),
                            onClick = onWifiDirect,
                            modifier = Modifier.testTag("connection_help_wifi_direct")
                        )
                    } else {
                        ChildWifiDirectAction(
                            state = uiState.childWifiDirectState,
                            supported = uiState.wifiDirectSupported,
                            permissionDenied = childPermissionDenied,
                            permissionPermanentlyDenied = childPermissionPermanentlyDenied,
                            onStart = onStartChildWifiDirect,
                            onStop = onStopChildWifiDirect,
                            onUseRegularWifi = onUseRegularWifi,
                            onOpenAppSettings = onOpenAppSettings
                        )
                    }
                }
            }
        }
        if (mode == ConnectionHelpMode.Parent) {
            if (credentialState != PendingCredentialState.Expired) {
                OdOutlinedCard(modifier = Modifier.testTag("help_manual_expert")) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                        Text(
                            stringResource(R.string.expert),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            stringResource(R.string.manual_address_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.manual_address_help_body),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (credentialState == PendingCredentialState.Available) {
                            Text(
                                stringResource(R.string.scanned_pairing_reused),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OdTextButton(
                            text = stringResource(R.string.enter_address_title),
                            onClick = onManualAddress,
                            modifier = Modifier.testTag("connection_help_manual")
                        )
                    }
                }
            }
            KnownChildrenManagement(
                children = uiState.knownChildren,
                onTryLastKnownConnection = onTryLastKnownConnection,
                onForget = onForget
            )
        }
        Text(
            stringResource(R.string.connection_help_local_only),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpiredPairingRecovery(onPairAgain: () -> Unit) {
    OdOutlinedCard(modifier = Modifier.testTag("expired_pairing_recovery")) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            Text(
                text = stringResource(R.string.pending_pairing_expired),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            OdPrimaryButton(
                text = stringResource(R.string.pair_again),
                onClick = onPairAgain,
                modifier = Modifier.testTag("pair_again_button")
            )
        }
    }
}

@Composable
private fun HelpCard(tag: String, label: String, title: String, body: String) {
    OdOutlinedCard(modifier = Modifier.testTag(tag)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChildWifiDirectAction(
    state: WifiDirectState,
    supported: Boolean,
    permissionDenied: Boolean,
    permissionPermanentlyDenied: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onUseRegularWifi: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    if (!supported) {
        Text(stringResource(R.string.wifi_direct_not_supported))
        return
    }
    if (permissionDenied) {
        Text(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                stringResource(R.string.wifi_direct_permission_nearby)
            } else {
                stringResource(R.string.wifi_direct_permission_location)
            },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("child_wifi_direct_permission")
        )
        OdOutlinedActionButton(
            text = stringResource(R.string.use_regular_wifi),
            onClick = onUseRegularWifi,
            modifier = Modifier.testTag("child_wifi_direct_regular_wifi")
        )
        if (permissionPermanentlyDenied) {
            OdPrimaryButton(
                text = stringResource(R.string.open_app_settings),
                onClick = onOpenAppSettings,
                modifier = Modifier.testTag("child_wifi_direct_app_settings")
            )
            return
        }
    }
    when (state) {
        WifiDirectState.Idle,
        is WifiDirectState.Error -> {
            if (state is WifiDirectState.Error) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
            OdOutlinedActionButton(
                text = stringResource(R.string.try_wifi_direct),
                onClick = onStart,
                modifier = Modifier.testTag("child_wifi_direct_try")
            )
        }
        else -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
                Text(
                    if (state == WifiDirectState.Advertising) {
                        stringResource(R.string.wifi_direct_advertising)
                    } else {
                        stringResource(R.string.wifi_direct_starting)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            OdSurfaceButton(
                text = stringResource(R.string.cancel),
                onClick = onStop,
                modifier = Modifier.testTag("child_wifi_direct_cancel")
            )
        }
    }
}

@Composable
private fun KnownChildrenManagement(
    children: List<TrustedChild>,
    onTryLastKnownConnection: (String) -> Unit,
    onForget: (String) -> Unit
) {
    if (children.isEmpty()) return
    OdSectionHeader(
        title = stringResource(R.string.known_children_title),
        helper = stringResource(R.string.known_children_management_help)
    )
    children.forEachIndexed { index, child ->
        OdOutlinedCard(modifier = Modifier.testTag("managed_child_$index")) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                Text(
                    child.displayName.ifBlank { stringResource(R.string.default_child_name) },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (child.lastKnownAddress != null && child.lastKnownPort != null) {
                    OdOutlinedActionButton(
                        text = stringResource(R.string.try_last_known_connection),
                        onClick = { onTryLastKnownConnection(child.childId) },
                        modifier = Modifier.testTag("try_last_known_$index")
                    )
                }
                OdTextButton(
                    text = stringResource(R.string.forget_child),
                    onClick = { onForget(child.childId) },
                    modifier = Modifier.testTag("forget_child_$index")
                )
            }
        }
    }
}
