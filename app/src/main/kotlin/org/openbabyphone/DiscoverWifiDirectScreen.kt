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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
    if (wifiState is WifiDirectState.Connected) {
        val endpoint = wifiState.endpoint
        onConnected(endpoint.host, endpoint.port, endpoint.name, uiState.pairingCode)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppLargeTopAppBar(
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!uiState.wifiDirectSupported) {
                    Text(
                        stringResource(R.string.wifi_direct_not_supported),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Spacing.space16))
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.navigate_back))
                    }
                    return@Column
                }

                Text(
                    stringResource(R.string.wifi_direct_parent_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Spacing.space16)
                )

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

                Spacer(modifier = Modifier.height(Spacing.space16))

                if (permissionDenied) {
                    Spacer(modifier = Modifier.height(Spacing.space8))
                    Text(
                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            stringResource(R.string.wifi_direct_permission_nearby)
                        else stringResource(R.string.wifi_direct_permission_location),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(Spacing.space8))
                }

                when (wifiState) {
                    WifiDirectState.Idle,
                    is WifiDirectState.Error -> {
                        Button(
                            onClick = {
                                val allGranted = requiredPermissions.all { perm ->
                                    ContextCompat.checkSelfPermission(
                                        context, perm
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                }
                                if (allGranted) {
                                    permissionDenied = false
                                    viewModel.startDiscovery()
                                } else {
                                    permissionLauncher.launch(requiredPermissions)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("wifi_direct_discover_button")
                        ) {
                            Text(stringResource(R.string.wifi_direct_start_discovery))
                        }
                        if (wifiState is WifiDirectState.Error) {
                            Spacer(modifier = Modifier.height(Spacing.space8))
                            Text(
                                text = wifiState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    WifiDirectState.Starting -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(Spacing.space8))
                        Text(
                            stringResource(R.string.wifi_direct_starting),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    is WifiDirectState.Discovering -> {
                        if (wifiState.peers.isEmpty()) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(Spacing.space8))
                            Text(
                                stringResource(R.string.wifi_direct_searching),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.stop() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.wifi_direct_stop))
                            }
                            Spacer(modifier = Modifier.height(Spacing.space16))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wifi_direct_peer_list"),
                                verticalArrangement = Arrangement.spacedBy(Spacing.space8)
                            ) {
                                wifiState.peers.forEach { peer ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("wifi_direct_peer_${peer.deviceAddress}")
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
                                                    peer.displayName,
                                                    style = MaterialTheme.typography.bodyLarge
                                                )
                                                Text(
                                                    "${peer.deviceName} : ${peer.port}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            Button(onClick = { viewModel.connectToPeer(peer) }) {
                                                Text(stringResource(R.string.connect))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is WifiDirectState.Connecting -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(Spacing.space8))
                        Text(
                            stringResource(R.string.wifi_direct_connecting, wifiState.peer.displayName),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                    is WifiDirectState.Connected -> Unit
                    WifiDirectState.Advertising -> Unit
                }
            }
        }
    }
}