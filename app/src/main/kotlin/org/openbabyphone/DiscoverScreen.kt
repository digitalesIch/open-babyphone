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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import org.openbabyphone.viewmodel.DiscoverViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddressInput: () -> Unit,
    onNavigateToListen: (String, Int, String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectLabel = stringResource(R.string.connect)
    val stopLabel = stringResource(R.string.discovery_stopped)
    val invalidQrFeedback = stringResource(R.string.invalid_qr_code_feedback)
    val scanPrompt = stringResource(R.string.scan_qr_code_prompt)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var scanErrorMessage by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val candidate = PairingQrCode.parseScannedCode(result.contents)
            if (candidate != null && PairingCode.isValid(candidate)) {
                viewModel.updatePairingCode(candidate)
                scanErrorMessage = null
            } else {
                scanErrorMessage = invalidQrFeedback
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppLargeTopAppBar(
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
                Text(
                    stringResource(R.string.discover_child),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = Spacing.space8)
                )

                Button(
                    onClick = {
                        if (uiState.isDiscovering) viewModel.stopDiscovery() else viewModel.startDiscovery()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discover_button")
                ) {
                    Text(if (uiState.isDiscovering) stopLabel else stringResource(R.string.discover_child))
                }

                Spacer(modifier = Modifier.height(Spacing.space16))
                Text(
                    stringResource(R.string.discover_child_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Spacing.space16))

                OutlinedTextField(
                    value = uiState.pairingCode,
                    onValueChange = { viewModel.updatePairingCode(it) },
                    label = { Text(stringResource(R.string.pairing_code_optional)) },
                    placeholder = { Text(stringResource(R.string.example_pairing_code)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discover_pairing_code_field"),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(Spacing.space8))

                OutlinedButton(
                    onClick = {
                        scanErrorMessage = null
                        scanLauncher.launch(
                            ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt(scanPrompt)
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_qr_button")
                ) {
                    Text(stringResource(R.string.scan_qr_code))
                }

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

                Spacer(modifier = Modifier.height(Spacing.space16))

                when {
                    uiState.isDiscovering && uiState.devices.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(Spacing.space8))
                            Text(stringResource(R.string.search_running), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    uiState.devices.isNotEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("device_list"),
                            verticalArrangement = Arrangement.spacedBy(Spacing.space8)
                        ) {
                            uiState.devices.forEach { device ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("device_${device.address}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Spacing.space16),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(device.name, style = MaterialTheme.typography.bodyLarge)
                                            Text("${device.address}:${device.port}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Button(onClick = {
                                            onNavigateToListen(device.address, device.port, device.name, uiState.pairingCode)
                                        }) {
                                            Text(connectLabel)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    uiState.error != null -> {
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    else -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_state_card"),
                            content = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.space24),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.space8))
                                    Text(
                                        stringResource(R.string.discovery_no_devices),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.space32))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("advanced_section")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.space16)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("advanced_toggle"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.advanced_section_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = { advancedExpanded = !advancedExpanded }) {
                                Icon(
                                    imageVector = if (advancedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (advancedExpanded) "Collapse" else "Expand"
                                )
                            }
                        }

                        if (advancedExpanded) {
                            Spacer(modifier = Modifier.height(Spacing.space8))
                            Text(
                                stringResource(R.string.advanced_section_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(Spacing.space12))
                            OutlinedButton(
                                onClick = onNavigateToAddressInput,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("address_input_button")
                            ) {
                                Text(stringResource(R.string.manual_connection))
                            }
                        }
                    }
                }
            }
        }
    }
}