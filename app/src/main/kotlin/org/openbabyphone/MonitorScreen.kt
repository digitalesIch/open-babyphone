package org.openbabyphone

import org.openbabyphone.ui.theme.Spacing
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.openbabyphone.service.ServiceConnectionManager
import org.openbabyphone.viewmodel.MonitorViewModel

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

    DisposableEffect(isMonitoring) {
        val binding = if (isMonitoring) bindMonitorService(context) else null
        onDispose {
            binding?.let { unbindAndStopService(context, it) }
        }
    }

    Scaffold(
        topBar = { AppTopAppBar(stringResource(R.string.childDevice), onNavigateBack) }
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pairing_card"),
                    content = {
                        Column(modifier = Modifier.padding(Spacing.space16)) {
                            Text(stringResource(R.string.pairingCodeTitle), style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(Spacing.space8))
                            OutlinedTextField(
                                value = uiState.pairingCode,
                                onValueChange = { viewModel.updatePairingCode(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pairing_code_field"),
                                enabled = !isMonitoring,
                                placeholder = { Text(stringResource(R.string.examplePairingCode)) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(Spacing.space8))
                            Text(
                                stringResource(
                                    if (isMonitoring) R.string.pairing_code_locked else R.string.monitoring_setup_hint
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(Spacing.space16))

                Button(
                    onClick = { isMonitoring = !isMonitoring },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("monitoring_toggle_button")
                ) {
                    Text(stringResource(if (isMonitoring) R.string.stop_monitoring else R.string.start_monitoring))
                }

                Spacer(modifier = Modifier.height(Spacing.space16))

                if (!isMonitoring) {
                    return@Column
                }

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
                                Text(stringResource(R.string.serviceTitle), style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(Spacing.space8))
                                Text(uiState.serviceName, style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.height(Spacing.space8))
                                Text(stringResource(R.string.serviceDescription), style = MaterialTheme.typography.bodyMedium)
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
                                Text(stringResource(R.string.portTitle), style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(Spacing.space8))
                                Text(uiState.port.toString(), style = MaterialTheme.typography.bodyLarge)
                                Spacer(modifier = Modifier.height(Spacing.space16))
                                Text(stringResource(R.string.addressTitle), style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(Spacing.space8))
                                if (uiState.addresses.isEmpty()) {
                                    Text(
                                        stringResource(R.string.notConnected),
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
                                Text(
                                    uiState.status,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.semantics { contentDescription = serviceStatusDescription }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
