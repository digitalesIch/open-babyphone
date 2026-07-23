/*
 * This file is part of Open Babyphone.
 *
 * Open Babyphone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openbabyphone.ui.theme.Spacing
import org.openbabyphone.viewmodel.WifiDirectParentUiState
import org.openbabyphone.viewmodel.WifiDirectParentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverWifiDirectScreen(
    onNavigateBack: () -> Unit,
    onConnected: (requestId: String) -> Unit,
    onUseRegularWifi: () -> Unit,
    requestId: String = "",
    onPairAgain: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WifiDirectParentViewModel = viewModel(),
    permissionChecker: (Context, String) -> Boolean = { context, permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    },
    permissionRequester: ((String, (Boolean) -> Unit) -> Unit)? = null,
    permissionPermanentlyDenied: (Context, String) -> Boolean = { currentContext, permission ->
        (currentContext as? Activity)?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
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
    val requiredPermission = remember { WifiDirectPermissions.requiredPermission() }
    var permissionDenied by remember { mutableStateOf(false) }
    var permissionPermanentlyDeniedState by remember { mutableStateOf(false) }
    val credentialState = pendingCredentialState(requestId)
    val hasPendingCredential = credentialState == PendingCredentialState.Available

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted || permissionChecker(context, requiredPermission)) {
            permissionDenied = false
            viewModel.startDiscovery()
        } else {
            permissionDenied = true
            permissionPermanentlyDeniedState = permissionPermanentlyDenied(context, requiredPermission)
        }
    }
    val tryWifiDirect: () -> Unit = tryWifiDirect@{
        permissionDenied = false
        permissionPermanentlyDeniedState = false
        if (credentialState == PendingCredentialState.Expired) return@tryWifiDirect
        if (permissionChecker(context, requiredPermission)) {
            viewModel.startDiscovery()
        } else if (permissionRequester != null) {
            permissionRequester(requiredPermission) { granted ->
                if (granted || permissionChecker(context, requiredPermission)) {
                    viewModel.startDiscovery()
                } else {
                    permissionDenied = true
                    permissionPermanentlyDeniedState =
                        permissionPermanentlyDenied(context, requiredPermission)
                }
            }
        } else {
            permissionLauncher.launch(requiredPermission)
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.leaveFlow() }
    }

    LaunchedEffect(uiState.wifiDirectState) {
        val endpoint = viewModel.consumeConnectedEndpoint() ?: return@LaunchedEffect
        val completedRequest = if (hasPendingCredential) {
            PendingConnections.store.completeEndpoint(
                requestId,
                endpoint.host,
                endpoint.port,
                endpoint.name
            )
        } else {
            val code = uiState.pairingCode
            if (!PairingCode.isValid(code)) null else PendingConnections.store.put(
                PendingConnection(
                    address = endpoint.host,
                    port = endpoint.port,
                    name = endpoint.name,
                    pairingCode = PairingCode.normalize(code).toCharArray()
                )
            )
        }
        viewModel.clearPairingCode()
        if (completedRequest == null) {
            viewModel.cancel()
        } else {
            onConnected(completedRequest)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.wifi_direct_experimental_title),
                onNavigateBack = {
                    viewModel.cancel()
                    onNavigateBack()
                },
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
            WifiDirectParentContent(
                uiState = uiState,
                credentialState = credentialState,
                permissionDenied = permissionDenied,
                permissionPermanentlyDenied = permissionPermanentlyDeniedState,
                onTry = tryWifiDirect,
                onRetry = tryWifiDirect,
                onCancel = {
                    viewModel.cancel()
                    onNavigateBack()
                },
                onUseRegularWifi = {
                    viewModel.cancel()
                    onUseRegularWifi()
                },
                onPairAgain = {
                    viewModel.cancel()
                    onPairAgain()
                },
                onOpenAppSettings = { openAppSettings(context) },
                onPairingCodeChange = viewModel::updatePairingCode,
                onConnect = { viewModel.connectToPeer(it, hasPendingCredential) },
                modifier = modifier
            )
        }
    }
}

@Composable
internal fun WifiDirectParentContent(
    uiState: WifiDirectParentUiState,
    credentialState: PendingCredentialState,
    permissionDenied: Boolean,
    permissionPermanentlyDenied: Boolean,
    onTry: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onUseRegularWifi: () -> Unit,
    onPairAgain: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPairingCodeChange: (String) -> Unit,
    onConnect: (WifiDirectPeer) -> Unit,
    modifier: Modifier = Modifier
) {
    val state = uiState.wifiDirectState
    val hasPendingCredential = credentialState == PendingCredentialState.Available
    val pairingCodeValid = PairingCode.isValid(uiState.pairingCode)
    Column(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxSize()
            .padding(Spacing.space16)
            .verticalScroll(rememberScrollState())
            .testTag("wifi_direct_state_presentation"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space16)
    ) {
        Text(
            text = stringResource(R.string.wifi_direct_parent_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (credentialState == PendingCredentialState.Expired) {
            Text(
                text = stringResource(R.string.pending_pairing_expired),
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("expired_pairing_recovery")
            )
            OdPrimaryButton(
                text = stringResource(R.string.pair_again),
                onClick = onPairAgain,
                modifier = Modifier.testTag("pair_again_button")
            )
            OdOutlinedActionButton(
                text = stringResource(R.string.use_regular_wifi),
                onClick = onUseRegularWifi,
                modifier = Modifier.testTag("wifi_direct_regular_wifi")
            )
            return@Column
        }
        when (state) {
            WifiDirectState.Idle -> {
                Text(
                    text = if (uiState.wifiDirectSupported) {
                        stringResource(R.string.wifi_direct_idle_explanation)
                    } else {
                        stringResource(R.string.wifi_direct_not_supported)
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("wifi_direct_idle")
                )
                if (permissionDenied) {
                    PermissionExplanation()
                    OdOutlinedActionButton(
                        text = stringResource(R.string.use_regular_wifi),
                        onClick = onUseRegularWifi,
                        modifier = Modifier.testTag("wifi_direct_regular_wifi")
                    )
                    if (permissionPermanentlyDenied) {
                        OdPrimaryButton(
                            text = stringResource(R.string.open_app_settings),
                            onClick = onOpenAppSettings,
                            modifier = Modifier.testTag("wifi_direct_app_settings")
                        )
                    }
                }
                if (uiState.wifiDirectSupported && !permissionPermanentlyDenied) {
                    OdPrimaryButton(
                        text = stringResource(R.string.try_wifi_direct),
                        onClick = onTry,
                        modifier = Modifier.testTag("wifi_direct_try")
                    )
                } else {
                    OdOutlinedActionButton(
                        text = stringResource(R.string.use_regular_wifi),
                        onClick = onUseRegularWifi
                    )
                }
            }
            WifiDirectState.Starting,
            is WifiDirectState.Discovering -> {
                val peers = (state as? WifiDirectState.Discovering)?.peers.orEmpty()
                if (peers.isEmpty()) {
                    ProgressState(
                        stringResource(R.string.wifi_direct_searching),
                        "wifi_direct_searching"
                    )
                } else {
                    if (!hasPendingCredential) PairingCodeField(uiState.pairingCode, onPairingCodeChange)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wifi_direct_peer_list"),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space8)
                    ) {
                        peers.forEachIndexed { index, peer ->
                            OdOutlinedCard(modifier = Modifier.testTag("wifi_direct_peer_$index")) {
                                Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                                    Text(
                                        peer.displayName.ifBlank {
                                            stringResource(R.string.default_child_name)
                                        },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    OdPrimaryButton(
                                        text = stringResource(R.string.connect),
                                        onClick = { onConnect(peer) },
                                        enabled = hasPendingCredential || pairingCodeValid
                                    )
                                }
                            }
                        }
                    }
                }
                CancelButton(onCancel)
            }
            is WifiDirectState.Connecting -> {
                ProgressState(
                    stringResource(R.string.wifi_direct_connecting, state.peer.displayName),
                    "wifi_direct_connecting"
                )
                CancelButton(onCancel)
            }
            is WifiDirectState.Connected -> {
                ProgressState(stringResource(R.string.connecting), "wifi_direct_connected")
                CancelButton(onCancel)
            }
            is WifiDirectState.Error -> {
                Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("wifi_direct_error")
                )
                OdPrimaryButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    modifier = Modifier.testTag("wifi_direct_retry")
                )
                OdOutlinedActionButton(
                    text = stringResource(R.string.use_regular_wifi),
                    onClick = onUseRegularWifi,
                    modifier = Modifier.testTag("wifi_direct_regular_wifi")
                )
                CancelButton(onCancel)
            }
            WifiDirectState.Advertising -> {
                ProgressState(stringResource(R.string.wifi_direct_starting), "wifi_direct_searching")
                CancelButton(onCancel)
            }
        }
    }
}

@Composable
private fun PairingCodeField(value: String, onValueChange: (String) -> Unit) {
    val valid = PairingCode.isValid(value)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.pairing_code_title)) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wifi_direct_pairing_code_field"),
        isError = value.isNotEmpty() && !valid,
        supportingText = {
            if (value.isNotEmpty() && !valid) {
                Text(stringResource(R.string.invalid_pairing_code_feedback))
            }
        },
        singleLine = true
    )
}

@Composable
private fun PermissionExplanation() {
    Text(
        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stringResource(R.string.wifi_direct_permission_nearby)
        } else {
            stringResource(R.string.wifi_direct_permission_location)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("wifi_direct_permission_explanation")
    )
}

@Composable
private fun ProgressState(message: String, tag: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space12),
        modifier = Modifier.testTag(tag)
    ) {
        CircularProgressIndicator()
        Text(message, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CancelButton(onCancel: () -> Unit) {
    OdSurfaceButton(
        text = stringResource(R.string.cancel),
        onClick = onCancel,
        modifier = Modifier.testTag("wifi_direct_cancel")
    )
}
