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
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import org.openbabyphone.ui.theme.Spacing
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openbabyphone.viewmodel.WifiDirectParentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverWifiDirectScreen(
    onNavigateBack: () -> Unit,
    onConnected: (host: String, port: Int, name: String, pairingCode: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WifiDirectParentViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var permissionDenied by remember { mutableStateOf(false) }
    val requiredPermission = remember { WifiDirectPermissions.requiredPermission() }
    val requiredPermissions = remember {
        arrayOf(requiredPermission)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = requiredPermissions.all { perm ->
            results[perm] == true || ContextCompat.checkSelfPermission(
                context, perm
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            permissionDenied = false
            viewModel.startDiscovery()
        } else {
            permissionDenied = true
        }
    }

    val wifiState = uiState.wifiDirectState
    LaunchedEffect(wifiState) {
        if (wifiState is WifiDirectState.Connected) {
            val endpoint = wifiState.endpoint
            onConnected(endpoint.host, endpoint.port, endpoint.name, uiState.pairingCode)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.wifi_direct_title),
                onNavigateBack = {
                    viewModel.stop()
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
            Column(
                modifier = modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(Spacing.space16)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.space16)
            ) {
                OdSectionHeader(
                    title = stringResource(R.string.wifi_direct_title),
                    helper = stringResource(R.string.wifi_direct_parent_description)
                )

                if (!uiState.wifiDirectSupported) {
                    OdOutlinedCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.space16)
                        ) {
                            Text(
                                stringResource(R.string.wifi_direct_not_supported),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            OdSurfaceButton(
                                text = stringResource(R.string.navigate_back),
                                onClick = onNavigateBack
                            )
                        }
                    }
                    return@Column
                }

                OdOutlinedCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space16)) {
                        OdCardTitle(stringResource(R.string.wifi_direct_connection_details))
                        OutlinedTextField(
                            value = uiState.pairingCode,
                            onValueChange = { viewModel.updatePairingCode(it) },
                            label = { Text(stringResource(R.string.pairing_code_optional)) },
                            placeholder = { Text(stringResource(R.string.example_pairing_code)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wifi_direct_pairing_code_field"),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge
                        )

                        if (permissionDenied) {
                            Text(
                                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                    stringResource(R.string.wifi_direct_permission_nearby)
                                else stringResource(R.string.wifi_direct_permission_location),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                OdSectionHeader(
                    title = stringResource(R.string.wifi_direct_discovery_status),
                    helper = when (wifiState) {
                        WifiDirectState.Idle -> stringResource(R.string.wifi_direct_start_discovery)
                        is WifiDirectState.Error -> wifiState.message
                        WifiDirectState.Starting -> stringResource(R.string.wifi_direct_starting)
                        is WifiDirectState.Discovering -> stringResource(R.string.wifi_direct_searching)
                        is WifiDirectState.Connecting -> stringResource(
                            R.string.wifi_direct_connecting,
                            wifiState.peer.displayName
                        )
                        is WifiDirectState.Connected -> stringResource(R.string.connecting)
                        WifiDirectState.Advertising -> stringResource(R.string.wifi_direct_advertising)
                    }
                )

                when (wifiState) {
                    WifiDirectState.Idle,
                    is WifiDirectState.Error -> {
                        OdOutlinedCard {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.space12),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                OdStatusPill(
                                    text = stringResource(R.string.disconnected),
                                    active = false
                                )
                                OdPrimaryButton(
                                    text = stringResource(R.string.wifi_direct_start_discovery),
                                    onClick = {
                                        val allGranted = requiredPermissions.all { perm ->
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                perm
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        }
                                        if (allGranted) {
                                            permissionDenied = false
                                            viewModel.startDiscovery()
                                        } else {
                                            permissionLauncher.launch(requiredPermissions)
                                        }
                                    },
                                    modifier = Modifier.testTag("wifi_direct_discover_button")
                                )
                                if (wifiState is WifiDirectState.Error) {
                                    Text(
                                        text = wifiState.message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    WifiDirectState.Starting -> {
                        WifiDirectStatusCard(
                            title = stringResource(R.string.wifi_direct_starting),
                            showProgress = true
                        )
                    }
                    is WifiDirectState.Discovering -> {
                        if (wifiState.peers.isEmpty()) {
                            WifiDirectStatusCard(
                                title = stringResource(R.string.wifi_direct_searching),
                                showProgress = true
                            )
                        } else {
                            OdSurfaceButton(
                                text = stringResource(R.string.wifi_direct_stop),
                                onClick = { viewModel.stop() }
                            )

                            OdSectionHeader(
                                title = stringResource(R.string.wifi_direct_available_devices),
                                helper = stringResource(R.string.wifi_direct_parent_description)
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wifi_direct_peer_list"),
                                verticalArrangement = Arrangement.spacedBy(Spacing.space8)
                            ) {
                                wifiState.peers.forEach { peer ->
                                    OdOutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("wifi_direct_peer_${peer.deviceAddress}")
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        peer.displayName,
                                                        style = MaterialTheme.typography.bodyLarge
                                                    )
                                                    Text(
                                                        "${peer.deviceName} : ${peer.port}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                OdSignalBars()
                                            }
                                            OdPrimaryButton(
                                                text = stringResource(R.string.connect),
                                                onClick = { viewModel.connectToPeer(peer) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is WifiDirectState.Connecting -> {
                        WifiDirectStatusCard(
                            title = stringResource(R.string.wifi_direct_connecting, wifiState.peer.displayName),
                            showProgress = true
                        )
                    }
                    is WifiDirectState.Connected -> Unit
                    WifiDirectState.Advertising -> Unit
                }
            }
        }
    }
}

@Composable
private fun WifiDirectStatusCard(
    title: String,
    showProgress: Boolean,
    modifier: Modifier = Modifier
) {
    OdOutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.space12)
        ) {
            if (showProgress) {
                CircularProgressIndicator()
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
